-- Hibernate writes Instants into "timestamp" columns as UTC wall-clock time, while column defaults and SQL
-- using CURRENT_TIMESTAMP write the session's (JVM's) wall-clock time, so the two disagreed by the zone offset.
-- With "timestamptz" every writer stores the same instant. Existing values were written by Hibernate, in UTC.
alter table users
    alter column created_at type timestamptz using created_at at time zone 'UTC',
    alter column updated_at type timestamptz using updated_at at time zone 'UTC';
alter table categories
    alter column created_at type timestamptz using created_at at time zone 'UTC',
    alter column updated_at type timestamptz using updated_at at time zone 'UTC';
alter table tickets
    alter column resolved_at type timestamptz using resolved_at at time zone 'UTC',
    alter column closed_at type timestamptz using closed_at at time zone 'UTC',
    alter column created_at type timestamptz using created_at at time zone 'UTC',
    alter column updated_at type timestamptz using updated_at at time zone 'UTC';
alter table comments
    alter column created_at type timestamptz using created_at at time zone 'UTC',
    alter column updated_at type timestamptz using updated_at at time zone 'UTC';
alter table ticket_attachments
    alter column created_at type timestamptz using created_at at time zone 'UTC',
    alter column updated_at type timestamptz using updated_at at time zone 'UTC';
