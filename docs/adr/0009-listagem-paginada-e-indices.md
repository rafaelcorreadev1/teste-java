# 0009. Listar com paginação e filtros, com índices compostos guiados pelas consultas

- Status: Aceito
- Data: 2026-10-27
- Change: `add-request-listing`

## Contexto
`GET /api/v1/requests?status=&customerId=&page=&size=` é um diferencial. O cenário de análise mostra consultas que passaram de 50 ms para 1,2 s, sintoma clássico de índice faltando ou de tabela crescendo sem paginação.

## Decisão
- Paginação por *offset* (`page`/`size`), com `size` máximo de 100 e ordenação fixa por `created_at DESC, id`.
- Filtros opcionais, sempre combinados com o `client_id` do chamador (ADR 0008).
- Índices, cada um justificado por uma consulta:

| Índice | Atende |
|---|---|
| `UNIQUE (client_id, external_id)` | Regra de duplicidade (já existe). |
| `(client_id, customer_id, created_at DESC)` | Listagem por consumidor + `customerId`. |
| `(client_id, status, created_at DESC)` | Listagem por consumidor + `status`. |
| `(next_attempt_at) WHERE status = 'PENDING'` (parcial) | Busca do worker. Fica pequeno porque só tem pendentes. |
| `(processing_started_at) WHERE status = 'PROCESSING'` (parcial) | Reconciliação. |

## Alternativas consideradas
- **Paginação por cursor (keyset)**: melhor em tabelas enormes, porque o offset degrada em páginas profundas. Fica descrito como evolução.
- **Um índice por coluna**: o planner combina mal, e o composto com `client_id` na frente casa com o filtro que é sempre aplicado.

## Consequências
- Cada índice custa na escrita, então só entram índices que têm uma consulta correspondente.
- A resposta usa um DTO de página próprio (`content`, `page`, `size`, `totalElements`, `totalPages`) para não expor o `Page` do Spring como contrato.
