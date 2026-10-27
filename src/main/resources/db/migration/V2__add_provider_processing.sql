alter table activation_request
    add column attempts              int          not null default 0,
    add column next_attempt_at       timestamptz  not null default now(),
    add column processing_started_at timestamptz,
    add column failure_reason        varchar(64);

create index ix_activation_request_due
    on activation_request (next_attempt_at)
    where status = 'PENDING';

create index ix_activation_request_processing
    on activation_request (processing_started_at)
    where status = 'PROCESSING';
