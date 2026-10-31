-- Same collation as person names, so search can lower() and sort both the same way.
ALTER TABLE movie ALTER COLUMN title TYPE text COLLATE "und-x-icu";
