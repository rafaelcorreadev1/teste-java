# Proposal

## Why

Hoje os consumidores só consultam uma solicitação por vez, pelo `id`. Para conciliar o próprio estado (por exemplo, "quais solicitações deste cliente falharam?"), eles precisam listar com filtros. É um diferencial do desafio que também exige justificar índices, o que se liga ao cenário de lentidão do item 10 (consultas de 50 ms → 1,2 s).

## What Changes

- `GET /api/v1/requests?status=&customerId=&page=&size=` com filtros opcionais combináveis, paginação por offset e ordenação da mais recente para a mais antiga.
- `size` padrão 20 e máximo 100. Valores inválidos resultam em `400`.
- A listagem respeita o isolamento por cliente consumidor.
- Migration V4 com índices compostos para os filtros, cada um justificado no README.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `activation-requests`: novo requisito de listagem paginada com filtros.

## Impact

- Novo método no controller e no repositório (consulta com filtros opcionais).
- DTO de página próprio (`PageResponse<T>`).
- Migration V4 (índices).
- ADR 0009.
