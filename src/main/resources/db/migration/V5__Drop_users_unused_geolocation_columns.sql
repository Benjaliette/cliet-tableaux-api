-- V5__Drop_users_unused_geolocation_columns.sql

ALTER TABLE users DROP COLUMN latitude;
ALTER TABLE users DROP COLUMN longitude;
ALTER TABLE users DROP COLUMN address;
