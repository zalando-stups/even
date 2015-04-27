CREATE TYPE access_request_status AS ENUM (
    'REQUESTED',
    'GRANTED',
    'DENIED',
    'FAILED',
    'REVOKED'
);

CREATE TABLE access_requests (
    ar_id serial PRIMARY KEY,
    ar_username TEXT NOT NULL,
    ar_hostname TEXT NOT NULL,
    ar_reason TEXT NOT NULL,
    ar_remote_host TEXT,
    ar_status access_request_status NOT NULL DEFAULT 'REQUESTED',
    ar_status_reason TEXT,
    ar_lifetime_minutes INTEGER NOT NULL,
    ar_created timestamp NOT NULL DEFAULT now(),
    ar_created_by TEXT,
    ar_last_modified timestamp NOT NULL DEFAULT now(),
    ar_last_modified_by TEXT,

    CONSTRAINT username_pattern CHECK (ar_username ~ '^[a-z][a-z0-9-]{0,31}$'),
    CONSTRAINT hostname_pattern CHECK (ar_hostname ~ '^[a-z0-9.-]{0,255}$'),
    CONSTRAINT remote_host_pattern CHECK (ar_remote_host ~ '^[a-z0-9.-]{0,255}$'),
    CONSTRAINT lifetime_minutes_range CHECK (ar_lifetime_minutes > 0)
);
