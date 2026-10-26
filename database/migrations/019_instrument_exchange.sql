BEGIN;

ALTER TABLE instruments
    ADD COLUMN exchange text;

COMMENT ON COLUMN instruments.exchange IS
    'Listing venue from the instrument catalog; OTC identifies crypto and forex reference instruments.';

COMMIT;
