# Northwind claims-service: agent instructions

- Java 17, Spring Boot. Build and test with `./mvnw -B verify`. Run the service with `./mvnw spring-boot:run` (http://localhost:8080).
- Before opening or updating a pull request, `./mvnw -B verify` must pass (compile, unit tests, integration tests).
- Money is integer cents. Never round per installment; use `Money.splitEvenly` so installments add back up to the total.
- Customers may only read their own claims. Load claims through `ClaimAccess.findVisible`, which returns 404 for other customers' claims.
- Every bug fix ships with a regression test.
- Test layout: unit tests next to each package under `src/test/java`; API integration tests use `@SpringBootTest` + `MockMvc` against the in-memory H2 database seeded by `SeedData`.
