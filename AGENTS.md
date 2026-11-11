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
