# Design

## Context

O esqueleto vem de `bootstrap-project`: Spring Boot 3.5, Postgres via Testcontainers e Flyway com `ddl-auto=validate`. Ainda não há tabela nem endpoint. A motivação está em proposal.md e os requisitos nas specs `activation-requests` e `api-errors`.

## Goals / Non-Goals

**Goals:**
- Garantir a unicidade por `externalId` **no banco**, com teste concorrente que prove isso.
- Fazer a máquina de estados ser testável sem Spring.
- Separar os DTOs (records) da entidade JPA.

**Non-Goals:**
- Chamar o provedor (`add-provider-activation`). Nesta change a solicitação apenas nasce `PENDING`.
- Autenticação e isolamento por cliente (`add-api-security`).
- Listagem (`add-request-listing`).

## Decisions

### Modelo e migration V1
`V1__create_activation_request.sql`:

```sql
create table activation_request (
  id            uuid primary key,
  external_id   varchar(64)  not null,
  customer_id   varchar(64)  not null,
  product_code  varchar(64)  not null,
  status        varchar(16)  not null,
  version       bigint       not null,
  created_at    timestamptz  not null,
  updated_at    timestamptz  not null,
  constraint uk_activation_request_external_id unique (external_id),
  constraint ck_activation_request_status check (status in ('PENDING','PROCESSING','COMPLETED','FAILED'))
);
```

- O `id` é um UUID gerado pela aplicação (`UUID.randomUUID()`), e não sequencial. Assim ele não é enumerável e já existe antes do insert, o que permite montar o `Location`.
- As datas são `Instant` mapeadas para `timestamptz`, com `hibernate.jdbc.time_zone=UTC`. O Jackson serializa em ISO-8601 com `Z`.
- O `check` de status replica no banco a regra do enum, como defesa extra.

### Unicidade (ver [ADR 0003](../../../docs/adr/0003-unicidade-externalid-constraint-banco.md))
Fluxo de `ActivationRequestService.create(command)`:

1. `ActivationRequestWriter.insert(...)`, com `@Transactional`, faz `saveAndFlush`. O `flush` força o `INSERT` dentro do método, para que a violação estoure ali e não no commit.
2. Se ocorrer `DataIntegrityViolationException` **e** a causa for a constraint `uk_activation_request_external_id` (via `ConstraintViolationException#getConstraintName` do Hibernate), a transação que falhou já foi encerrada. Então busca-se `findByExternalId` em uma leitura nova e:
   - com conteúdo igual, `CreateResult.Existing(request)` → `200`;
   - com conteúdo diferente, `DuplicateRequestException` → `409`.
3. Outras violações de integridade são propagadas e viram `500`.

O método de serviço **não** é `@Transactional`, porque a leitura de recuperação precisa acontecer fora da transação marcada para rollback. O resultado é modelado como `sealed interface CreateResult permits Created, Existing`, e o controller faz `switch` com pattern matching para decidir entre `201` e `200`.

Não há *fast path* (`existsByExternalId` antes do insert): a constraint resolve os dois casos e o caminho fica único.

### Máquina de estados (ver [ADR 0004](../../../docs/adr/0004-maquina-de-estados-no-dominio.md))
- `enum ActivationStatus` com `allowedTargets()` e `canTransitionTo(target)`.
- A entidade `ActivationRequest` tem construtor de fábrica `ActivationRequest.create(externalId, customerId, productCode, clock)`, os métodos `startProcessing`, `complete`, `fail` e `returnToPending`, e nenhum setter público. Cada transição valida a mudança e atualiza `updatedAt`. O `Clock` injetado deixa os testes determinísticos.
- `@Version Long version` dá o lock otimista.

### Erros (ver [ADR 0005](../../../docs/adr/0005-erros-problem-details.md))
- `ApiExceptionHandler extends ResponseEntityExceptionHandler`, sobrescrevendo `handleMethodArgumentNotValid` (`errors[]`) e `handleHttpMessageNotReadable` (`malformed-request`). Também trata `MethodArgumentTypeMismatchException` (UUID inválido → 400), `ActivationRequestNotFoundException` (404), `DuplicateRequestException` (409) e `Exception` (500, com log `error`).
- `spring.mvc.problemdetails.enabled=true` e `server.error.include-stacktrace=never`.
- Nesta change o `traceId` vem do MDC (`traceId`), preenchido por um filtro simples (`CorrelationIdFilter`) que gera um UUID ou aceita `X-Correlation-Id`. Ele é criado aqui porque o contrato de erro depende dele; a change de observabilidade só o propaga para o provedor e para os logs JSON.

### Estrutura

```
request/
  api/        ActivationRequestController, CreateActivationRequest (record), ActivationRequestResponse (record)
  domain/     ActivationRequest, ActivationStatus, InvalidStatusTransitionException
  app/        ActivationRequestService, ActivationRequestWriter, CreateResult, exceções de negócio
  infra/      ActivationRequestRepository
shared/error/ ApiExceptionHandler, ProblemTypes
shared/web/   CorrelationIdFilter
```

## Risks / Trade-offs

- [Depender do nome da constraint para distinguir a violação] → O nome é fixado no migration (ADR 0002) e coberto pelo teste concorrente. Uma renomeação quebra o teste, não a produção silenciosamente.
- [A exceção no caminho de duplicidade custa mais que um `ON CONFLICT`] → Retry é exceção, não regra. A evolução para `INSERT ... ON CONFLICT DO NOTHING RETURNING` fica registrada no ADR.
- [Conflito de versão (`ObjectOptimisticLockingFailureException`) chegando à API] → Nesta change só o worker altera status, e ele trata o conflito (próxima change). Pela API vira `409` genérico.

## Migration Plan

Migration V1 aditivo, sem dados a migrar. O rollback é feito removendo a tabela (ambiente novo).
