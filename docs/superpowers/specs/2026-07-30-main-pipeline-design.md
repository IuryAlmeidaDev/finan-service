# Pipeline de deploy para main

## Objetivo

Executar a entrega completa quando houver push em `main`: validar o projeto, publicar a imagem da release e atualizar a VPS.

## Workflow

Um workflow independente, `main-deploy.yml`, será acionado por push em `main` e por `workflow_dispatch`.

1. O job `test` executa `mvn -B test` com Temurin 21 e cache Maven.
2. O job `build-and-deploy`, dependente dos testes, autentica no GHCR e publica `ghcr.io/iuryalmeidadev/finan-service:main`.
3. O mesmo job conecta à VPS pelos secrets atuais, executa `IMAGE_TAG=main docker compose pull app`, sobe o serviço e confirma que o endpoint responde.

## Isolamento e falhas

As imagens `:develop` e `:main` permanecem distintas. Qualquer falha em testes, build, push, SSH ou health check impede as etapas posteriores e marca a execução como falha.

## Validação

O arquivo será validado por inspeção do YAML, por execução manual do workflow após o merge e pela conferência dos jobs Test e Build and deploy no GitHub Actions.
