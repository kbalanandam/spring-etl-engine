-- Optional grants template for control-plane MySQL schema.
-- Replace <APP_USER> and <APP_HOST> before execution.
-- Example:
--   CREATE USER IF NOT EXISTS 'etl_app'@'%' IDENTIFIED BY 'change-me';
--   SOURCE scripts/sql/mysql/controlplane-grants-template.sql;

USE `etl_controlplane`;

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP
ON `etl_controlplane`.*
TO '<APP_USER>'@'<APP_HOST>';

FLUSH PRIVILEGES;

