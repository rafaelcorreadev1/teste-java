# api-errors Specification

## Purpose
Define um contrato de erro único e legível por máquina para todos os consumidores da API, sem expor detalhes internos da aplicação.

## Requirements

### Requirement: Formato padronizado de erro
Toda resposta de erro da API SHALL usar `Content-Type: application/problem+json` e conter `type`, `title`, `status`, `detail`, `instance` e `traceId`. O `type` MUST ser um identificador estável por categoria de erro.

#### Scenario: Erro de recurso inexistente
- **WHEN** o cliente consulta uma solicitação inexistente
- **THEN** a resposta tem `Content-Type` `application/problem+json`
- **AND** contém `status` 404, `type` terminado em `/problems/not-found`, `instance` com o caminho chamado e um `traceId` não vazio

### Requirement: Detalhar erros de validação por campo
Erros de validação SHALL incluir `errors`, uma lista com `field` e `message` para cada campo inválido.

#### Scenario: Dois campos inválidos
- **WHEN** o cliente envia uma solicitação sem `customerId` e sem `productCode`
- **THEN** a resposta `400` tem `type` terminado em `/problems/validation-error`
- **AND** `errors` contém exatamente os campos `customerId` e `productCode`

### Requirement: Rejeitar corpo malformado
Um corpo que não é JSON válido ou com tipos incompatíveis MUST resultar em `400` com `type` `malformed-request`, sem ecoar a mensagem do parser.

#### Scenario: JSON inválido
- **WHEN** o cliente envia `{"externalId": "EXT-1",` (JSON truncado)
- **THEN** o sistema responde `400` com `type` terminado em `/problems/malformed-request`

### Requirement: Não expor detalhes internos
Erros inesperados MUST resultar em `500` com mensagem genérica. Nenhuma resposta de erro SHALL conter stack trace, nome de classe, SQL ou mensagem de exceção interna. O detalhe completo vai apenas para o log, associado ao mesmo `traceId`.

#### Scenario: Falha inesperada
- **WHEN** ocorre uma exceção não prevista ao processar a requisição
- **THEN** o sistema responde `500` com `type` terminado em `/problems/internal-error` e `detail` genérico
- **AND** o corpo não contém `Exception`, `at com.` nem SQL
- **AND** o log registra a exceção com o mesmo `traceId` da resposta
