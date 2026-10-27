# Design

## Context

O repositório já filtra por `client_id` (change `add-api-security`). Decisão completa em [ADR 0009](../../../docs/adr/0009-listagem-paginada-e-indices.md).

## Goals / Non-Goals

**Goals:**
- Cada filtro tem um índice que o atende, comprovado por `EXPLAIN`.
- O contrato de página é estável e não vaza o `Page` do Spring.

**Non-Goals:**
- Paginação por cursor (descrita como evolução).
- Filtro por intervalo de datas ou por `productCode`.

## Decisions

- **Consulta**: um único `@Query` JPQL com filtros opcionais (`(:status is null or r.status = :status)`) é simples, mas o Postgres planeja mal o padrão "parâmetro nulo ou igual" com prepared statements genéricos. Por isso a escolha é **Spring Data `Specification`**, que monta só os predicados presentes, e o plano usa o índice certo. Ter quatro métodos derivados (`findByClientIdAndStatus...`) foi descartado por explodir em combinações.
- **Ordenação fixa** em `created_at desc, id desc`. O desempate por `id` evita itens repetidos ou pulados entre páginas quando há timestamps iguais. A ordenação livre ficou fora porque cada ordenação exigiria seu índice.
- **Validação**: `@Min(0) page`, `@Min(1) @Max(100) size` e `ActivationStatus status`. Um enum inválido cai no `MethodArgumentTypeMismatchException` → `400` (já tratado).
- **`PageResponse<T>`** é um record com `content`, `page`, `size`, `totalElements` e `totalPages`.
- **Migration V4**:

```sql
create index ix_activation_request_client_customer on activation_request (client_id, customer_id, created_at desc);
create index ix_activation_request_client_status   on activation_request (client_id, status, created_at desc);
```

  Sem filtro, a listagem usa o prefixo `client_id` de qualquer um dos dois, mas sem ordenar por índice. Se essa consulta virar a mais comum, entra `(client_id, created_at desc)`. A decisão fica registrada para revisão com dados reais (`pg_stat_statements`).

## Risks / Trade-offs

- [`count(*)` para `totalElements` é caro em tabelas grandes] → Aceitável no volume do desafio. A evolução é keyset sem total, ou total estimado.
- [Offset profundo degrada] → `size` máximo de 100 e a evolução para cursor ficam documentados.

## Migration Plan

O V4 só cria índices. Em produção com a tabela grande, usaria `CREATE INDEX CONCURRENTLY` em um migration separado e não transacional (`-- flyway:executeInTransaction=false`).
