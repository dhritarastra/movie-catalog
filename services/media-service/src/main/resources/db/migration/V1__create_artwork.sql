-- Metadata for stored images. The bytes live on disk under media.storage-dir/<id>/.
CREATE TABLE artwork (
    id           uuid PRIMARY KEY,
    content_type text        NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    size_bytes   bigint      NOT NULL CHECK (size_bytes > 0),
    width        integer     NOT NULL CHECK (width > 0),
    height       integer     NOT NULL CHECK (height > 0),
    -- Hash of the original file; the gateway uses it in the HTTP ETag.
    sha256       text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now()
);
