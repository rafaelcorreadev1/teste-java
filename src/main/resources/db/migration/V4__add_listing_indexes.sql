-- client_id na frente porque toda listagem filtra pelo consumidor autenticado;
-- created_at desc no fim atende à ordenação sem sort adicional.
create index ix_activation_request_client_customer
    on activation_request (client_id, customer_id, created_at desc);

create index ix_activation_request_client_status
    on activation_request (client_id, status, created_at desc);
