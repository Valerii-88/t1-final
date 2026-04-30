--liquibase formatted sql

--changeset limit-service:001-init-limit-schema
create table limit_config (
    id bigint primary key,
    default_limit numeric(19,2) not null check (default_limit >= 0),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

insert into limit_config (id, default_limit, created_at, updated_at)
values (1, 100000.00, current_timestamp, current_timestamp);

create table user_limit (
    user_id varchar(128) primary key,
    available_amount numeric(19,2) not null check (available_amount >= 0),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table limit_operation (
    operation_id varchar(128) primary key,
    user_id varchar(128) not null references user_limit (user_id),
    amount numeric(19,2) not null check (amount > 0),
    status varchar(16) not null check (status in ('RESERVED', 'CONFIRMED', 'CANCELED')),
    created_at timestamptz not null,
    confirmed_at timestamptz null,
    canceled_at timestamptz null
);

create index idx_limit_operation_user_id on limit_operation (user_id);
create index idx_limit_operation_status on limit_operation (status);
