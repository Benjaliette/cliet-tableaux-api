-- V2__Alter_paintings_price_slug_image.sql

ALTER TABLE paintings
ALTER COLUMN price_cents TYPE BIGINT;

ALTER TABLE paintings DROP COLUMN slug;
ALTER TABLE paintings DROP COLUMN thumbnail_url;

ALTER TABLE paintings RENAME COLUMN image_url TO image_public_id;