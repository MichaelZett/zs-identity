-- Stands for an application that drew a baseline over its own history, the
-- way tennistournament did with B20: everything below version 20 is taken as
-- applied and never runs. With a shared history this swallowed the building
-- block's V1_x migrations; with a history of our own it must not matter.
CREATE TABLE app_setting
(
    key   VARCHAR(64) PRIMARY KEY,
    value VARCHAR(256)
);
