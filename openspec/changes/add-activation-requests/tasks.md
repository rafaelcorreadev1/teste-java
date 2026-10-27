# Tasks

## 1. Domínio

- [ ] 1.1 Criar `ActivationStatus` com transições permitidas e verificar com teste unitário parametrizado cobrindo toda a matriz origem×destino
- [ ] 1.2 Criar a entidade `ActivationRequest` (fábrica, métodos de transição, `@Version`, sem setters) e verificar com teste unitário sem Spring (transição válida atualiza `updatedAt`, terminal rejeita, `PENDING→COMPLETED` rejeita)

## 2. Persistência

- [ ] 2.1 Criar `V1__create_activation_request.sql` com a constraint `uk_activation_request_external_id` e o check de status, e verificar que o contexto sobe com `ddl-auto=validate`
- [ ] 2.2 Criar `ActivationRequestRepository` (`findByExternalId`) e verificar com `@DataJpaTest` + Testcontainers que um insert duplicado viola a constraint pelo nome esperado
- [ ] 2.3 Verificar o lock otimista com teste de integração: duas cópias da mesma versão, a segunda gravação falha e o status permanece o da primeira

## 3. Caso de uso de criação e consulta

- [ ] 3.1 Implementar `ActivationRequestWriter` (insert com flush), `ActivationRequestService` (`create` → `CreateResult` e `findById`) e verificar com teste unitário (Mockito) os ramos criado, retry igual e conteúdo divergente
- [ ] 3.2 Escrever o teste concorrente (10 threads, `CountDownLatch`, Postgres real) e verificar 1×`Created`, 9×`Existing` e 1 linha no banco

## 4. API e erros

- [ ] 4.1 Criar os records `CreateActivationRequest` (Bean Validation: `@NotBlank`, `@Size(max=64)`, `@Pattern`) e `ActivationRequestResponse`, e verificar que a entidade não é exposta
- [ ] 4.2 Criar `ActivationRequestController` (`POST` com `201`+`Location` ou `200`; `GET /{id}`) e verificar com `@WebMvcTest` os status e o header
- [ ] 4.3 Criar `CorrelationIdFilter` e `ApiExceptionHandler` (validation-error, malformed-request, not-found, duplicate-request, internal-error) e verificar com `@WebMvcTest` cada `type`, `errors[]`, `traceId` e a ausência de stack trace no 500
- [ ] 4.4 Escrever o teste de integração ponta a ponta (MockMvc + Postgres): criar, consultar, reenviar igual (200), reenviar divergente (409), id inexistente (404), e verificar que passa em `./mvnw verify`

## 5. Documentação e estudo

- [ ] 5.1 Documentar no README a seção "Decisões técnicas → Duplicidade", com a tabela de respostas, e verificar que bate com os testes
- [ ] 5.2 Marcar ADR 0003, 0004 e 0005 como Aceito e verificar o índice em `docs/adr/README.md`
- [ ] 5.3 Atualizar a seção `add-activation-requests` em `estudo/estudo.html`, regerar o PDF e verificar a seção no PDF
