USE Starexec;

ALTER TABLE jobs ADD COLUMN using_dependencies BOOLEAN NOT NULL DEFAULT FALSE;