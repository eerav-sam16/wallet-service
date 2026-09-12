# Wallet Service — Paytm R2 Assignment

A wallet service with peer-to-peer transfers. Correct under concurrency.

## Quick Start (one command)
```bash
docker compose up --build
```

## Fund Test Wallets
```bash
# After docker compose up, seed wallet balances
docker exec -it wallet-postgres psql -U wallet_user -d walletdb \
  -c "UPDATE wallets SET balance_paise = 1000000 WHERE user_id IN ('user-001','user-002','user-003','user-004','user-005');"
```

## APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /wallets | Get or create wallet |
| GET | /wallets/{id} | Get balance |
| POST | /transfers | Transfer money |
| GET | /transfers/{id} | Get transfer status |

## Test Tokens (pre-seeded)

| User | Token |
|------|-------|
| user-001 | token-user-001 |
| user-002 | token-user-002 |
| user-003 | token-user-003 |

## Example Requests

```bash
# Create wallet
curl -X POST http://localhost:8080/wallets \
  -H "Authorization: Bearer token-user-001" \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-001"}'

# Transfer money
curl -X POST http://localhost:8080/transfers \
  -H "Authorization: Bearer token-user-001" \
  -H "Content-Type: application/json" \
  -d '{
    "from": "<wallet-id-a>",
    "to":   "<wallet-id-b>",
    "amountPaise": 5000,
    "idempotencyKey": "unique-key-001"
  }'
```

## Burst Test (all 3 gates)
```bash
chmod +x burst.sh
./burst.sh http://localhost:8080
```

## Observability
```
Health:  http://localhost:8080/actuator/health
Metrics: http://localhost:8080/actuator/prometheus
```

## Diagrams
See `docs/` folder:
- `01-entity-diagram.puml` — DB tables and constraints
- `02-sequence-diagrams.puml` — API flows
- `03-lld-class-diagram.puml` — Code structure

Render at: https://www.planttext.com
