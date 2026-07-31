# Finance Service

## Pré-requisito da migration

A migration inicial cria índices de busca parcial com `pg_trgm`. O usuário que
executa o Flyway precisa ter privilégio para `CREATE EXTENSION`, ou a extensão
`pg_trgm` deve ser pré-provisionada no banco PostgreSQL antes da inicialização
do serviço.

## Deploy em `develop`

Pushes para `develop` executam o workflow `.github/workflows/develop-deploy.yml`.
O workflow roda os testes com Java 21, publica a imagem no GHCR e atualiza somente
o stack em `/opt/finan-service` na VPS.

Configure estes secrets no repositório GitHub antes do primeiro deploy:

- `VPS_HOST`: IP ou hostname da VPS.
- `VPS_USER`: usuário SSH, normalmente `ubuntu`.
- `VPS_SSH_KEY`: chave privada usada pelo GitHub Actions para acessar a VPS.
- `GHCR_USERNAME`: usuário que possui um token de leitura do pacote.
- `GHCR_READ_TOKEN`: token com permissão mínima `read:packages`.

Na VPS, crie `/opt/finan-service/.env` a partir de `deploy/.env.example`, trocando
`DB_PASSWORD` por uma senha longa e aleatória. Esse arquivo não deve ser commitado.

O PostgreSQL e os anexos usam volumes Docker persistentes. A aplicação fica
disponível na porta `8082`.