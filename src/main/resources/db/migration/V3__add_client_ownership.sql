alter table activation_request add column client_id varchar(128);

-- Solicitações criadas antes da autenticação não têm dono conhecido.
update activation_request set client_id = 'legacy' where client_id is null;

alter table activation_request alter column client_id set not null;

alter table activation_request drop constraint uk_activation_request_external_id;
alter table activation_request
    add constraint uk_activation_request_client_external_id unique (client_id, external_id);
