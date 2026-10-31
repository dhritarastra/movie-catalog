CREATE TABLE movie (
    id              uuid PRIMARY KEY,
    title           text        NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 200),
    synopsis        text        CHECK (length(synopsis) <= 2000),
    release_date    date,
    runtime_minutes integer     CHECK (runtime_minutes BETWEEN 1 AND 1000),
    genres          text[]      NOT NULL DEFAULT '{}',
    -- Owned by media-service, so no foreign key.
    artwork_id      uuid,
    -- Starts at 1, since proto3 sends 0 for a missing expected_version.
    version         integer     NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX movie_created_at_id_idx ON movie (created_at DESC, id DESC);
