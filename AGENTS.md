# Repository Guidelines

## Project Structure & Module Organization

ViaCidada is a Java 21/Spring Boot REST API. Application code is under
`src/main/java/br/com/viacidada`, organized by responsibility: `controller`,
`service`, `repository`, `entity`, `dto`, `mapper`, `security`, `client`,
`config`, `exception`, and `specification`. Configuration profiles and related
resources live in `src/main/resources` (`application-h2.yml`,
`application-test.yml`, and `application-oracle.yml`). Integration tests are in
`src/test/java`. `target/` is generated output and should not be edited or
committed.

## Build, Test, and Development Commands

Use the Maven wrapper so the project uses its declared Maven setup:

```powershell
.\mvnw.cmd test                 # Compile and run all tests
.\mvnw.cmd spring-boot:run     # Run locally with the default H2 profile
.\mvnw.cmd clean package       # Clean, test, and package the application
```

Set `SPRING_PROFILES_ACTIVE=oracle` only when Oracle credentials and a safe
schema are available. `SPTRANS_TOKEN` enables real SPTrans calls; tests use the
`test` profile and must not depend on external services.

## Coding Style & Naming Conventions

Follow existing Spring conventions: four-space indentation, one public type per
file, `PascalCase` for classes/enums, `camelCase` for methods and fields, and
uppercase names for enum constants. Keep HTTP concerns in controllers, business
rules in services, persistence in repositories, and API contracts in DTOs.
Reuse the existing Lombok, Bean Validation, exception-handler, JWT, HATEOAS,
and cache patterns. No repository formatter or linter is configured; keep
imports clean and run `git diff --check` when working from a Git checkout.

## Testing Guidelines

Tests use JUnit 5, Spring Boot Test, MockMvc, and Spring Security Test. Name
test classes with the `*Tests` suffix and test methods descriptively (the
existing suite uses Portuguese `@DisplayName` descriptions). Cover endpoint
status/JSON contracts, authentication and authorization, validation, and
service/repository behavior. Run `.\mvnw.cmd test` before submitting changes.

## Security & Configuration Tips

Never commit JWT secrets, database passwords, or `SPTRANS_TOKEN`; use
environment variables. Keep Oracle `ddl-auto` at `validate` or `none` in shared
environments, and do not run schema-changing commands against shared databases.

## Commit & Pull Request Guidelines

Git history is not available in this checkout, so no project-specific commit
format could be verified. Use short imperative messages, for example
`Add accessibility occurrence filtering`. Pull requests should explain the
behavioral change, list validation commands and results, identify configuration
or API changes, and include request/response examples or screenshots when they
clarify an endpoint change.

## Educational Pair Programming & Architecture Mentorship (Mentoria e Explicação Didática)

O desenvolvedor deste projeto é um programador iniciante em fase de aprendizado de arquitetura de software, ecossistema Spring Boot e boas práticas. Além disso, **este projeto será apresentado e defendido perante professores e bancas acadêmicas**. Portanto, a velocidade de geração de código nunca deve atropelar o aprendizado: o desenvolvedor precisa ter total consciência e domínio de cada etapa, decisão e linha implementada.

Ao trabalhar neste repositório, o agente deve seguir rigorosamente as seguintes diretrizes pedagógicas:

1. **Explicar o Papel de Cada Camada e Arquivo**:
   - Sempre que criar ou alterar código, descreva explicitamente em qual camada ele reside (`controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `specification`, `security`, `config`, etc.) e qual é a sua única responsabilidade.
   - Reforce conceitos de arquitetura em camadas e Separação de Responsabilidades (SRP/SOLID) de forma prática.

2. **Justificar o "Porquê" de Cada Decisão Técnica**:
   - Não apenas mostre *o que* foi feito, mas explique *por que* essa solução foi adotada em comparação a alternativas (ex.: por que usamos DTOs em vez de expor a Entidade diretamente no Controller? Por que usar `Specification` para filtros dinâmicos em vez de dezenas de `findBy`? Por que colocar regras de negócio no `@Service` e não no Controller? Por que usamos `@Cacheable` em consultas repetitivas?).
   - Ensine o desenvolvedor a pensar criticamente e a tomar decisões arquiteturais por conta própria.

3. **Didática e Linguagem Acessível (sem perder a excelência técnica)**:
   - Responda e explique sempre em português claro, direto e didático, usando analogias simples quando o conceito for abstrato.
   - **Não reduza a qualidade do código**: continue aplicando rigorosamente as melhores metodologias (Clean Code, Design Patterns, Bean Validation, testes unitários e de integração, segurança com JWT, OpenAPI/Swagger). O objetivo é **elevar o conhecimento do desenvolvedor até o nível da solução**, e não nivelar a solução por baixo.

4. **Preparação para Apresentação Acadêmica (Argumentação para Professores)**:
   - Ao finalizar cada funcionalidade relevante ou refatoração, inclua um bloco rápido de orientação para a banca:
     - **Resumo do que foi feito**: Síntese executiva da feature.
     - **Como defender para o professor**: 1 a 3 argumentos técnicos sólidos caso os professores perguntem o motivo daquela abordagem ou a vantagem arquitetural daquela decisão.

5. **Passo a Passo Consciente**:
   - Em tarefas complexas ou estruturais, quebre a implementação em etapas claras para que o desenvolvedor acompanhe a evolução do raciocínio em vez de receber um bloco maciço e hermético de código sem contexto.
