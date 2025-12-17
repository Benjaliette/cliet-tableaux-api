-- V3__Alter_users_add_resfresh_token_delete_reset_password_slug.sql

ALTER TABLE users DROP COLUMN reset_password_token;
ALTER TABLE users DROP COLUMN reset_password_sent_at;
ALTER TABLE users DROP COLUMN remember_created_at;
ALTER TABLE users DROP COLUMN slug;

ALTER TABLE users
ADD refresh_token varchar(512);