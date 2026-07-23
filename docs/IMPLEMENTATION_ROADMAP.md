# Finance Service — Roadmap de Implementação

Este documento registra a ordem recomendada para continuar a implementação em sessões separadas, sem repetir trabalho já integrado.

## Estado atual

- Tasks 1–4 concluídas: bootstrap Quarkus, PostgreSQL/Flyway, seed, entidades, tipos compartilhados e repositories.
- Commits de referência: `be0de46`, `76440cd`, `9136d4c`, `37dd840` e `32bf364`.
- A branch `main` estava limpa antes desta documentação.
- A suíte Maven precisa ser executada com JDK 21. A tentativa com o JDK atual falhou antes dos testes com `release version 21 not supported`.

## Ordem de execução

### Parte 0 — Validar a fundação

Configurar Temurin JDK 21, executar `rtk .\\mvnw.cmd test` e corrigir somente problemas encontrados nessa fundação.

### Parte 1 — Domínio financeiro básico

Implementar Tasks 5–6:

- transações, saldos e contas;
- regras de categorias e tags;
- testes unitários e PostgreSQL a cada tarefa.

### Parte 2 — Planejamento financeiro

Implementar Tasks 7–9:

- parcelamentos;
- recorrências e escopos de alteração;
- budgets, rollover e metas de receita.

### Parte 3 — Processamento e consultas

Implementar Tasks 10–13:

- anexos locais seguros;
- relatórios e agregações;
- consumidores Kafka idempotentes e DLQ;
- jobs agendados.

### Parte 4 — API REST

Implementar Tasks 14–16:

- erros HTTP e DTOs;
- endpoints de contas, transações, categorias e tags;
- endpoints de parcelas, recorrências, budgets, goals e relatórios.

### Parte 5 — Aceite final

Implementar Task 17:

- testes de contrato completos;
- testes end-to-end;
- documentação de execução e integração.

## Regra para cada parte

Cada Task deve seguir o ciclo: teste falho, execução RED, implementação mínima, execução GREEN e commit. Não reimplementar Tasks 1–4; usar o plano detalhado em `docs/superpowers/plans/2026-07-23-finance-service.md` como referência técnica.

## Próximo passo

Começar pela Parte 0 e, após a suíte passar com JDK 21, iniciar a Task 5.
