# Finance Service

## Pré-requisito da migration

A migration inicial cria índices de busca parcial com `pg_trgm`. O usuário que
executa o Flyway precisa ter privilégio para `CREATE EXTENSION`, ou a extensão
`pg_trgm` deve ser pré-provisionada no banco PostgreSQL antes da inicialização
do serviço.
