# Finance Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir o Finance Service do Life OS, com persistência PostgreSQL, regras financeiras determinísticas, consumo idempotente dos eventos do Task Service, jobs e API REST completa.

**Architecture:** Aplicação Quarkus modular por capacidade (`account`, `category`, `transaction`, `installment`, `recurring`, `budget`, `goal`, `tag`, `report`, `taskintegration`), com entidades Panache encapsuladas por repositories e services transacionais. Recursos REST somente validam/transcodificam HTTP; cálculos monetários e regras de escopo ficam em classes de domínio puras; Kafka e jobs chamam os mesmos services.

**Tech Stack:** Java 21, Quarkus 3.31.2, Maven Wrapper, Hibernate ORM with Panache, PostgreSQL, Flyway, Quarkus REST Jackson, Hibernate Validator, SmallRye Reactive Messaging Kafka, SmallRye Fault Tolerance, Scheduler, JUnit 5, AssertJ, REST Assured e Testcontainers.

## Global Constraints

- Usar Java 21 e o BOM `io.quarkus.platform:quarkus-bom:3.31.2`; essa versão e os artefatos foram conferidos na documentação oficial Quarkus.
- Dependências Quarkus: `quarkus-rest-jackson`, `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-flyway`, `quarkus-hibernate-validator`, `quarkus-messaging-kafka`, `quarkus-smallrye-fault-tolerance`, `quarkus-scheduler`, `quarkus-junit5` e `rest-assured`.
- Banco isolado `finance_db`; migrations são a única forma de criar/alterar schema.
- Dinheiro é sempre `BigDecimal` com escala 2 e `RoundingMode.UNNECESSARY`; persistência usa `numeric(19,2)`. Nunca usar `float` ou `double`.
- Moeda padrão `BRL`; datas financeiras usam `LocalDate`; auditoria usa UTC (`Instant` no contrato e `LocalDateTime` UTC no banco).
- Cada comando deste plano parte da raiz do repositório e usa prefixo `rtk`.
- Cada tarefa executa exatamente um ciclo TDD: teste falho, RED observado, implementação mínima, GREEN e commit.
- Arquivos permanecem focados: resource delega ao service; repository só consulta; domínio puro calcula; DTO não contém regra.
- Não implementar autenticação, multiusuário, API Gateway, storage remoto ou novas mensagens: não constam da Spec Finance.

## Ambiguidades técnicas resolvidas

1. **Identidade do projeto:** `groupId=dev.iury.lifeos`, `artifactId=finance-service`, pacote raiz `dev.iury.lifeos.finance`, porta HTTP `8082`.
2. **Conta usada pelo Kafka:** “primeira conta CHECKING ativa” significa menor `createdAt`, com desempate por `id`; ausência de conta gera falha recuperável e segue retry/DLQ.
3. **Valor Kafka ausente:** embora transações normais exijam `amount > 0`, o contrato manda usar `0.00`; transações originadas de tarefa são a única exceção e permanecem pendentes até edição/confirmação.
4. **Categoria Kafka:** o seed cria uma categoria de sistema EXPENSE chamada literalmente `Não Categorizado`; o consumidor a busca por `isSystem=true`, `type=EXPENSE`, `name`.
5. **Contrato temporal Kafka:** `completedAt`, `reopenedAt` e `timestamp` são `Instant`; `taskId` é `UUID`; `expectedAmount` é `BigDecimal` anulável. Nomes JSON, `eventType` e tópicos são literais às Specs Task e Finance.
6. **DLQ:** canal incoming usa failure strategy `dead-letter-queue` com tópico `task.completed.events.DLQ`; `@Retry(maxRetries = 3, delay = 2000)` fica no método de aplicação chamado pelo consumer, permitindo que a falha chegue ao connector após as tentativas.
7. **TaskReopenedEvent:** o payload literal é `{eventType, taskId, reopenedAt, timestamp}` no tópico `task.reopened.events`; evento sem transação é idempotente; transação pendente recebe `deletedAt`; paga é preservada com warning.
8. **Ajuste de saldo:** como `amount` não carrega sinal, o lançamento usa `accountId` como origem quando precisa reduzir e `destinationAccountId=accountId` quando precisa aumentar, exclusivamente para `BALANCE_ADJUSTMENT`; `BalanceEffect` converte isso em sinal. A API nunca aceita esse tipo no POST genérico.
9. **Saldo realizado:** considera lançamentos pagos com `date <= hoje`; saldo previsto considera todos com `date <= último dia do mês corrente`; ambos ignoram soft-deleted.
10. **Recorrência “esta e próximas”:** divide a série: encerra a regra antiga no dia anterior à ocorrência, cria nova regra com `startDate` na data selecionada e migra transações futuras não pagas para a nova regra. Pagas nunca são alteradas.
11. **Fim da recorrência:** uma ocorrência em `endDate` é gerada; após gerá-la, a regra é desativada.
12. **Anexos:** MVP usa filesystem local configurável em `finance.attachments.directory`, nome físico UUID, MIME JPEG/PNG/PDF, limite 10 MiB e máximo cinco; a API oferece upload e remoção, exatamente como a tabela de endpoints (não adiciona download).
13. **Paginação:** resposta é `PageResponse<T>(items,page,size,totalElements,totalPages)`; filtros `accountId` e `accountIds` são unidos sem duplicação.
14. **Percentuais sem divisor:** savings rate com receita zero é `0.00`; percentuais de budget/goal com denominador zero são `0.00`.
15. **Cópias mensais:** conflito de chave única no mês destino retorna 400 e toda a cópia é atômica.
16. **Exclusão permanente de conta:** apaga por cascade transações, anexos e vínculos de tags daquela conta; transferências em que ela é destino também são apagadas, conforme “TODOS os lançamentos históricos da conta”.

## Contratos Kafka literais

O teste de contrato da Task 12 deve usar estes documentos sem renomear, acrescentar ou remover campos:

```json
{
  "eventType": "TASK_COMPLETED",
  "taskId": "uuid",
  "title": "Pagar conta de luz",
  "expectedAmount": 150.00,
  "completedAt": "2026-07-22T15:00:00Z",
  "timestamp": "2026-07-22T15:00:01Z"
}
```

Tópico: `task.completed.events`.

```json
{
  "eventType": "TASK_REOPENED",
  "taskId": "uuid",
  "reopenedAt": "2026-07-22T16:00:00Z",
  "timestamp": "2026-07-22T16:00:01Z"
}
```

Tópico: `task.reopened.events`.

## Mapa de arquivos

- `pom.xml`, `.mvn/wrapper/*`, `mvnw`, `mvnw.cmd`: build reproduzível.
- `src/main/resources/application.properties`: PostgreSQL, Flyway, Kafka, scheduler, limites HTTP.
- `src/main/resources/db/migration/V1__finance_schema.sql`: schema, constraints, índices.
- `src/main/resources/db/migration/V2__seed_categories.sql`: categorias literais da Spec.
- `src/main/java/dev/iury/lifeos/finance/common/*`: dinheiro, relógio, paginação e erros HTTP.
- Cada pacote de capacidade contém entidade, repository, DTO, service e resource apenas quando necessário.
- `src/test/java/.../domain/*Test.java`: regras puras.
- `src/test/java/.../*RepositoryTest.java`: PostgreSQL real via Dev Services/Testcontainers.
- `src/test/java/.../*ResourceTest.java`: contrato HTTP REST Assured.
- `src/test/java/.../taskintegration/*Test.java`: Kafka real e idempotência.
- `README.md`: execução, configuração, endpoints, jobs e contratos Kafka.

---

### Task 1: Bootstrap Quarkus e configuração executável

**Files:**
- Create: `pom.xml`
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Create: `mvnw`
- Create: `mvnw.cmd`
- Create: `src/main/resources/application.properties`
- Test: `src/test/java/dev/iury/lifeos/finance/BootstrapTest.java`

**Interfaces:**
- Produces: aplicação Java 21; datasource `finance_db`; profile test com Dev Services PostgreSQL e Kafka.

- [ ] **Step 1: Write the failing test**

Crie `BootstrapTest` com `@QuarkusTest` e um teste `applicationStarts()` que injeta `AgroalDataSource`, abre conexão e afirma `connection.isValid(2)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk mvn test -Dtest=BootstrapTest`
Expected: FAIL porque ainda não há projeto Maven/configuração Quarkus.

- [ ] **Step 3: Write minimal implementation**

Gere o wrapper Maven 3.9.11 e `pom.xml` com Java 21, BOM 3.31.2 e todas as dependências da seção Global Constraints, mais `org.assertj:assertj-core` em test. Configure `quarkus.http.port=8082`, datasource PostgreSQL apontando a `${DB_URL:jdbc:postgresql://localhost:5432/finance_db}`, usuário/senha `${DB_USER:postgres}`/`${DB_PASSWORD:postgres}`, Flyway migrate-at-start, `%test.quarkus.datasource.devservices.enabled=true`, `%test.quarkus.kafka.devservices.enabled=true`, attachment directory e limite multipart 10 MiB.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=BootstrapTest`
Expected: PASS, com PostgreSQL Dev Service saudável.

- [ ] **Step 5: Commit**

Run: `rtk git add pom.xml .mvn mvnw mvnw.cmd src/main/resources/application.properties src/test/java/dev/iury/lifeos/finance/BootstrapTest.java && rtk git commit -m "build: bootstrap finance service"`

### Task 2: Schema PostgreSQL e seed de categorias

**Files:**
- Create: `src/main/resources/db/migration/V1__finance_schema.sql`
- Create: `src/main/resources/db/migration/V2__seed_categories.sql`
- Test: `src/test/java/dev/iury/lifeos/finance/migration/FinanceMigrationTest.java`

**Interfaces:**
- Produces: tabelas `account`, `category`, `financial_transaction`, `attachment`, `installment_group`, `recurring_rule`, `budget`, `income_goal`, `tag`, `transaction_tag`; FKs, uniques e índices.

- [ ] **Step 1: Write the failing test**

Crie teste Quarkus que consulta `information_schema.tables` e exige as nove tabelas; consulte `category` e exija `Não Categorizado` e `Ajuste de Saldo` com `is_system=true`; tente inserir budgets repetidos para confirmar unique `(category_id,year,month)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=FinanceMigrationTest`
Expected: FAIL com relação `account` inexistente.

- [ ] **Step 3: Write minimal implementation**

Escreva V1 com UUID PK, tipos `varchar` limitados, dinheiro `numeric(19,2)`, timestamps, checks de enum, escala/positividade, dois níveis de categoria, 15 tags por regra de service, FKs e índices para filtros da seção 6. Use `financial_transaction` para evitar palavra reservada. Escreva V2 com todas as categorias e subcategorias literais da seção 4.2.1, marcadas `is_system=true`, inclusive as duas de Sistema com tipo EXPENSE.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=FinanceMigrationTest`
Expected: PASS.

- [ ] **Step 5: Commit**

Run: `rtk git add src/main/resources/db/migration src/test/java/dev/iury/lifeos/finance/migration && rtk git commit -m "feat: add finance database schema"`

### Task 3: Tipos compartilhados, entidades e precisão monetária

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/common/Money.java`
- Create: `src/main/java/dev/iury/lifeos/finance/common/TimeProvider.java`
- Create: `src/main/java/dev/iury/lifeos/finance/common/SystemTimeProvider.java`
- Create: `src/main/java/dev/iury/lifeos/finance/model/*.java`
- Test: `src/test/java/dev/iury/lifeos/finance/common/MoneyTest.java`
- Test: `src/test/java/dev/iury/lifeos/finance/model/EntityPersistenceTest.java`

**Interfaces:**
- Produces: `Money.scale(BigDecimal)`, enums exatos da Spec e entidades JPA correspondentes ao V1.

- [ ] **Step 1: Write the failing test**

Teste `Money.scale("10") == 10.00`, preservação de `0.01`, rejeição de `1.001`; persista e recarregue uma Account, Category, Transaction, RecurringRule, Budget, IncomeGoal, Tag, Attachment e InstallmentGroup e compare todos os campos essenciais.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=MoneyTest,EntityPersistenceTest`
Expected: FAIL por classes ausentes.

- [ ] **Step 3: Write minimal implementation**

Implemente `Money.scale` com `value.setScale(2, UNNECESSARY)` e `Money.cents`/`Money.fromCents`. Crie enums `AccountType`, `CategoryType`, `TransactionType`, `InstallmentStatus`, `RecurringFrequency`, `RolloverType`, `ProgressStatus`, `RecurrenceScope`, `Period`. Mapeie entidades com nomes/colunas do SQL, UUID gerado, `@Enumerated(STRING)`, callbacks de timestamps e relações LAZY; `TimeProvider` expõe `Instant instant()` e `LocalDate today()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=MoneyTest,EntityPersistenceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

Run: `rtk git add src/main/java/dev/iury/lifeos/finance/common src/main/java/dev/iury/lifeos/finance/model src/test/java/dev/iury/lifeos/finance/common src/test/java/dev/iury/lifeos/finance/model && rtk git commit -m "feat: map finance domain entities"`

### Task 4: Repositories e consultas PostgreSQL

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/repository/*.java`
- Create: `src/main/java/dev/iury/lifeos/finance/transaction/TransactionFilter.java`
- Test: `src/test/java/dev/iury/lifeos/finance/repository/FinanceRepositoryTest.java`

**Interfaces:**
- Produces: repositories por agregado; `TransactionRepository.search(TransactionFilter, Page)`; somas de saldo, budget, goal e relatórios; busca `findByLinkedTaskId`.

- [ ] **Step 1: Write the failing test**

Insira dataset com duas contas, pai/subcategoria, tags, lançamentos pagos/pendentes/ignorados/deletados. Exija busca combinada `accountIds + categoryId + isPaid + date + amount + ILIKE + tag + installment + recurring`, ordenação permitida, paginação, descendentes da categoria e agregações sem soft-deleted.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=FinanceRepositoryTest`
Expected: FAIL por repositories ausentes.

- [ ] **Step 3: Write minimal implementation**

Implemente Panache repositories focados. Construa JPQL com parâmetros nomeados; normalize `accountId/accountIds` em `LinkedHashSet`; aceite apenas `date,amount,description,createdAt` e `asc,desc`; converta `Period` em datas no service, não no repository. Crie queries explícitas para saldos, budget, goals e relatórios aplicando `deletedAt is null` e flags de exclusão.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=FinanceRepositoryTest`
Expected: PASS sobre PostgreSQL Dev Service.

- [ ] **Step 5: Commit**

Run: `rtk git add src/main/java/dev/iury/lifeos/finance/repository src/main/java/dev/iury/lifeos/finance/transaction/TransactionFilter.java src/test/java/dev/iury/lifeos/finance/repository && rtk git commit -m "feat: add finance repositories"`

### Task 5: Domínio de transações, saldos e contas

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/account/BalanceCalculator.java`
- Create: `src/main/java/dev/iury/lifeos/finance/account/AccountService.java`
- Create: `src/main/java/dev/iury/lifeos/finance/transaction/TransactionValidator.java`
- Create: `src/main/java/dev/iury/lifeos/finance/transaction/TransactionService.java`
- Test: `src/test/java/dev/iury/lifeos/finance/account/BalanceCalculatorTest.java`
- Test: `src/test/java/dev/iury/lifeos/finance/transaction/TransactionServiceTest.java`

**Interfaces:**
- Produces: CRUD de conta/transação; `Balance(realized,projected)`; pay/unpay; ajuste; archive/unarchive/hard delete.

- [ ] **Step 1: Write the failing test**

Cubra todas as combinações INCOME/EXPENSE/TRANSFER/BALANCE_ADJUSTMENT pagas e pendentes, limites de data, includeInTotal, tipo de categoria, transferência inválida, categoria automática de ajuste, diferença positiva/negativa/zero, arquivamento com saldo não zero e exclusão com confirmação diferente de `EXCLUIR`.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=BalanceCalculatorTest,TransactionServiceTest`
Expected: FAIL por services ausentes.

- [ ] **Step 3: Write minimal implementation**

Implemente cálculos com `BigDecimal`, escala 2 e sinais definidos na decisão 8. Valide conta ativa, amount, categoria/tipo e transferência. Faça services `@ApplicationScoped` e métodos mutadores `@Transactional`; ajuste zero não cria lançamento; archive exige saldo realizado zero; delete exige arquivada e confirmação literal.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=BalanceCalculatorTest,TransactionServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

Run: `rtk git add src/main/java/dev/iury/lifeos/finance/account src/main/java/dev/iury/lifeos/finance/transaction src/test/java/dev/iury/lifeos/finance/account src/test/java/dev/iury/lifeos/finance/transaction && rtk git commit -m "feat: implement accounts and transactions"`

### Task 6: Categorias e tags

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/category/CategoryService.java`
- Create: `src/main/java/dev/iury/lifeos/finance/tag/TagService.java`
- Test: `src/test/java/dev/iury/lifeos/finance/category/CategoryServiceTest.java`
- Test: `src/test/java/dev/iury/lifeos/finance/tag/TagServiceTest.java`

**Interfaces:**
- Produces: CRUD/archive/migração de categoria; CRUD e associação de tag limitada a 15.

- [ ] **Step 1: Write the failing test**

Teste limite de dois níveis, tipo herdado do pai, proteção de sistema, bloqueio por filhos/transações, migração atômica para categoria do mesmo tipo, nome único de tag, 15 associações e exclusão que só remove vínculos.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=CategoryServiceTest,TagServiceTest`
Expected: FAIL por services ausentes.

- [ ] **Step 3: Write minimal implementation**

Implemente serviços transacionais, comparações case-insensitive de nomes, contagens antes de delete, update em lote na migração e tabela associativa sem cascade para Transaction.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=CategoryServiceTest,TagServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

Run: `rtk git add src/main/java/dev/iury/lifeos/finance/category src/main/java/dev/iury/lifeos/finance/tag src/test/java/dev/iury/lifeos/finance/category src/test/java/dev/iury/lifeos/finance/tag && rtk git commit -m "feat: implement categories and tags"`

### Task 7: Parcelamentos

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/installment/InstallmentCalculator.java`
- Create: `src/main/java/dev/iury/lifeos/finance/installment/InstallmentService.java`
- Test: `src/test/java/dev/iury/lifeos/finance/installment/InstallmentCalculatorTest.java`
- Test: `src/test/java/dev/iury/lifeos/finance/installment/InstallmentIntegrationTest.java`

**Interfaces:**
- Produces: `List<InstallmentSlice> split(BigDecimal,int,LocalDate,String)`; criar/listar/detalhar/cancelar grupo.

- [ ] **Step 1: Write the failing test**

Use teste parametrizado para valores de 0.01 a 1000.00 e parcelas 2 a 60: soma deve ser total, primeira absorve resto e demais são iguais; teste 12x end-to-end com datas `plusMonths`, labels `1/12..12/12`; cancelamento preserva pagas.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=InstallmentCalculatorTest,InstallmentIntegrationTest`
Expected: FAIL por calculator ausente.

- [ ] **Step 3: Write minimal implementation**

Converta total para centavos exatos, aplique fórmula literal da Spec, gere grupo e N despesas pendentes numa transação; valide total > 0 e N >= 2; cancelamento marca grupo CANCELED e soft-delete apenas parcelas pendentes.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=InstallmentCalculatorTest,InstallmentIntegrationTest`
Expected: PASS.

- [ ] **Step 5: Commit**

Run: `rtk git add src/main/java/dev/iury/lifeos/finance/installment src/test/java/dev/iury/lifeos/finance/installment && rtk git commit -m "feat: implement installment purchases"`

### Task 8: Recorrência e escopos de alteração

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/recurring/RecurringDateCalculator.java`
- Create: `src/main/java/dev/iury/lifeos/finance/recurring/RecurringService.java`
- Test: `src/test/java/dev/iury/lifeos/finance/recurring/RecurringDateCalculatorTest.java`
- Test: `src/test/java/dev/iury/lifeos/finance/recurring/RecurringScopeTest.java`

**Interfaces:**
- Produces: próxima data para oito frequências; CRUD de regra; `updateOccurrence(id,scope,command)` e `deleteOccurrence(id,scope)`.

- [ ] **Step 1: Write the failing test**

Cubra DAILY/WEEKLY/BIWEEKLY/MONTHLY/BIMONTHLY/QUARTERLY/SEMI_ANNUALLY/ANNUALLY, dia 31 em fevereiro bissexto/não bissexto, dayOfWeek, endDate inclusiva e os três escopos de edição/exclusão, provando que pagas ficam intactas.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=RecurringDateCalculatorTest,RecurringScopeTest`
Expected: FAIL por classes ausentes.

- [ ] **Step 3: Write minimal implementation**

Implemente cálculo com `YearMonth.lengthOfMonth`, incrementos exatos e avanço semanal; implemente desvinculação em ONLY_THIS, split de regra em THIS_AND_FUTURE e update somente não pagas em ALL, conforme decisões 10/11; delete sempre usa soft delete.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=RecurringDateCalculatorTest,RecurringScopeTest`
Expected: PASS.

- [ ] **Step 5: Commit**

Run: `rtk git add src/main/java/dev/iury/lifeos/finance/recurring src/test/java/dev/iury/lifeos/finance/recurring && rtk git commit -m "feat: implement recurring transactions"`

### Task 9: Budgets, rollover e metas de receita

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/budget/BudgetCalculator.java`
- Create: `src/main/java/dev/iury/lifeos/finance/budget/BudgetService.java`
- Create: `src/main/java/dev/iury/lifeos/finance/goal/IncomeGoalService.java`
- Test: `src/test/java/dev/iury/lifeos/finance/budget/BudgetCalculatorTest.java`
- Test: `src/test/java/dev/iury/lifeos/finance/budget/BudgetGoalIntegrationTest.java`

**Interfaces:**
- Produces: status GREEN/YELLOW/RED; três rollovers; CRUD/copy de budgets e goals.

- [ ] **Step 1: Write the failing test**

Teste 0, 79.99, 80, 99.99 e 100%; includePending; independência pai/filho; FULL_ROLLOVER positivo/negativo, POSITIVE_ONLY e NO_ROLLOVER; categorias por tipo; cópia atômica e conflito; goal somente INCOME paga.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=BudgetCalculatorTest,BudgetGoalIntegrationTest`
Expected: FAIL por classes ausentes.

- [ ] **Step 3: Write minimal implementation**

Implemente fórmulas literais com percentuais escala 2/HALF_UP. Services validam mês 1..12, valor > 0, categoria correta e unicidade; copy replica limite/target e tipo, aplica rollover calculado ao Budget e usa uma transação.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=BudgetCalculatorTest,BudgetGoalIntegrationTest`
Expected: PASS.

- [ ] **Step 5: Commit**

Run: `rtk git add src/main/java/dev/iury/lifeos/finance/budget src/main/java/dev/iury/lifeos/finance/goal src/test/java/dev/iury/lifeos/finance/budget && rtk git commit -m "feat: implement budgets and income goals"`

### Task 10: Anexos locais seguros

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/attachment/AttachmentStorage.java`
- Create: `src/main/java/dev/iury/lifeos/finance/attachment/FileSystemAttachmentStorage.java`
- Create: `src/main/java/dev/iury/lifeos/finance/attachment/AttachmentService.java`
- Test: `src/test/java/dev/iury/lifeos/finance/attachment/AttachmentServiceTest.java`

**Interfaces:**
- Produces: `add(transactionId,fileName,mime,size,InputStream)` e `remove(transactionId,attachmentId)`.

- [ ] **Step 1: Write the failing test**

Com `@TempDir`, teste JPEG/PNG/PDF, rejeite MIME diferente, tamanho acima de 10 MiB, sexto anexo, nome com `../`, transação inexistente e remoção do arquivo/metadado.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=AttachmentServiceTest`
Expected: FAIL por storage ausente.

- [ ] **Step 3: Write minimal implementation**

Ignore caminho do nome original, gere UUID físico sob diretório normalizado, valide `resolved.startsWith(root)`, grave por stream e remova arquivo se persistência falhar; service aplica limites e ownership.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=AttachmentServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

Run: `rtk git add src/main/java/dev/iury/lifeos/finance/attachment src/test/java/dev/iury/lifeos/finance/attachment && rtk git commit -m "feat: add transaction attachments"`

### Task 11: Relatórios e agregações

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/report/ReportService.java`
- Create: `src/main/java/dev/iury/lifeos/finance/report/ReportDtos.java`
- Test: `src/test/java/dev/iury/lifeos/finance/report/ReportServiceTest.java`

**Interfaces:**
- Produces: monthly summary, category breakdown, cash flow, budget status, income goals e by-tag com filtros de período.

- [ ] **Step 1: Write the failing test**

Monte dataset com receitas/despesas, transfer, ajuste, ignored e tags. Afirme totais, ordem decrescente, percentuais, savings rate normal/receita zero, net mensal, exclusões por tipo/flag e todos os filtros `month/year/startDate/endDate`.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=ReportServiceTest`
Expected: FAIL por ReportService ausente.

- [ ] **Step 3: Write minimal implementation**

Implemente records de retorno com nomes literais da Spec e service read-only que usa agregações dos repositories, exclui tipos/flags exatamente conforme seção 8 e calcula percentuais escala 2.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=ReportServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

Run: `rtk git add src/main/java/dev/iury/lifeos/finance/report src/test/java/dev/iury/lifeos/finance/report && rtk git commit -m "feat: add finance reports"`

### Task 12: Consumidores Kafka compatíveis com Task Service

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/taskintegration/TaskCompletedEvent.java`
- Create: `src/main/java/dev/iury/lifeos/finance/taskintegration/TaskReopenedEvent.java`
- Create: `src/main/java/dev/iury/lifeos/finance/taskintegration/TaskEventService.java`
- Create: `src/main/java/dev/iury/lifeos/finance/taskintegration/TaskEventConsumer.java`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/dev/iury/lifeos/finance/taskintegration/TaskEventContractTest.java`
- Test: `src/test/java/dev/iury/lifeos/finance/taskintegration/TaskEventKafkaTest.java`

**Interfaces:**
- Consumes: tópicos/payloads literais definidos nas Specs 02 §11 e 03 §7.
- Produces: lançamento idempotente por `linkedTaskId`; reabertura segura; DLQ.

- [ ] **Step 1: Write the failing test**

Desserialize e serialize os JSONs literais das Specs e compare árvore JSON campo a campo. Com Kafka Dev Service, publique completed duas vezes e espere uma despesa; valide amount ausente = 0.00; publique reopened para pendente/paga; force três falhas e consuma registro de `task.completed.events.DLQ`.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=TaskEventContractTest,TaskEventKafkaTest`
Expected: FAIL por records/canais ausentes.

- [ ] **Step 3: Write minimal implementation**

Crie records com `@JsonPropertyOrder`, campos e tipos da decisão 5. Configure incoming channels `task-completed`/`task-reopened`, topics literais, Jackson deserializer, earliest offset e DLQ literal. Consumer usa `@Incoming`; service transacional usa constraint unique em `linked_task_id`, `@Retry(maxRetries=3,delay=2000)`, conta/categoria determinísticas e warning SLF4J para reabertura paga.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=TaskEventContractTest,TaskEventKafkaTest`
Expected: PASS; payload de round-trip é literal e duplicata mantém uma linha.

- [ ] **Step 5: Commit**

Run: `rtk git add src/main/java/dev/iury/lifeos/finance/taskintegration src/main/resources/application.properties src/test/java/dev/iury/lifeos/finance/taskintegration && rtk git commit -m "feat: consume task lifecycle events"`

### Task 13: Jobs agendados

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/recurring/RecurringTransactionGenerator.java`
- Create: `src/main/java/dev/iury/lifeos/finance/budget/BudgetRolloverJob.java`
- Test: `src/test/java/dev/iury/lifeos/finance/recurring/RecurringTransactionGeneratorTest.java`
- Test: `src/test/java/dev/iury/lifeos/finance/budget/BudgetRolloverJobTest.java`

**Interfaces:**
- Produces: cron diário `0 1 0 * * ?`; cron mensal `0 5 0 1 * ?`; métodos package-private executáveis em teste.

- [ ] **Step 1: Write the failing test**

Fixe TimeProvider; teste catch-up de múltiplas datas sem duplicar, autoConfirm, atualização de índice/lastGeneratedDate, endDate inclusiva/desativação e clamp fevereiro. Teste aplicação mensal única dos três rollovers.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=RecurringTransactionGeneratorTest,BudgetRolloverJobTest`
Expected: FAIL por jobs ausentes.

- [ ] **Step 3: Write minimal implementation**

Anote jobs com `@Scheduled` nos crons exatos. Gerador mantém loop até próxima data > hoje, persiste ocorrência e lastGeneratedDate atomicamente e usa unique `(recurring_rule_id,recurring_instance_index)`. Rollover cria/atualiza próximo Budget uma vez e respeita unique mensal.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=RecurringTransactionGeneratorTest,BudgetRolloverJobTest`
Expected: PASS.

- [ ] **Step 5: Commit**

Run: `rtk git add src/main/java/dev/iury/lifeos/finance/recurring/RecurringTransactionGenerator.java src/main/java/dev/iury/lifeos/finance/budget/BudgetRolloverJob.java src/test/java/dev/iury/lifeos/finance/recurring/RecurringTransactionGeneratorTest.java src/test/java/dev/iury/lifeos/finance/budget/BudgetRolloverJobTest.java && rtk git commit -m "feat: schedule recurring and rollover jobs"`

### Task 14: Erros HTTP e DTOs REST

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/common/error/*.java`
- Create: `src/main/java/dev/iury/lifeos/finance/common/error/ApiExceptionMapper.java`
- Create: `src/main/java/dev/iury/lifeos/finance/api/dto/*.java`
- Test: `src/test/java/dev/iury/lifeos/finance/common/error/ApiExceptionMapperTest.java`

**Interfaces:**
- Produces: `ApiError(error,message,status,timestamp)` e requests/responses Bean Validation para todos os endpoints.

- [ ] **Step 1: Write the failing test**

Teste mapeamento de cada exceção/status da seção 11, timestamp UTC, mensagem de categoria com nome/contagem e violações Bean Validation como `VALIDATION_ERROR` 400.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=ApiExceptionMapperTest`
Expected: FAIL por mapper ausente.

- [ ] **Step 3: Write minimal implementation**

Crie hierarquia `FinanceException(code,status,message)`, subclasses literais da Spec, mapper único e DTO records pequenos. Use `@NotNull`, `@Size`, `@Positive`, regex hex/MIME e constraints compostas validadas nos services; nunca exponha entidades JPA.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=ApiExceptionMapperTest`
Expected: PASS.

- [ ] **Step 5: Commit**

Run: `rtk git add src/main/java/dev/iury/lifeos/finance/common/error src/main/java/dev/iury/lifeos/finance/api/dto src/test/java/dev/iury/lifeos/finance/common/error && rtk git commit -m "feat: standardize finance api errors"`

### Task 15: REST de contas, transações, categorias e tags

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/api/AccountResource.java`
- Create: `src/main/java/dev/iury/lifeos/finance/api/TransactionResource.java`
- Create: `src/main/java/dev/iury/lifeos/finance/api/CategoryResource.java`
- Create: `src/main/java/dev/iury/lifeos/finance/api/TagResource.java`
- Test: `src/test/java/dev/iury/lifeos/finance/api/CoreResourcesTest.java`

**Interfaces:**
- Produces: todos os endpoints das seções 9.1–9.4; base `/api/finance`.

- [ ] **Step 1: Write the failing test**

Com REST Assured, cubra CRUD, archive/unarchive/delete/adjust, pay/unpay, detalhe, soft delete, multipart add/remove, migração, tags e todos os filtros combinando ao menos dois; valide 200/201/204 e erros 400/403/404.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=CoreResourcesTest`
Expected: FAIL com 404 para `/api/finance/accounts`.

- [ ] **Step 3: Write minimal implementation**

Crie resources `@Path`, `@Consumes/@Produces`, `@Valid`, responses e locations. Parse query params nos tipos exatos, aplique defaults page=0,size=20,max=100, sortBy=date,sortDir=desc e converta `Period` via TimeProvider; resources somente mapeiam DTO/service.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=CoreResourcesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

Run: `rtk git add src/main/java/dev/iury/lifeos/finance/api src/test/java/dev/iury/lifeos/finance/api/CoreResourcesTest.java && rtk git commit -m "feat: expose core finance api"`

### Task 16: REST de parcelas, recorrências, budgets, goals e relatórios

**Files:**
- Create: `src/main/java/dev/iury/lifeos/finance/api/InstallmentResource.java`
- Create: `src/main/java/dev/iury/lifeos/finance/api/RecurringResource.java`
- Create: `src/main/java/dev/iury/lifeos/finance/api/BudgetResource.java`
- Create: `src/main/java/dev/iury/lifeos/finance/api/IncomeGoalResource.java`
- Create: `src/main/java/dev/iury/lifeos/finance/api/ReportResource.java`
- Test: `src/test/java/dev/iury/lifeos/finance/api/ExtendedResourcesTest.java`

**Interfaces:**
- Produces: todos os endpoints das seções 9.5–9.9.

- [ ] **Step 1: Write the failing test**

Com REST Assured, cubra CRUD/list/detail/cancel de parcelas, CRUD de regras, três scopes no PUT/DELETE de transação recorrente, CRUD/copy de budgets/goals e seis relatórios com cada forma de período.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=ExtendedResourcesTest`
Expected: FAIL com 404 para `/api/finance/installments`.

- [ ] **Step 3: Write minimal implementation**

Implemente resources finos e rotas literais da Spec. `scope` é query param obrigatório apenas quando `recurringRuleId != null`; copy exige quatro params; relatórios aceitam month/year ou startDate/endDate e rejeitam intervalos invertidos.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd test -Dtest=ExtendedResourcesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

Run: `rtk git add src/main/java/dev/iury/lifeos/finance/api src/test/java/dev/iury/lifeos/finance/api/ExtendedResourcesTest.java && rtk git commit -m "feat: expose finance planning api"`

### Task 17: Testes de contrato completos e documentação

**Files:**
- Create: `src/test/java/dev/iury/lifeos/finance/api/FinanceApiAcceptanceTest.java`
- Create: `src/test/java/dev/iury/lifeos/finance/api/AttachmentResourceTest.java`
- Create: `README.md`

**Interfaces:**
- Consumes: todas as capacidades anteriores.
- Produces: suíte de aceite rastreável às Specs e documentação operacional.

- [ ] **Step 1: Write the failing test**

Crie fluxo REST Assured único: conta → categorias → receitas/despesas/transfer → saldos → 12 parcelas → recorrência → budgets/goals → tags → relatórios → archive/delete. Crie multipart JPEG/PDF e erros de tamanho/quantidade. Antes da documentação, teste que README contém `finance_db`, `8082`, ambos os tópicos, DLQ, crons, comandos dev/test/package e tabela completa de endpoints.

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk .\mvnw.cmd test -Dtest=FinanceApiAcceptanceTest,AttachmentResourceTest`
Expected: FAIL na asserção de README inexistente ou em qualquer contrato ainda divergente.

- [ ] **Step 3: Write minimal implementation**

Corrija somente divergências reveladas. Escreva README com pré-requisitos Java 21/Docker, `rtk .\mvnw.cmd quarkus:dev`, variáveis DB/Kafka/storage, migrations, exemplos curl válidos, payloads Kafka literais das Specs, idempotência/retry/DLQ, jobs, regras monetárias, testes e todos os endpoints.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk .\mvnw.cmd clean verify`
Expected: BUILD SUCCESS; unitários, PostgreSQL, Kafka e REST Assured passam.

- [ ] **Step 5: Commit**

Run: `rtk git add README.md src/test/java/dev/iury/lifeos/finance/api && rtk git commit -m "docs: document finance service"`

## Verificação final de execução

Após Task 17:

1. Run: `rtk .\mvnw.cmd clean verify` — Expected: `BUILD SUCCESS`.
2. Run: `rtk git status --short` — Expected: sem saída.
3. Run: `rtk git log --oneline --reverse` — Expected: um commit por tarefa, em ordem banco/entidades → repositories → domínio → Kafka/jobs → REST/documentação.
4. Compare `TaskCompletedEvent` e `TaskReopenedEvent` com as Specs 02 §11 e 03 §7: nomes JSON, tópicos e eventType devem ser byte-for-byte equivalentes no teste de contrato.
