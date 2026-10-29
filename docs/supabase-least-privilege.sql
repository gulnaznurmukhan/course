-- ClientFlow: least-privilege database role for the running application (Supabase).
--
-- Run once in Supabase -> SQL Editor AFTER the first deployment has created the
-- "clientflow" schema (Flyway). Replace the password before running.
--
-- Afterwards:
--   DB_USERNAME        = clientflow_app.<project-ref>     (pooler user name format)
--   DB_PASSWORD        = the password below
--   FLYWAY_DB_USERNAME = postgres.<project-ref>           (only migrations use the admin account)
--   FLYWAY_DB_PASSWORD = the database password from Supabase
--
-- The application role can read and change data in "clientflow" only. It cannot
-- create or drop tables, cannot touch other schemas and cannot manage roles.

CREATE ROLE clientflow_app WITH LOGIN PASSWORD 'REPLACE-WITH-A-LONG-RANDOM-PASSWORD';

GRANT USAGE ON SCHEMA clientflow TO clientflow_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA clientflow TO clientflow_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA clientflow TO clientflow_app;

-- Tables created by later migrations (run as postgres) get the same rights automatically
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA clientflow
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO clientflow_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA clientflow
    GRANT USAGE, SELECT ON SEQUENCES TO clientflow_app;

-- Flyway's history table is only read by the application at startup (validation)
REVOKE INSERT, UPDATE, DELETE ON clientflow.flyway_schema_history FROM clientflow_app;
