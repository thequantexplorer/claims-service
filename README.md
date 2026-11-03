# Northwind Insurance: claims-service

Calculates, schedules and pays insurance claims for Northwind's claims platform.

| Package | What it does |
|---|---|
| `intake` | Validates new claims (reporting window, installments, payee) |
| `coverage` | Payable amount after deductible and coverage limit |
| `payouts` | Monthly installment schedules |
| `fraud` | Risk score; high scores go to special investigations |
| `documents` | Required documents per claim type |
| `notifications` | Customer status emails |

Consumers: `claims-portal` (customer web portal), `adjuster-desktop` (Windows app), `field-adjuster-ios` (iOS app).

```sh
./mvnw -B verify          # build + tests
./mvnw spring-boot:run    # http://localhost:8080
```
