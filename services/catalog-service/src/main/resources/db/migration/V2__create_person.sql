CREATE TABLE person (
    id         uuid PRIMARY KEY,
    -- ICU collation sorts names alphabetically; the Alpine image's en_US.utf8 compares bytes.
    name       text        COLLATE "und-x-icu" NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    bio        text        CHECK (length(bio) <= 2000),
    birth_date date        CHECK (birth_date >= DATE '1800-01-01'),
    -- Starts at 1, since proto3 sends 0 for a missing expected_version.
    version    integer     NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX person_name_id_idx ON person (name, id);
