-- Limits here mirror MovieValidator, so the database rejects anything the service would.
CREATE TABLE movie (
    id              uuid PRIMARY KEY,
    title           text        NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 200),
    synopsis        text        CHECK (length(synopsis) <= 2000),
    release_date    date,
    runtime_minutes integer     CHECK (runtime_minutes BETWEEN 1 AND 1000),
    genres          text[]      NOT NULL DEFAULT '{}',
    -- Owned by media-service, so there is no foreign key across the service boundary.
    artwork_id      uuid,
    -- Starts at 1 so an omitted expected_version (proto3 sends 0) never matches.
    version         integer     NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

-- Backs the list order: newest first, with id as the tie-breaker so pages are stable.
CREATE INDEX movie_created_at_id_idx ON movie (created_at DESC, id DESC);
