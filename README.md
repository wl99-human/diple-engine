# Distributed Idempotent Payment & Ledger Engine (DIPLE)
### Production-Grade Architecture for FAANG SWE II (Google Consumer Payments / Wallet Track)

---

## 1. Overview & Key Capabilities

**DIPLE** is a high-throughput, fault-tolerant financial transaction processor engineered in Java 17+ / Spring Boot 3.3+ with Virtual Threads. It delivers:

1. **Exactly-Once Ingestion:** SHA-256 payload hashing, distributed mutex locks, and durable PostgreSQL/H2 state machines (`409 Conflict`, `422 Unprocessable Entity`, `200/201 Cached Replay`).
2. **Deterministic Pessimistic Locking:** Global lexicographical sorting of account IDs before acquiring `@Lock(LockModeType.PESSIMISTIC_WRITE)` locks, completely eliminating distributed deadlocks under concurrent bidirectional transfers.
3. **Double-Entry Balance Invariants:** Real-time balance conservation (Sum of Debits == Sum of Credits) and availability invariants (`availableBalance = postedBalance - pendingDebits >= 0`).
4. **Two-Phase Payment Holds:** 2-phase authorization holds against available balances, partial/full captures, and void releases with TTL expiration.
5. **Transactional Outbox & DLQ:** Atomic event staging in ACID database transactions, poller with Full Decorrelated Jitter, consumer deduplication, and automated Dead Letter Queue (DLQ) routing.
6. **Cryptographic SHA-256 Merkle Audit Trail:** Recursive chaining detecting unauthorized database row modifications or deletions.
7. **Interactive Glassmorphic UI:** Live balance matrix, transfer simulator with payload tampering toggle, 1-click chaos benchmark runner, and streaming Merkle ledger inspector.

---

## 2. Directory Structure

```
D:\Projects\diple-engine/
├── mvnw.cmd                              # Maven Wrapper for 1-command builds
├── pom.xml                               # Spring Boot 3.3.3 & Maven Dependencies
├── setup_maven.ps1                       # Portable Maven downloader
├── test_e2e.ps1                          # End-to-end integration & chaos test script
├── tools/
│   └── apache-maven-3.9.6/               # Bundled Apache Maven toolchain
└── src/
    ├── main/
    │   ├── java/com/google/payments/diple/
    │   │   ├── DipleApplication.java     # Application entrypoint & genesis balance funder
    │   │   ├── api/                      # REST Controllers (Payments, Accounts, Reconciliation, Chaos)
    │   │   ├── common/                   # Money Value Object, GAAP AccountType, Enums, Exceptions
    │   │   ├── domain/                   # JPA Entities (Account, JournalPosting, PaymentHold, OutboxEvent, IdempotencyKeyRecord)
    │   │   ├── idempotency/              # SHA-256 Idempotency Engine & Distributed Mutex Lock
    │   │   ├── ledger/                   # Deadlock-Free Transfer Engine, Holds Service, Merkle Reconciliation
    │   │   ├── outbox/                   # Outbox Relay Poller, Event Broker, Resilient Wallet Consumer
    │   │   └── repository/               # Pessimistic Locking Repositories
    │   └── resources/
    │       ├── application.yml           # Configuration (Virtual Threads, H2 PostgreSQL mode, Actuator)
    │       └── static/                   # Glassmorphic Web UI (index.html, app.css, app.js)
    └── test/
        └── java/com/google/payments/diple/
            ├── MoneyValueObjectTest.java         # Integer minorUnits math & overflow tests
            ├── IdempotencyEngineTest.java        # State machine & collision tests
            ├── LedgerInvariantsTest.java         # Balance conservation & 2-phase hold tests
            ├── PessimisticLockDeadlockTest.java  # High-concurrency circular transfer deadlock tests
            └── OutboxDlqResilienceTest.java      # Outbox staging, relay, deduplication & DLQ tests
```

---

## 3. Getting Started

### 3.1 Run Automated Tests (JUnit 5)
```powershell
cd D:\Projects\diple-engine
.\mvnw.cmd test
```

### 3.2 Run the Application Locally
```powershell
cd D:\Projects\diple-engine
.\mvnw.cmd spring-boot:run
```

### 3.3 Run End-to-End Integration & Chaos Verification
In a separate terminal:
```powershell
cd D:\Projects\diple-engine
powershell -ExecutionPolicy Bypass -File .\test_e2e.ps1
```

### 3.4 Open the Interactive Web Dashboard
Open http://localhost:8080 in your web browser.
