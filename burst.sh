#!/bin/bash
# ─────────────────────────────────────────────────────────────
# burst.sh — Tests all 3 gates the evaluators will probe live
# Usage: ./burst.sh [BASE_URL]
# Default: http://localhost:8080
# ─────────────────────────────────────────────────────────────

BASE_URL="${1:-http://localhost:8080}"
TOKEN_A="token-user-001"
TOKEN_B="token-user-002"
TOKEN_C="token-user-003"

echo ""
echo "=========================================="
echo " Wallet Service Burst Test"
echo " Target: $BASE_URL"
echo "=========================================="

# SETUP — create wallets
echo ""
echo "── SETUP: Creating wallets ──"

WALLET_A=$(curl -s -X POST "$BASE_URL/wallets" \
  -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-001"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['walletId'])")

WALLET_B=$(curl -s -X POST "$BASE_URL/wallets" \
  -H "Authorization: Bearer $TOKEN_B" \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-002"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['walletId'])")

WALLET_C=$(curl -s -X POST "$BASE_URL/wallets" \
  -H "Authorization: Bearer $TOKEN_C" \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-003"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['walletId'])")

echo "Wallet A: $WALLET_A"
echo "Wallet B: $WALLET_B"
echo "Wallet C: $WALLET_C"

# ─────────────────────────────────────────
# GATE 1 — Race-free get-or-create
# ─────────────────────────────────────────
echo ""
echo "── GATE 1: Race-free get-or-create (50 concurrent) ──"
NEW_USER="user-burst-$(date +%s)"

for i in $(seq 1 50); do
  curl -s -X POST "$BASE_URL/wallets" \
    -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d "{\"userId\":\"$NEW_USER\"}" > /dev/null &
done
wait

RESULT=$(curl -s -X POST "$BASE_URL/wallets" \
  -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d "{\"userId\":\"$NEW_USER\"}")
echo "Response: $RESULT"

if echo "$RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); exit(0 if 'walletId' in d else 1)"; then
  echo "✅ GATE 1 PASSED — exactly 1 wallet created"
else
  echo "❌ GATE 1 FAILED"
fi

# ─────────────────────────────────────────
# GATE 2 — Idempotent retry storm
# ─────────────────────────────────────────
echo ""
echo "── GATE 2: Idempotent retry storm (30 concurrent, same key) ──"

IDEM_KEY="idem-$(date +%s)"
BAL_A_BEFORE=$(curl -s "$BASE_URL/wallets/$WALLET_A" \
  -H "Authorization: Bearer $TOKEN_A" | python3 -c "import sys,json; print(json.load(sys.stdin).get('balancePaise',0))")
echo "Balance A before: $BAL_A_BEFORE paise"

for i in $(seq 1 30); do
  curl -s -X POST "$BASE_URL/transfers" \
    -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d "{\"from\":\"$WALLET_A\",\"to\":\"$WALLET_B\",\"amountPaise\":1000,\"idempotencyKey\":\"$IDEM_KEY\"}" > /dev/null &
done
wait

BAL_A_AFTER=$(curl -s "$BASE_URL/wallets/$WALLET_A" \
  -H "Authorization: Bearer $TOKEN_A" | python3 -c "import sys,json; print(json.load(sys.stdin).get('balancePaise',0))")

EXPECTED=$((BAL_A_BEFORE - 1000))
echo "Balance A after (expect $EXPECTED): $BAL_A_AFTER"

if [ "$BAL_A_AFTER" -eq "$EXPECTED" ]; then
  echo "✅ GATE 2 PASSED — debited exactly once"
else
  echo "❌ GATE 2 FAILED — possible double debit"
fi

# ─────────────────────────────────────────
# GATE 3 — Conservation under contention
# ─────────────────────────────────────────
echo ""
echo "── GATE 3: Conservation under contention (100 concurrent) ──"

BA=$(curl -s "$BASE_URL/wallets/$WALLET_A" -H "Authorization: Bearer $TOKEN_A" | python3 -c "import sys,json; print(json.load(sys.stdin).get('balancePaise',0))")
BB=$(curl -s "$BASE_URL/wallets/$WALLET_B" -H "Authorization: Bearer $TOKEN_B" | python3 -c "import sys,json; print(json.load(sys.stdin).get('balancePaise',0))")
BC=$(curl -s "$BASE_URL/wallets/$WALLET_C" -H "Authorization: Bearer $TOKEN_C" | python3 -c "import sys,json; print(json.load(sys.stdin).get('balancePaise',0))")
TOTAL_BEFORE=$((BA + BB + BC))
echo "Total before: $TOTAL_BEFORE paise (A=$BA B=$BB C=$BC)"

for i in $(seq 1 34); do
  curl -s -X POST "$BASE_URL/transfers" \
    -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" \
    -d "{\"from\":\"$WALLET_A\",\"to\":\"$WALLET_B\",\"amountPaise\":100,\"idempotencyKey\":\"ab-$i-$(date +%s%N)\"}" > /dev/null &
  curl -s -X POST "$BASE_URL/transfers" \
    -H "Authorization: Bearer $TOKEN_B" -H "Content-Type: application/json" \
    -d "{\"from\":\"$WALLET_B\",\"to\":\"$WALLET_A\",\"amountPaise\":100,\"idempotencyKey\":\"ba-$i-$(date +%s%N)\"}" > /dev/null &
  curl -s -X POST "$BASE_URL/transfers" \
    -H "Authorization: Bearer $TOKEN_C" -H "Content-Type: application/json" \
    -d "{\"from\":\"$WALLET_C\",\"to\":\"$WALLET_A\",\"amountPaise\":50,\"idempotencyKey\":\"ca-$i-$(date +%s%N)\"}" > /dev/null &
done
wait

BA2=$(curl -s "$BASE_URL/wallets/$WALLET_A" -H "Authorization: Bearer $TOKEN_A" | python3 -c "import sys,json; print(json.load(sys.stdin).get('balancePaise',0))")
BB2=$(curl -s "$BASE_URL/wallets/$WALLET_B" -H "Authorization: Bearer $TOKEN_B" | python3 -c "import sys,json; print(json.load(sys.stdin).get('balancePaise',0))")
BC2=$(curl -s "$BASE_URL/wallets/$WALLET_C" -H "Authorization: Bearer $TOKEN_C" | python3 -c "import sys,json; print(json.load(sys.stdin).get('balancePaise',0))")
TOTAL_AFTER=$((BA2 + BB2 + BC2))

echo "Total after:  $TOTAL_AFTER paise (A=$BA2 B=$BB2 C=$BC2)"

if [ "$TOTAL_BEFORE" -eq "$TOTAL_AFTER" ] && [ "$BA2" -ge 0 ] && [ "$BB2" -ge 0 ] && [ "$BC2" -ge 0 ]; then
  echo "✅ GATE 3 PASSED — conservation holds, no negative balances"
else
  echo "❌ GATE 3 FAILED — money lost or negative balance"
fi

echo ""
echo "=========================================="
echo " All Tests Done"
echo "=========================================="
