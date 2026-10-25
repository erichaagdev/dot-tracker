create table member (
  id uuid primary key default uuidv7(),
  google_subject text unique,
  email text not null,
  display_name text not null,
  role text not null check (role in ('ADMIN', 'MEMBER')),
  joined_at timestamptz not null,
  version bigint not null
);
create unique index member_email on member (lower(email));

create table invite (
  id uuid primary key default uuidv7(),
  code text not null unique,
  created_by uuid not null references member (id),
  created_at timestamptz not null,
  expires_at timestamptz not null,
  accepted_by uuid references member (id),
  accepted_at timestamptz,
  version bigint not null
);

create table product_line (
  id uuid primary key default uuidv7(),
  name text not null unique,
  default_page_count integer not null check (default_page_count > 0),
  sort_order integer not null,
  version bigint not null
);

create table book (
  id uuid primary key default uuidv7(),
  product_line_id uuid not null references product_line (id),
  name text not null,
  also_known_as text[] not null default '{}',
  availability text not null check (availability in ('AVAILABLE', 'COMING_SOON', 'RETIRED', 'UNKNOWN')),
  page_count integer not null check (page_count > 0),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  version bigint not null
);
create unique index book_line_name on book (product_line_id, lower(name));

create table book_listing (
  book_id uuid not null references book (id) on delete cascade,
  position integer not null,
  retailer text not null,
  price_cents integer check (price_cents >= 0),
  url text,
  exclusive boolean not null default false,
  primary key (book_id, position)
);

create table collection_entry (
  id uuid primary key default uuidv7(),
  member_id uuid not null references member (id) on delete cascade,
  book_id uuid not null references book (id) on delete cascade,
  owned boolean not null default false,
  wanted text check (wanted in ('YES', 'MAYBE', 'NO')),
  progress text not null default 'NOT_STARTED' check (progress in ('NOT_STARTED', 'IN_PROGRESS', 'FINISHED')),
  pages_done integer[] not null default '{}',
  rating smallint check (rating between 0 and 5),
  design_rating smallint check (design_rating between 0 and 5),
  color_rating smallint check (color_rating between 0 and 5),
  notes text,
  started_on date,
  finished_on date,
  updated_at timestamptz not null,
  version bigint not null,
  unique (member_id, book_id)
);

-- Spring Session's tables, copied unchanged from schema-postgresql.sql in spring-session-jdbc
CREATE TABLE SPRING_SESSION (
	PRIMARY_ID CHAR(36) NOT NULL,
	SESSION_ID CHAR(36) NOT NULL,
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	EXPIRY_TIME BIGINT NOT NULL,
	PRINCIPAL_NAME VARCHAR(100),
	CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
	SESSION_PRIMARY_ID CHAR(36) NOT NULL,
	ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
	ATTRIBUTE_BYTES BYTEA NOT NULL,
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
