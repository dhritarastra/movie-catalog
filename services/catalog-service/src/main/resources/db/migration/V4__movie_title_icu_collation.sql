-- Titles use the same ICU collation as person names, so lower() folds every letter ("ZOË" to
-- "zoë") and titles sort the same way as names whatever the database's own locale. Search
-- relies on both: it compares lowered text and orders movies and people in one list.
ALTER TABLE movie ALTER COLUMN title TYPE text COLLATE "und-x-icu";
