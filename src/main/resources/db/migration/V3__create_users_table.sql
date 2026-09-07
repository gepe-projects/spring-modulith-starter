-- Example feature module "user" (see agents.md §3, §9 and README).
-- Ids are UUID v7 generated in the application — the column only stores them.
-- Uniqueness of email is a DATABASE constraint (not an application pre-check):
-- it stays correct when several instances insert concurrently (agents.md §11).
CREATE TABLE users
(
    id    UUID         NOT NULL,
    name  VARCHAR(100) NOT NULL,
    email VARCHAR(320) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_users_email ON users (email);
