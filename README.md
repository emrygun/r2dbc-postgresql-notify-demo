# PostgreSQL R2DBC Notiy Chat Example Application

## Summary
Yet another demo usage of PostgreSQL `triggers`, `notify` and `listen` capabilities with R2DBC drivers.

The project includes Testcontainers initialization of database container (Postgresql 15) and database migrations 
(as well as the `trigger` definitions) with Liquibase.

## Basic Requirements
* Java 17 or higher.
* Docker version > 1.6.0 
* Free 5432 port for PostgreSQL

## Trigger Definitions
The PostgreSQL trigger is used to keep track of messages inserted into table. Functions and migration details are 
specified on Liquibase changelog configuration.

From `classpath:db/migration/changelog.yml:`:
```postgresql
CREATE FUNCTION on_new_message() RETURNS trigger as $$
    DECLARE
    BEGIN
        PERFORM pg_notify('message_insert_channel', row_to_json(NEW)::text);
        RETURN NEW;
    END $$
LANGUAGE plpgsql
```
```postgresql
CREATE TRIGGER new_message_insert
AFTER INSERT
ON message
FOR EACH ROW
EXECUTE PROCEDURE on_new_message()
```

---
## Author
Emre Uygun\
contact@emreuygun.dev