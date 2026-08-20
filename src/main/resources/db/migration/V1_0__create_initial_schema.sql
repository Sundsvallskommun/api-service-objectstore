create table stored_file (
    bucket        varchar(63)   not null,
    id            varchar(36)   not null,
    file_name     varchar(255),
    content_type  varchar(255),
    size_in_bytes bigint        not null,
    etag          varchar(64)   not null,
    content       longblob,
    created       datetime(6)   not null,
    expires_at    datetime(6),
    primary key (bucket, id)
) engine = InnoDB;

create index ix_stored_file_expires_at on stored_file (expires_at);

create table shedlock (
    name       varchar(64)  not null,
    lock_until timestamp(3) not null,
    locked_at  timestamp(3) not null default current_timestamp(3),
    locked_by  varchar(255) not null,
    primary key (name)
) engine = InnoDB;
