# Northwind Insurance: claims-service

Calculates, schedules and pays insurance claims for Northwind's claims platform.

| Package | What it does |
|---|---|
| `intake` | Validates new claims (reporting window, installments, payee) |
| `payouts` | `PayoutCalculator`: payable amount after deductible and coverage limit, and monthly installment schedules |
| `fraud` | Risk score; high scores go to special investigations |
| `documents` | Required documents per claim type |
| `notifications` | Customer status emails |
| `attachments` | Customer photos, receipts and PDFs on open claims (upload, list, download) |

Consumers: `claims-portal` (customer web portal), `adjuster-desktop` (Windows app), `field-adjuster-ios` (iOS app).

```sh
./mvnw -B verify          # build + tests
./mvnw spring-boot:run    # http://localhost:8080
```

## Claim attachments API

All requests need the gateway's `X-User-Id` and `X-User-Role` headers (401 otherwise). Claims the caller cannot see return 404 before any other check, so other customers' claims are never revealed.

| Endpoint | Who | Success | Errors (checked in this order) |
|---|---|---|---|
| `POST /api/claims/{id}/attachments` (multipart, field `file`) | Owning `CUSTOMER` | `201` + attachment JSON + `Location` header | `404` claim not visible, `403` not a customer, `409` claim not `SUBMITTED`/`IN_REVIEW`, `413` claim already has 20 files, `413` file over 10 MB, `400` missing or empty file, `415` not JPEG/PNG/PDF |
| `GET /api/claims/{id}/attachments` | Anyone who can see the claim | `200` + array of attachment JSON, oldest first, no file bytes | `404` |
| `GET /api/claims/{id}/attachments/{attachmentId}` | Anyone who can see the claim | `200` + raw bytes | `404`, including an attachment that belongs to a different claim |

Attachment JSON:

```json
{
  "id": "5b0d6c1e-8a8f-4c55-9a43-0f1a3c7b2d11",
  "claimId": "CLM-1002",
  "fileName": "dent.jpg",
  "contentType": "image/jpeg",
  "sizeBytes": 48213,
  "uploadedBy": "cust-ana",
  "uploadedAt": "2026-11-17T10:15:30.123456Z"
}
```

- The file type comes from the file's first bytes only (JPEG `FF D8 FF`, PNG `89 50 4E 47 0D 0A 1A 0A`, PDF `%PDF-`). The client's `Content-Type` and file extension are ignored.
- `fileName` is the uploaded name with path components and control characters removed, cut to 255 characters.
- Downloads send the stored `Content-Type`, `Content-Disposition: attachment; filename*=UTF-8''<fileName>` and `X-Content-Type-Options: nosniff`.
- Attachments are append-only: there is no delete or rename.

```sh
curl -H 'X-User-Id: cust-ana' -H 'X-User-Role: CUSTOMER' \
  -F file=@dent.jpg http://localhost:8080/api/claims/CLM-1002/attachments
```
