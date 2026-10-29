-- A person's role on a movie. Limits here mirror CreditInput, so the database rejects anything
-- the service would.
CREATE TABLE credit (
    id             uuid PRIMARY KEY,
    -- Deleting a movie deletes its credits; a person with credits cannot be deleted.
    movie_id       uuid        NOT NULL REFERENCES movie (id) ON DELETE CASCADE,
    person_id      uuid        NOT NULL REFERENCES person (id) ON DELETE RESTRICT,
    type           text        NOT NULL CHECK (type IN ('CAST', 'DIRECTOR', 'WRITER', 'PRODUCER')),
    character_name text        CHECK (length(btrim(character_name)) BETWEEN 1 AND 200),
    billing_order  integer     NOT NULL CHECK (billing_order BETWEEN 0 AND 1000),
    created_at     timestamptz NOT NULL DEFAULT now(),
    -- Only cast credits name a character.
    CONSTRAINT credit_character_only_for_cast CHECK (type = 'CAST' OR character_name IS NULL),
    -- The same person cannot hold the same role (and character) twice on one movie. NULLS NOT
    -- DISTINCT makes two creator credits with no character name duplicates too (Postgres 15+).
    -- Its index starts with movie_id, so it also serves lookups by movie.
    CONSTRAINT credit_unique UNIQUE NULLS NOT DISTINCT (movie_id, person_id, type, character_name)
);

-- Backs lookups by person, and the check that blocks deleting a credited person.
CREATE INDEX credit_person_id_idx ON credit (person_id);
