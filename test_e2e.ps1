$ErrorActionPreference = 'Stop'

$backendDir = Join-Path $PSScriptRoot "backend"

Write-Host "=== 1. Testing GET /v1/accounts ==="
$accounts = Invoke-RestMethod -Uri "http://localhost:8080/v1/accounts" -Method Get
Write-Host "Accounts count: $($accounts.Count)"

Write-Host "`n=== 2. Testing Fresh POST /v1/payments/transfers ==="
$key = "live_key_" + [System.Guid]::NewGuid().ToString()
$body = @{
    senderAccountId = "acc_alice_01"
    receiverAccountId = "merchant_google_store"
    amountMinorUnits = 5000
    currency = "USD"
    description = "Integration Test Transfer"
} | ConvertTo-Json

$res1 = Invoke-RestMethod -Uri "http://localhost:8080/v1/payments/transfers" -Method Post -Headers @{ "X-Idempotency-Key" = $key; "Content-Type" = "application/json" } -Body $body
Write-Host "Transfer 1 -> Tx: $($res1.transactionId), Status: $($res1.status), isCachedReplay: $($res1.isCachedReplay)"

Write-Host "`n=== 3. Testing Idempotent Replay (Same Key & Payload) ==="
$res2 = Invoke-RestMethod -Uri "http://localhost:8080/v1/payments/transfers" -Method Post -Headers @{ "X-Idempotency-Key" = $key; "Content-Type" = "application/json" } -Body $body
Write-Host "Transfer 2 (Replay) -> Tx: $($res2.transactionId), Status: $($res2.status), isCachedReplay: $($res2.isCachedReplay)"

Write-Host "`n=== 4. Testing Payload Tampering (Same Key, Modified Payload) ==="
$tampered = @{
    senderAccountId = "acc_alice_01"
    receiverAccountId = "merchant_google_store"
    amountMinorUnits = 999999
    currency = "USD"
    description = "Tampered Payload"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "http://localhost:8080/v1/payments/transfers" -Method Post -Headers @{ "X-Idempotency-Key" = $key; "Content-Type" = "application/json" } -Body $tampered
    Write-Host "ERROR: Tampering was not blocked!"
} catch {
    Write-Host "SUCCESS: Tampered request correctly rejected with 422 Unprocessable Entity."
}

Write-Host "`n=== 5. Testing Two-Phase Payment Hold & Capture ==="
$holdKey = "hold_key_" + [System.Guid]::NewGuid().ToString()
$holdBody = @{
    accountId = "acc_bob_02"
    amountMinorUnits = 10000
    currency = "USD"
    description = "Pre-authorization hold"
    holdDurationMinutes = 15
} | ConvertTo-Json

$hold = Invoke-RestMethod -Uri "http://localhost:8080/v1/payments/holds" -Method Post -Headers @{ "X-Idempotency-Key" = $holdKey; "Content-Type" = "application/json" } -Body $holdBody
Write-Host "Hold Created -> ID: $($hold.holdId), Status: $($hold.status)"

$capBody = @{ destinationAccountId = "merchant_google_store"; description = "Capture checkout" } | ConvertTo-Json
$capture = Invoke-RestMethod -Uri "http://localhost:8080/v1/payments/holds/$($hold.holdId)/capture" -Method Post -Headers @{ "Content-Type" = "application/json" } -Body $capBody
Write-Host "Hold Captured -> Tx: $($capture.id), Status: $($capture.status)"

Write-Host "`n=== 6. Testing Concurrency Chaos Benchmark ==="
$chaosReq = @{ totalTransfers = 50; concurrencyLevel = 10; duplicateRatePercent = 25; useHotMerchant = $true } | ConvertTo-Json
$chaos = Invoke-RestMethod -Uri "http://localhost:8080/v1/chaos/run" -Method Post -Headers @{ "Content-Type" = "application/json" } -Body $chaosReq
Write-Host "Chaos Benchmark: $($chaos.verificationSummary)"
Write-Host "Throughput: $($chaos.requestsPerSecond) RPS | p50: $($chaos.p50LatencyMs)ms | p95: $($chaos.p95LatencyMs)ms | p99: $($chaos.p99LatencyMs)ms"

Write-Host "`n=== 7. Verifying Double-Entry Reconciliation & Merkle Integrity ==="
$audit = Invoke-RestMethod -Uri "http://localhost:8080/v1/reconciliation/status" -Method Get
Write-Host "Reconciliation Status: $($audit.status)"
Write-Host "Double-Entry Balance Conserved: $($audit.isDoubleEntryConserved)"
Write-Host "SHA-256 Merkle Hash Chain Intact: $($audit.isMerkleChainIntact)"
Write-Host "Total Postings Audited: $($audit.totalPostingsAudited)"
