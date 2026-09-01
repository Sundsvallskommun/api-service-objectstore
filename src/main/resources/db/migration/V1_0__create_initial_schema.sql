-- The key columns are compared byte for byte rather than under the case-insensitive collation a MariaDB server
-- applies by default, so that the database and Hibernate agree on which row an id names. The charset is stated
-- rather than inherited so that a server configured differently cannot silently store something else.
create table stored_file (
    bucket        varchar(63)   not null collate utf8mb4_bin,
    id            varchar(36)   not null collate utf8mb4_bin,
    file_name     varchar(255),
    content_type  varchar(255),
    size_in_bytes bigint        not null,
    etag          varchar(64)   not null,
    content       longblob,
    created       datetime(6)   not null,
    expires_at    datetime(6),
    primary key (bucket, id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create index ix_stored_file_expires_at on stored_file (expires_at);

create table shedlock (
    name       varchar(64)  not null,
    lock_until timestamp(3) not null,
    locked_at  timestamp(3) not null default current_timestamp(3),
    locked_by  varchar(255) not null,
    primary key (name)
) engine = InnoDB;
