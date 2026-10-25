# Deploying Dot Tracker

This is how Dot Tracker goes onto Google Cloud the first time and how each new version goes out after that. The app runs on Cloud Run in `us-central1`, keeps its data in Cloud SQL for PostgreSQL 18 and reads its two secrets from Secret Manager. `docs/plans/technical-plan.md` explains why it's set up this way, and this file holds the commands. Every `gcloud` command and flag here was checked against Google Cloud SDK 581.0.0, and all of them are in the GA track unless a line says otherwise.

Run everything from the repository root in one terminal, with Docker running. Commands that set shell variables carry those variables into the steps after them, so a first deploy goes best in a single terminal session. The commands work in zsh and bash.

The deploy creates these resources.

- A new project, with an id you choose, such as `dot-tracker-1234`
- The Cloud Run service `dot-tracker`, running as the service account `dot-tracker-run`
- The Artifact Registry Docker repository `dot-tracker`
- The Cloud SQL instance `dot-tracker`, with a database and a user both named `dottracker`
- The secrets `db-password` and `google-client-secret`
- A Google OAuth client named Dot Tracker

## First deploy

### 1. Create a gcloud configuration for the app

Your usual gcloud configuration is signed in to your work account with a work project selected, and nothing here changes it. The app gets its own named configuration, signed in to your personal Google account. Creating it with `--no-activate` leaves the globally active configuration as it is, and `CLOUDSDK_ACTIVE_CONFIG_NAME` points only this terminal at the new one, so other terminals and your IDE stay on your work account.

```sh
gcloud config configurations create dot-tracker --no-activate
export CLOUDSDK_ACTIVE_CONFIG_NAME=dot-tracker
gcloud auth login
```

Sign in with your personal Google account in the browser window that opens. `gcloud auth login` sets that account in the `dot-tracker` configuration only, and without `--update-adc` it leaves Application Default Credentials alone. Check that this terminal is on the new configuration and account.

```sh
gcloud config list
```

To go back to your work setup, run `unset CLOUDSDK_ACTIVE_CONFIG_NAME` or open a new terminal. The first four lines of "Redeploying" set a new terminal up again, and the sections after it assume them.

### 2. Create the project and link billing

Pick a project id. It has to be unique across all of Google Cloud, 6 to 30 characters of lowercase letters, digits and hyphens, starting with a letter.

```sh
PROJECT_ID=<your project id, such as dot-tracker-1234>
REGION=us-central1
gcloud projects create $PROJECT_ID --name="Dot Tracker" --set-as-default
```

`--set-as-default` stores the project in the `dot-tracker` configuration, so later commands don't need `--project`. gcloud charges each call to the current project's quota, so an API has to be enabled in the project before gcloud can call it there. The Cloud Billing API is the first one, since linking billing goes through it and it doesn't need billing itself.

```sh
gcloud services enable cloudbilling.googleapis.com
gcloud billing accounts list
BILLING_ACCOUNT=<ACCOUNT_ID of your personal billing account>
gcloud billing projects link $PROJECT_ID --billing-account=$BILLING_ACCOUNT
```

If `gcloud billing accounts list` shows nothing, create a billing account at https://console.cloud.google.com/billing and run it again.

### 3. Enable the APIs

These are the only other APIs the app and this setup use. Logging is already on in every new project.

- `run.googleapis.com` runs the service.
- `sqladmin.googleapis.com` creates the database, and the Cloud SQL Java connector uses it at runtime to find the instance.
- `artifactregistry.googleapis.com` holds the images.
- `secretmanager.googleapis.com` holds the database password and the Google client secret.
- `iam.googleapis.com` creates the service account.
- `cloudresourcemanager.googleapis.com` looks up the project number and grants the service account its project role.
- `billingbudgets.googleapis.com` sets the budget alert.

```sh
gcloud services enable run.googleapis.com sqladmin.googleapis.com artifactregistry.googleapis.com \
  secretmanager.googleapis.com iam.googleapis.com cloudresourcemanager.googleapis.com \
  billingbudgets.googleapis.com
```

### 4. Set a budget alert

The budget covers only this project and emails the billing account's administrators, which is you, at 50, 90 and 100 percent of $15 a month. The amount is in the billing account's currency. A budget only sends alerts and never stops anything.

```sh
gcloud billing budgets create --billing-account=$BILLING_ACCOUNT --display-name="Dot Tracker" \
  --budget-amount=15 --calendar-period=month --filter-projects=projects/$PROJECT_ID \
  --threshold-rule=percent=0.5 --threshold-rule=percent=0.9 --threshold-rule=percent=1.0
```

### 5. Create the image repository

The repository gets a cleanup policy that keeps the five newest images and deletes the rest, which keeps storage inside the free tier. A keep policy wins over a delete policy, so the five newest always stay. The action and tag state values are uppercase because gcloud silently drops a value written as `Keep` or `any`. The policy file goes in the git-ignored `build` folder.

```sh
gcloud artifacts repositories create dot-tracker --repository-format=docker --location=$REGION \
  --description="Dot Tracker images"
mkdir -p build
cat > build/cleanup-policy.json <<'EOF'
[
  {"name": "keep-newest-five", "action": {"type": "KEEP"}, "mostRecentVersions": {"keepCount": 5}},
  {"name": "delete-the-rest", "action": {"type": "DELETE"}, "condition": {"tagState": "ANY"}}
]
EOF
gcloud artifacts repositories set-cleanup-policies dot-tracker --location=$REGION \
  --policy=build/cleanup-policy.json --no-dry-run
gcloud artifacts repositories list-cleanup-policies dot-tracker --location=$REGION
```

The last command should say dry run is disabled and list both policies, each with its action. Artifact Registry applies the policy periodically rather than on every push, so an extra image or two can linger for a while.

### 6. Create the database

`--edition=enterprise` matters, because a new PostgreSQL instance otherwise gets the much pricier Enterprise Plus edition, which has no shared-core tier. Daily backups are on by default, and `--backup-start-time` is in UTC, so 08:00 is 3 a.m. in Chicago during daylight saving time. The instance gets a public IP with no authorized networks, so nothing can connect to it directly. The app reaches it through the Cloud SQL Java connector, which brings its own TLS and authorizes through IAM. Deletion protection guards the instance against an accidental delete. Creating it takes several minutes.

```sh
gcloud sql instances create dot-tracker --database-version=POSTGRES_18 --edition=enterprise \
  --tier=db-f1-micro --region=$REGION --storage-type=SSD --storage-size=10GB \
  --backup-start-time=08:00 --deletion-protection
```

Then create the database and its user. The generated password goes straight into Secret Manager, so it never needs writing down. `printf` rather than `echo` keeps a trailing newline out of the secret.

```sh
gcloud sql databases create dottracker --instance=dot-tracker
DB_PASSWORD=$(openssl rand -hex 24)
gcloud sql users create dottracker --instance=dot-tracker --password="$DB_PASSWORD"
printf '%s' "$DB_PASSWORD" | gcloud secrets create db-password --data-file=-
unset DB_PASSWORD
```

### 7. Create the Google OAuth client

`gcloud` can't create a web OAuth client, so this step happens in the Cloud Console. Cloud Run gives every service a deterministic URL of the form `https://<service>-<project number>.<region>.run.app`, as long as the `<service>-<project number>` part fits in 63 characters, which it does here. That means the service's address is known before it exists, and the client's redirect URI can be registered now.

```sh
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')
SERVICE_URL=https://dot-tracker-$PROJECT_NUMBER.$REGION.run.app
echo $SERVICE_URL
```

Open the Google Auth Platform at https://console.cloud.google.com/auth/overview. Check that the account menu shows your personal account and the project picker shows the new project, since a browser signed in to your work account opens the work one. Then go through these pages in order.

1. On the overview, choose Get started. Enter "Dot Tracker" as the app name and your personal email as the user support email, choose External as the audience, give your email as the contact, agree to the user data policy and create it. External is the only choice for a project outside a Google Workspace organization. It lets any Google account reach the sign-in, and the app itself admits only invited members.
2. On Branding, add the host of `$SERVICE_URL`, such as `dot-tracker-123456789012.us-central1.run.app`, under Authorized domains, and save. The whole host is the domain to authorize, because `*.run.app` is on the public suffix list. Until the app is verified, which it doesn't need to be, Google's consent screen names that domain rather than the app.
3. On Audience, choose Publish app and confirm, so the status reads In production. While it's in testing, only listed test users can sign in. With only the basic scopes below, publishing needs no verification.
4. On Data Access, choose Add or remove scopes, select `openid`, `.../auth/userinfo.email` and `.../auth/userinfo.profile`, update and save. These are the only scopes the app asks for, and all three are non-sensitive.
5. On Clients, choose Create client and pick Web application as the type. Name it "Dot Tracker" and add two authorized redirect URIs, your `$SERVICE_URL` followed by `/login/oauth2/code/google`, and `http://localhost:8080/login/oauth2/code/google` for signing in locally. Leave the JavaScript origins empty, and create it.
6. Copy the client ID and the client secret from the dialog that follows, or download its JSON. Google shows the full secret only this once.

Back in the terminal, keep both in shell variables. `read -rs` waits silently for you to paste the secret and press Enter.

```sh
GOOGLE_CLIENT_ID=<client ID>
read -rs GOOGLE_CLIENT_SECRET
```

### 8. Store the client secret and create the service account

The service account is the identity the app runs as. It can connect to Cloud SQL and read the two secrets, and nothing more.

```sh
printf '%s' "$GOOGLE_CLIENT_SECRET" | gcloud secrets create google-client-secret --data-file=-
gcloud iam service-accounts create dot-tracker-run --display-name="Dot Tracker on Cloud Run"
SERVICE_ACCOUNT=dot-tracker-run@$PROJECT_ID.iam.gserviceaccount.com
gcloud projects add-iam-policy-binding $PROJECT_ID --member=serviceAccount:$SERVICE_ACCOUNT \
  --role=roles/cloudsql.client
for secret in db-password google-client-secret; do
  gcloud secrets add-iam-policy-binding $secret --member=serviceAccount:$SERVICE_ACCOUNT \
    --role=roles/secretmanager.secretAccessor
done
```

A new service account can take a few seconds to become visible to IAM. If a binding says the account doesn't exist, wait a moment and run that binding again.

### 9. Build and push the image

`gcloud auth configure-docker` registers gcloud as Docker's credential helper for the registry's host, once per machine. The helper signs in as whichever gcloud configuration is active when Docker runs, so push from this terminal.

```sh
gcloud auth configure-docker $REGION-docker.pkg.dev
IMAGE=$REGION-docker.pkg.dev/$PROJECT_ID/dot-tracker/dot-tracker
gain bootBuildImage
TAG=$(git rev-parse --short HEAD)
docker tag dot-tracker:latest $IMAGE:$TAG
docker push $IMAGE:$TAG
```

`gain bootBuildImage` builds `dot-tracker:latest` for `linux/amd64`, the platform Cloud Run runs. Each image is tagged with the commit it was built from, so commit before building.

### 10. Deploy

The plain settings go in an env vars file, because the admin email list holds commas, which `--set-env-vars` would split on. The file goes in the git-ignored `build` folder and is only needed this once, since later deploys keep the service's settings. Replace the placeholder with the Google account emails that become admins on their first sign-in, separated by commas.

```sh
ADMIN_EMAILS='<admin emails, comma-separated>'
cat > build/cloud-run-env.yaml <<EOF
SPRING_DATASOURCE_URL: "jdbc:postgresql:///dottracker?cloudSqlInstance=$PROJECT_ID:$REGION:dot-tracker&socketFactory=com.google.cloud.sql.postgres.SocketFactory"
SPRING_DATASOURCE_USERNAME: "dottracker"
GOOGLE_CLIENT_ID: "$GOOGLE_CLIENT_ID"
DOT_TRACKER_ADMIN_EMAILS: "$ADMIN_EMAILS"
EOF
```

Then deploy.

```sh
gcloud run deploy dot-tracker --image=$IMAGE:$TAG --region=$REGION \
  --service-account=$SERVICE_ACCOUNT --memory=1Gi --cpu-boost \
  --min-instances=0 --max-instances=2 --allow-unauthenticated \
  --env-vars-file=build/cloud-run-env.yaml \
  --set-secrets=SPRING_DATASOURCE_PASSWORD=db-password:latest,GOOGLE_CLIENT_SECRET=google-client-secret:latest \
  --startup-probe=httpGet.path=/actuator/health,periodSeconds=5,timeoutSeconds=3,failureThreshold=24 \
  --liveness-probe=httpGet.path=/actuator/health,periodSeconds=30,timeoutSeconds=5,failureThreshold=3
```

The service gets 1 GiB of memory because the buildpack's memory calculator sizes the JVM from the container's limit. Its fixed regions, which are 250 thread stacks, a 240 MiB code cache and the metaspace, need about 670 MiB before the heap gets anything, so the image refuses to start in 512 MiB. In a local run limited to 1 GiB, the app started in 8.5 seconds and, after a few thousand requests, the JVM had committed about 335 MiB and the container used about 510 MiB. That run was under amd64 emulation on an Apple silicon Mac, which makes startup slower than on Cloud Run.

The startup probe gives a new instance two minutes to answer `/actuator/health`, and the liveness probe restarts an instance that stops answering for a minute and a half. `/actuator/health` includes the database check, so a Cloud SQL restart during maintenance can restart the app's instances too. The db-f1-micro tier allows 25 connections and each instance's pool opens up to 10, so two instances fit. `--allow-unauthenticated` lets anyone load the page, and the app itself asks everyone to sign in.

The deploy waits for the new revision to pass its startup probe, and reports a failure if it doesn't. Its log says why, as in "Reading logs" below. When the deploy finishes, gcloud prints a service URL, which can be the hash-based address ending in `a.run.app`. Both addresses reach the service, but Google sign-in only works at `$SERVICE_URL`, because that's the address registered as the redirect URI.

### 11. Sign in and check it works

Open `$SERVICE_URL`, choose Continue with Google and sign in with one of the admin emails. Then run a short smoke check.

1. Browse lists 93 books.
2. Open a book, mark a page done and reload. The page stays done.
3. In Group, invite a friend and copy the link.
4. Open the link in a private window. The welcome screen says when the invite expires. Revoke the invite afterwards unless someone will use it.

Then walk through every screen on a phone and on a desktop.

Once the first deploy works, delete the sentence in CLAUDE.md's "Persistence" section that lets `V1__schema.sql` and `V2__catalog.sql` change in place, since production has now applied them.

The app never uses its Google credentials until someone signs in, so a mistake in them doesn't stop it from starting and only shows at sign-in.

- A wrong or missing client ID stops at a Google error page saying "Error 401: invalid_client" and "The OAuth client was not found." Check `GOOGLE_CLIENT_ID`.
- A redirect URI that isn't registered stops at a Google error page saying "Error 400: redirect_uri_mismatch", whose details show the URI the app sent. It usually means the app was opened at an address other than `$SERVICE_URL`.
- A wrong client secret, a secret with a stray newline, or an email missing from `DOT_TRACKER_ADMIN_EMAILS` all come back to the sign-in screen with "That Google account isn't in the group yet, or signing in didn't finish."

`gcloud run services describe dot-tracker --region=$REGION` shows the service's environment variables and which secrets it reads. Spring Security logs why a sign-in failed only at trace level. To tell a bad secret, whose error mentions `invalid_client`, from an email that isn't let in, whose error mentions `not_invited`, turn trace logging on, sign in again, read the logs and turn it off again.

```sh
gcloud run services update dot-tracker --region=$REGION \
  --update-env-vars=LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=TRACE
gcloud run services update dot-tracker --region=$REGION \
  --remove-env-vars=LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY
```

A corrected client secret goes in as a new version. Each instance reads the latest version when it starts, so the next deploy, as in "Redeploying" below, picks it up.

```sh
read -rs GOOGLE_CLIENT_SECRET
printf '%s' "$GOOGLE_CLIENT_SECRET" | gcloud secrets versions add google-client-secret --data-file=-
```

## Redeploying

A new version is a build, a push and a deploy with the new image.

```sh
export CLOUDSDK_ACTIVE_CONFIG_NAME=dot-tracker
PROJECT_ID=$(gcloud config get project)
REGION=us-central1
IMAGE=$REGION-docker.pkg.dev/$PROJECT_ID/dot-tracker/dot-tracker
gain bootBuildImage
TAG=$(git rev-parse --short HEAD)
docker tag dot-tracker:latest $IMAGE:$TAG
docker push $IMAGE:$TAG
gcloud run deploy dot-tracker --image=$IMAGE:$TAG --region=$REGION
```

Only the image changes. Memory, probes, scaling, the service account, the environment variables and the secrets all carry over from the running revision. Flyway runs any new migration when the new revision starts, and if the migration fails, the revision never passes its startup probe and the previous one keeps serving. After the first deploy, an applied Flyway migration never changes, so every schema change is a new `V<n>__<description>.sql`.

To go back to an earlier version, deploy one of the tags the cleanup policy kept, which `gcloud artifacts docker tags list $IMAGE` lists.

## Reading logs

The newest entries come first.

```sh
gcloud run services logs read dot-tracker --region=$REGION --limit=100
gcloud run services logs read dot-tracker --region=$REGION --log-filter='severity>=WARNING' --freshness=7d
```

Following the log as it happens is only in the beta track and needs two components, after which `gcloud beta run services logs tail dot-tracker --region=$REGION` works.

```sh
gcloud components install beta log-streaming
```

The Cloud Console shows the same logs on the service's Logs tab under Cloud Run.

## Costs

The bill is about $10 a month, nearly all of it Cloud SQL, at about $7.70 for the db-f1-micro instance and $1.70 for its 10 GB of SSD. Cloud Run, Artifact Registry with its cleanup policy, Secret Manager and logging fit in their free tiers for a small group. Scaling to zero means the first visit after a quiet spell waits several seconds for an instance to start. Keeping one instance running removes that wait for roughly $13 more a month at 1 GiB, and the setting carries over to later deploys.

```sh
gcloud run services update dot-tracker --region=$REGION --min-instances=1
```

## Signing in with Google locally

`gain bootRun` runs the app on port 8080 against the Postgres in `compose.yaml`, with real Google sign-in through the same OAuth client, whose redirect URIs include `http://localhost:8080/login/oauth2/code/google`. It reads the client from a git-ignored `.env` at the repository root, with one `NAME=value` per line and no quotes.

```
GOOGLE_CLIENT_ID=<client ID>
GOOGLE_CLIENT_SECRET=<client secret>
DOT_TRACKER_ADMIN_EMAILS=<your email>
```

The secret can be read back from Secret Manager in a terminal on the `dot-tracker` configuration.

```sh
export CLOUDSDK_ACTIVE_CONFIG_NAME=dot-tracker
gcloud secrets versions access latest --secret=google-client-secret
```

Then run `gain bootRun` and open http://localhost:8080 in Chrome. Outside the demo profile the session cookie is `Secure`, and Chrome keeps a `Secure` cookie on `http://localhost`, where some browsers don't.
