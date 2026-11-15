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

## Caller identity

Requests under `/api/` must carry `X-User-Id` and `X-User-Role` signed by the API gateway: `X-Gateway-Timestamp` (epoch seconds, within 5 minutes) and `X-Gateway-Signature`, the hex HMAC-SHA256 of `userId\nrole\ntimestamp\nMETHOD\npath[?query]` keyed with the shared secret (`GATEWAY_SIGNING_SECRET`, see `GatewaySignature`). Without a configured secret every `/api/` request gets 401. Run the service on an internal network that only the gateway can reach.
