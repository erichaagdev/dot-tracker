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
- The service account `dot-tracker-deploy` and the workload identity pool `github` with its provider `dot-tracker`, which let GitHub Actions deploy
- The GitHub repository `erichaagdev/dot-tracker`, with the secrets `GCP_PROJECT_ID` and `GCP_PROJECT_NUMBER`

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
- `sts.googleapis.com` trades a GitHub Actions run's token for a Google one.
- `iamcredentials.googleapis.com` lets that run act as the deploy service account.

```sh
gcloud services enable run.googleapis.com sqladmin.googleapis.com artifactregistry.googleapis.com \
  secretmanager.googleapis.com iam.googleapis.com cloudresourcemanager.googleapis.com \
  billingbudgets.googleapis.com sts.googleapis.com iamcredentials.googleapis.com
```

### 4. Set a budget alert

The budget covers only this project and emails the billing account's administrators, which is you, at 50, 90 and 100 percent of $15 a month. The amount is in the billing account's currency. A budget only sends alerts and never stops anything.

```sh
gcloud billing budgets create --billing-account=$BILLING_ACCOUNT --display-name="Dot Tracker" \
  --budget-amount=15 --calendar-period=month --filter-projects=projects/$PROJECT_ID \
  --threshold-rule=percent=0.5 --threshold-rule=percent=0.9 --threshold-rule=percent=1.0
```

### 5. Create the image repository

The repository gets a cleanup policy that keeps the five newest images and deletes the rest, which keeps storage to roughly 0.4 GB, as "Costs" explains. A keep policy wins over a delete policy, so the five newest always stay. The action and tag state values are uppercase because gcloud silently drops a value written as `Keep` or `any`. The policy file goes in the git-ignored `build` folder.

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
2. On Branding, add the host of `$SERVICE_URL`, such as `dot-tracker-123456789012.us-central1.run.app`, under Authorized domains. The whole host is the domain to authorize, because `*.run.app` is on the public suffix list. Then enter `$SERVICE_URL` as the application home page and `$SERVICE_URL/privacy` as the privacy policy link, which Google requires before an External app can be published, leave the logo and the terms of service empty, and save. The app serves its privacy page at `/privacy` to anyone, signed in or not. Until the app is verified, which it doesn't need to be, Google's consent screen names that domain rather than the app.
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
./gradlew bootBuildImage
TAG=$(git rev-parse --short HEAD)
docker tag dot-tracker:latest $IMAGE:$TAG
docker push $IMAGE:$TAG
```

`./gradlew bootBuildImage` builds `dot-tracker:latest` for `linux/amd64`, the platform Cloud Run runs. Each image is tagged with the commit it was built from, so commit before building.

The image is a GraalVM native image, which the buildpack compiles ahead of time from the app and its libraries. That takes about 3 minutes and up to about 14 GB of memory for an arm64 build on an Apple silicon Mac with 18 cores, since the compiler sizes its heap to the machine it runs on. The amd64 build that Cloud Run needs runs under emulation on Apple silicon and is very slow, so give it time and give Docker plenty of memory. Only the first deploy has to build here, since the GitHub workflow in step 12 builds every later one. The executable goes into a layer of about 77 MB compressed, and that layer changes with every build, so each push uploads about that much.

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
  --startup-probe=httpGet.path=/actuator/health,periodSeconds=1,timeoutSeconds=1,failureThreshold=120 \
  --liveness-probe=httpGet.path=/actuator/health,periodSeconds=30,timeoutSeconds=5,failureThreshold=3
```

The service gets 1 GiB of memory, which is far more than the native image needs. Run natively on arm64 on an Apple silicon Mac with one CPU and 1 GiB and the migrations already applied, the app started in about 0.2 seconds, and the container used about 110 MiB once it was up. On Cloud Run it uses about 240 MiB, so 512 MiB would fit too. A cold start there takes about 5 to 6 seconds from an instance starting to its first answer, and a request that woke a service scaled to zero got its page in 5.5 seconds, against about 21 seconds for the JVM build. Starting the native process takes about 2 seconds before Spring logs anything, and Spring takes another 2.2 to 2.8 seconds with its first Cloud SQL connection, both far longer than on a Mac.

The startup probe checks `/actuator/health` every second and gives a new instance two minutes to answer, so an instance takes traffic within a second of being ready. The liveness probe restarts an instance that stops answering for a minute and a half. `/actuator/health` includes the database check, so a Cloud SQL restart during maintenance can restart the app's instances too. The db-f1-micro tier allows 25 connections and each instance's pool opens up to 10, so two instances fit. `--allow-unauthenticated` lets anyone load the page, and the app itself asks everyone to sign in.

The deploy waits for the new revision to pass its startup probe, and reports a failure if it doesn't. Its log says why, as in "Reading logs" below. When the deploy finishes, gcloud prints a service URL, which can be the hash-based address ending in `a.run.app`. Both addresses reach the service, but Google sign-in only works at `$SERVICE_URL`, because that's the address registered as the redirect URI.

### 11. Sign in and check it works

Open `$SERVICE_URL`, choose Continue with Google and sign in with one of the admin emails. Then run a short smoke check.

1. Browse lists 93 books.
2. Open a book, mark a page done and reload. The page stays done.
3. In Group, invite a friend and copy the link.
4. Open the link in a private window. The welcome screen says when the invite expires. Revoke the invite afterwards unless someone will use it.

Then walk through every screen on a phone and on a desktop.

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

### 12. Let GitHub deploy every push

`.github/workflows/deploy.yml` runs on GitHub Actions for every push to `main`. It builds the app, runs every test, builds the image, pushes it and deploys it the way "Redeploying" does, so a failing test stops it before anything reaches Google Cloud. It signs in to Google Cloud through workload identity federation, which trades the short-lived token GitHub gives each run for a Google one, so no key is stored anywhere. This step needs the service from the first deploy, since a workflow run only changes the image of a service that already exists.

The step can run in a new terminal another day, so it sets its variables again.

```sh
export CLOUDSDK_ACTIVE_CONFIG_NAME=dot-tracker
PROJECT_ID=$(gcloud config get project)
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')
REGION=us-central1
REPOSITORY=erichaagdev/dot-tracker
```

GitHub deploys as its own service account, which can push images to the image repository, deploy new revisions of the service and run them as `dot-tracker-run`, and nothing more.

```sh
gcloud iam service-accounts create dot-tracker-deploy --display-name="Dot Tracker deploys from GitHub"
DEPLOY_ACCOUNT=dot-tracker-deploy@$PROJECT_ID.iam.gserviceaccount.com
gcloud artifacts repositories add-iam-policy-binding dot-tracker --location=$REGION \
  --member=serviceAccount:$DEPLOY_ACCOUNT --role=roles/artifactregistry.writer
gcloud run services add-iam-policy-binding dot-tracker --region=$REGION \
  --member=serviceAccount:$DEPLOY_ACCOUNT --role=roles/run.developer
gcloud iam service-accounts add-iam-policy-binding dot-tracker-run@$PROJECT_ID.iam.gserviceaccount.com \
  --member=serviceAccount:$DEPLOY_ACCOUNT --role=roles/iam.serviceAccountUser
```

Then create the pool and its GitHub provider, and let runs from the repository act as the deploy account. The provider's condition accepts a token only from a run on `main` of this repository, so no fork, pull request or other branch can deploy.

```sh
gcloud iam workload-identity-pools create github --location=global --display-name="GitHub"
gcloud iam workload-identity-pools providers create-oidc dot-tracker --location=global \
  --workload-identity-pool=github --display-name="Dot Tracker" \
  --issuer-uri=https://token.actions.githubusercontent.com \
  --attribute-mapping=google.subject=assertion.sub,attribute.repository=assertion.repository \
  --attribute-condition="assertion.repository == '$REPOSITORY' && assertion.ref == 'refs/heads/main'"
gcloud iam service-accounts add-iam-policy-binding $DEPLOY_ACCOUNT --role=roles/iam.workloadIdentityUser \
  --member=principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/$REPOSITORY
```

Last, create the GitHub repository with this folder as its source, give it the project's id and number as secrets and push. The repository is public, because GitHub runs a public repository's workflows on a 4-core runner with 16 GB at no cost, where the native build takes about 9.5 minutes. A private repository on GitHub's Free plan gets a 2-core runner with about 8 GB, where the native build ran short of memory and didn't finish within an hour. GitHub masks the secrets in the logs.

```sh
gh repo create $REPOSITORY --public --source=. --remote=origin
gh secret set GCP_PROJECT_ID --repo=$REPOSITORY --body="$PROJECT_ID"
gh secret set GCP_PROJECT_NUMBER --repo=$REPOSITORY --body="$PROJECT_NUMBER"
git push -u origin main
```

The push starts the first run, which deploys the commit that's already running. Follow it on the repository's Actions tab or with `gh run watch`. New IAM bindings can take a few minutes to take effect, so if the first run fails at authentication or at the push, rerun it from the Actions tab or with `gh run rerun --failed`.

## Redeploying

Every push to `main` deploys through `.github/workflows/deploy.yml`, and its `workflow_dispatch` trigger lets `gh workflow run deploy.yml` or the Run workflow button on the Actions tab deploy `main` again without a new commit. That's the way to redeploy without a push, since on an Apple silicon Mac the amd64 native image builds under emulation, which is very slow. A failed build or test stops the run before anything is pushed, and the running version keeps serving. Pushes that land while a run is going wait for it, and only the newest of them deploys next.

To deploy from this machine anyway, build, push and deploy by hand.

```sh
export CLOUDSDK_ACTIVE_CONFIG_NAME=dot-tracker
PROJECT_ID=$(gcloud config get project)
REGION=us-central1
IMAGE=$REGION-docker.pkg.dev/$PROJECT_ID/dot-tracker/dot-tracker
./gradlew bootBuildImage
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

Every start logs five warnings that are expected in a native image. Four come from Flyway, saying it's unable to scan `/db/migration` because of an unsupported protocol, `resource`. Flyway's own scanner can't read files inside a native image, and Spring Boot hands it the migrations another way, so the lines after them still show the migrations validated. The fifth comes from Micrometer, saying GC notifications won't be available, since a native image has no garbage collector beans to watch.

## Checking a native image locally

The tests run on the JVM, so a hint a native image is missing only shows when the image runs. A change that keeps something new in the session, adds a library or reaches a class by reflection is worth checking this way before it ships. On an Apple silicon Mac, build the image for arm64, which runs natively and takes a few minutes, and run it against the Postgres in `compose.yaml`. The compose network is named after the folder the repository is in, so it's `dot-tracker_default` for a folder named `dot-tracker`.

```sh
./gradlew bootBuildImage --imagePlatform=linux/arm64 --imageName=dot-tracker-native:arm64
docker compose up -d postgres
docker run --rm --name dot-tracker-native --network dot-tracker_default -p 8080:8080 --cpus=1 --memory=1g \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/dottracker \
  -e SPRING_DATASOURCE_USERNAME=dottracker -e SPRING_DATASOURCE_PASSWORD=dottracker \
  -e GOOGLE_CLIENT_ID=dummy -e GOOGLE_CLIENT_SECRET=dummy -e DOT_TRACKER_ADMIN_EMAILS=admin@example.com \
  dot-tracker-native:arm64
```

Nobody can sign in with a made-up Google client, but http://localhost:8080/oauth2/authorization/google still stores Google's authorization request in the session and redirects to Google, which shows the session works. A missing hint shows in the log as an error naming the type, such as a `MissingReflectionRegistrationError` or a `SerializationConstructorAccessor class not found`. What the session holds goes in `SessionRuntimeHints`, and anything else in a registrar like `PersistenceRuntimeHints`. Then `docker compose down` stops Postgres.

## Costs

The bill is about $10 a month, nearly all of it Cloud SQL, at about $7.70 for the db-f1-micro instance and $1.70 for its 10 GB of SSD. Cloud Run, Secret Manager, logging and Artifact Registry fit in their free tiers for a small group. Artifact Registry keeps five images, each with its own layer of about 77 MB for the native executable, which comes to roughly 0.4 GB, inside the free 0.5 GB. The repository is public, so GitHub Actions costs nothing, and a deploy takes about 11 minutes, nearly all of it the native build. Scaling to zero means the first visit after a quiet spell waits for an instance to start. Keeping one instance running removes that wait for roughly $13 more a month at 1 GiB, and the setting carries over to later deploys.

```sh
gcloud run services update dot-tracker --region=$REGION --min-instances=1
```

## Signing in with Google locally

`./gradlew bootRun` runs the app on port 8080 against the Postgres in `compose.yaml`, with real Google sign-in through the same OAuth client, whose redirect URIs include `http://localhost:8080/login/oauth2/code/google`. It reads the client from a git-ignored `.env` at the repository root, with one `NAME=value` per line and no quotes.

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

Then run `./gradlew bootRun` and open http://localhost:8080 in Chrome. Outside the demo profile the session cookie is `Secure`, and Chrome keeps a `Secure` cookie on `http://localhost`, where some browsers don't.
