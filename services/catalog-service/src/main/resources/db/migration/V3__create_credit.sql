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
    -- NULLS NOT DISTINCT makes two creator credits without a character duplicates too.
    CONSTRAINT credit_unique UNIQUE NULLS NOT DISTINCT (movie_id, person_id, type, character_name)
);

-- Also used when blocking the delete of a credited person.
CREATE INDEX credit_person_id_idx ON credit (person_id);
