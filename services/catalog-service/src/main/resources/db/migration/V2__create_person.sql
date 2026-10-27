-- Limits here mirror PersonValidator, so the database rejects anything the service would.
CREATE TABLE person (
    id         uuid PRIMARY KEY,
    -- ICU's language-neutral collation sorts names alphabetically, ignoring case and accents
    -- ("Émile" between "denis" and "Jeremy"). The database default cannot be relied on: on the
    -- Alpine image, en_US.utf8 compares bytes, which puts every accented capital after "z".
    name       text        COLLATE "und-x-icu" NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    bio        text        CHECK (length(bio) <= 2000),
    birth_date date        CHECK (birth_date >= DATE '1800-01-01'),
    -- Starts at 1 so an omitted expected_version (proto3 sends 0) never matches.
    version    integer     NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Backs the list order: by name, with id as the tie-breaker so pages are stable.
CREATE INDEX person_name_id_idx ON person (name, id);
