# Tasks

## 1. Reorganização

- [x] 1.1 Mover as classes do processamento assíncrono para `request.processing` (main e testes), mantendo package-private o que só o worker usa, e verificar que compila
- [x] 1.2 Adicionar a regra ArchUnit "`request.app` não depende de `request.processing`" e verificar que passa
- [x] 1.3 Verificar com `./mvnw verify` que todos os testes continuam passando

## 2. Documentação e estudo

- [x] 2.1 Registrar a estrutura de pacotes no caderno de estudo, regerar o PDF e verificar
