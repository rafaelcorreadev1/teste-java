# Tasks

## 1. Consulta

- [ ] 1.1 Criar `V4__add_listing_indexes.sql` e verificar com `EXPLAIN` (em teste de integração ou manualmente) que os filtros por status e por customerId usam os índices
- [ ] 1.2 Criar `ActivationRequestSpecifications` (cliente obrigatório, status e customerId opcionais) e verificar com teste de integração cada combinação de filtro e o isolamento por cliente

## 2. API

- [ ] 2.1 Criar `PageResponse<T>` e o endpoint `GET /api/v1/requests` com validação de `page`/`size`/`status`, e verificar com `@WebMvcTest`: padrão 20, `size=1000` → 400, `status=DONE` → 400
- [ ] 2.2 Verificar com teste de integração a paginação com 25 registros (page 1, size 10 → 10 itens, `totalPages` 3) e a ordem por `createdAt desc`

## 3. Documentação e estudo

- [ ] 3.1 Documentar no README a listagem e a tabela de índices com justificativa, e verificar que bate com o V4
- [ ] 3.2 Marcar ADR 0009 como Aceito e verificar o índice
- [ ] 3.3 Atualizar a seção `add-request-listing` em `estudo/estudo.html`, regerar o PDF e verificar a seção no PDF
