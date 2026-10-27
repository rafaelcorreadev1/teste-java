create table activation_request (
    id            uuid         primary key,
    external_id   varchar(64)  not null,
    customer_id   varchar(64)  not null,
    product_code  varchar(64)  not null,
    status        varchar(16)  not null,
    version       bigint       not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    constraint uk_activation_request_external_id unique (external_id),
    constraint ck_activation_request_status check (status in ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);
