# Distributed Idempotent Payment & Ledger Engine (DIPLE)

---

## 1. Overview & Key Capabilities

**DIPLE** is a high-throughput, fault-tolerant financial transaction processor engineered in Java 17+ / Spring Boot 3.3+ with Virtual Threads and Gradle. It delivers:

1. **Exactly-Once Ingestion:** SHA-256 payload hashing, distributed mutex locks, and durable PostgreSQL/H2 state machines (`409 Conflict`, `422 Unprocessable Entity`, `200/201 Cached Replay`).
2. **Deterministic Pessimistic Locking:** Global lexicographical sorting of account IDs before acquiring `@Lock(LockModeType.PESSIMISTIC_WRITE)` locks, completely eliminating distributed deadlocks under concurrent bidirectional transfers.
3. **Double-Entry Balance Invariants:** Real-time balance conservation (Sum of Debits == Sum of Credits) and availability invariants (`availableBalance = postedBalance - pendingDebits >= 0`).
4. **Two-Phase Payment Holds:** 2-phase authorization holds against available balances, partial/full captures, and void releases with TTL expiration.
5. **Transactional Outbox & DLQ:** Atomic event staging in ACID database transactions, poller with Full Decorrelated Jitter, consumer deduplication, and automated Dead Letter Queue (DLQ) routing.
6. **Cryptographic SHA-256 Merkle Audit Trail:** Recursive chaining detecting unauthorized database row modifications or deletions.
7. **HMAC Signature Validation & Rate Limiting:** Request authenticity verification and token-bucket rate limiting.
8. **Prometheus Metrics & Telemetry:** Micrometer-backed latency histograms, throughput counters, and Prometheus-compatible `/actuator/prometheus` endpoint.
9. **Interactive Glassmorphic React UI:** Live balance matrix, transfer simulator with payload tampering toggle, two-phase payment holds console, 1-click chaos benchmark runner, and streaming Merkle ledger inspector.

---

## 2. Tech Stack

| Layer      | Technology                                                     |
|------------|----------------------------------------------------------------|
| Backend    | Java 17+, Spring Boot 3.3.3, Spring Data JPA, Hibernate       |
| Database   | H2 (PostgreSQL compatibility mode) — swap for PostgreSQL in production |
| Build      | Gradle 8.10.2 with Gradle Wrapper                              |
| Frontend   | React 18, TypeScript, Vite 5, Radix UI, Lucide Icons           |
| Metrics    | Spring Boot Actuator, Micrometer, Prometheus                   |
| Testing    | JUnit 5, AssertJ, Mockito, PowerShell E2E scripts              |

---

## 3. Directory Structure

```
diple-engine/
├── README.md                                # This file
├── .gitignore                               # Project-wide gitignore (IDE, OS)
├── test_e2e.ps1                             # End-to-end integration & chaos test script
│
├── backend/                                 # ── Backend (Java / Spring Boot / Gradle) ──
│   ├── build.gradle                         # Spring Boot 3.3.3 & Gradle dependencies
│   ├── settings.gradle                      # Root project settings
│   ├── gradlew / gradlew.bat               # Gradle Wrapper (Unix / Windows)
│   ├── setup_gradle.ps1                     # Portable Gradle toolchain downloader
│   ├── .gitignore                           # Backend gitignore (build/, .gradle/, tools/)
│   ├── gradle/                              # Gradle wrapper JAR & properties
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/google/payments/diple/
│   │   │   │   ├── DipleApplication.java        # Application entrypoint & genesis balance funder
│   │   │   │   ├── api/                         # REST Controllers
│   │   │   │   │   ├── AccountController.java
│   │   │   │   │   ├── PaymentController.java
│   │   │   │   │   ├── ChaosBenchmarkController.java
│   │   │   │   │   ├── ReconciliationController.java
│   │   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   │   └── dto/                     # Request/Response DTOs
│   │   │   │   │       ├── AccountDtos.java
│   │   │   │   │       ├── ChaosDtos.java
│   │   │   │   │       └── PaymentDtos.java
│   │   │   │   ├── common/                      # Shared Value Objects, Enums & Exceptions
│   │   │   │   │   ├── config/
│   │   │   │   │   │   └── WebCorsConfig.java
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   ├── Money.java
│   │   │   │   │   │   ├── AccountType.java
│   │   │   │   │   │   ├── AccountStatus.java
│   │   │   │   │   │   ├── HoldStatus.java
│   │   │   │   │   │   ├── IdempotencyStatus.java
│   │   │   │   │   │   ├── PostingDirection.java
│   │   │   │   │   │   └── TransactionStatus.java
│   │   │   │   │   └── exception/               # Domain-specific exceptions
│   │   │   │   │       ├── AccountFrozenException.java
│   │   │   │   │       ├── AccountNotFoundException.java
│   │   │   │   │       ├── CurrencyMismatchException.java
│   │   │   │   │       ├── IdempotencyConflictException.java
│   │   │   │   │       ├── IdempotencyPayloadMismatchException.java
│   │   │   │   │       ├── InsufficientFundsException.java
│   │   │   │   │       ├── InvalidHoldException.java
│   │   │   │   │       ├── InvalidTransferException.java
│   │   │   │   │       └── LedgerInvariantViolationException.java
│   │   │   │   ├── domain/                      # JPA Entities
│   │   │   │   │   ├── Account.java
│   │   │   │   │   ├── IdempotencyKeyRecord.java
│   │   │   │   │   ├── JournalPosting.java
│   │   │   │   │   ├── OutboxEvent.java
│   │   │   │   │   ├── PaymentHold.java
│   │   │   │   │   └── TransactionRecord.java
│   │   │   │   ├── idempotency/                 # SHA-256 Idempotency Engine & Distributed Mutex
│   │   │   │   │   ├── IdempotencyService.java
│   │   │   │   │   ├── CachedResponse.java
│   │   │   │   │   ├── DistributedLockManager.java
│   │   │   │   │   └── InMemoryDistributedLockManager.java
│   │   │   │   ├── ledger/                      # Deadlock-Free Transfer Engine, Holds & Merkle
│   │   │   │   │   ├── LedgerTransferService.java
│   │   │   │   │   ├── PaymentHoldService.java
│   │   │   │   │   └── LedgerReconciliationService.java
│   │   │   │   ├── outbox/                      # Transactional Outbox, Relay & DLQ
│   │   │   │   │   ├── OutboxEventService.java
│   │   │   │   │   ├── OutboxRelayWorker.java
│   │   │   │   │   ├── EventBroker.java
│   │   │   │   │   ├── InMemoryEventBroker.java
│   │   │   │   │   └── WalletNotificationConsumer.java
│   │   │   │   ├── repository/                  # Spring Data JPA Repositories (Pessimistic Locking)
│   │   │   │   │   ├── AccountRepository.java
│   │   │   │   │   ├── IdempotencyKeyRepository.java
│   │   │   │   │   ├── JournalPostingRepository.java
│   │   │   │   │   ├── OutboxEventRepository.java
│   │   │   │   │   ├── PaymentHoldRepository.java
│   │   │   │   │   └── TransactionRepository.java
│   │   │   │   ├── security/                    # Request Auth & Rate Limiting
│   │   │   │   │   ├── HmacSignatureValidator.java
│   │   │   │   │   └── TokenBucketRateLimiter.java
│   │   │   │   └── telemetry/                   # Observability & Metrics
│   │   │   │       └── MetricsService.java
│   │   │   └── resources/
│   │   │       ├── application.yml              # Configuration (Virtual Threads, H2, Actuator)
│   │   │       └── static/                      # Frontend production build output
│   │   └── test/
│   │       └── java/com/google/payments/diple/
│   │           ├── MoneyValueObjectTest.java
│   │           ├── IdempotencyEngineTest.java
│   │           ├── LedgerInvariantsTest.java
│   │           ├── PessimisticLockDeadlockTest.java
│   │           └── OutboxDlqResilienceTest.java
│   └── tools/                                   # Bundled Gradle download (gitignored)
│
└── frontend/                                # ── Frontend (React / TypeScript / Vite) ──
    ├── package.json                         # Dependencies & scripts
    ├── vite.config.ts                       # Vite config (dev proxy → :8080, prod → backend/static/)
    ├── tsconfig.json                        # Root TypeScript config
    ├── tsconfig.app.json                    # App TypeScript config
    ├── tsconfig.node.json                   # Node TypeScript config
    ├── eslint.config.js                     # ESLint configuration
    ├── index.html                           # HTML entry point
    ├── .gitignore                           # Frontend gitignore
    └── src/
        ├── main.tsx                         # React entry point
        ├── App.tsx                          # Root application component & tab navigation
        ├── App.css                          # App-specific styles
        ├── index.css                        # Global styles & design tokens
        ├── vite-env.d.ts                    # Vite type declarations
        ├── components/                      # UI Components
        │   ├── Header.tsx                   # App header & branding
        │   ├── AccountsMatrix.tsx           # Live account balance matrix
        │   ├── TransferConsole.tsx           # Transfer simulator with tampering toggle
        │   ├── PaymentHoldsConsole.tsx       # Two-phase payment holds manager
        │   ├── ChaosBenchmark.tsx           # 1-click concurrency chaos runner
        │   ├── MerkleLedgerStream.tsx        # Streaming Merkle audit trail inspector
        │   └── ToastContainer.tsx           # Notification toast system
        ├── services/
        │   └── api.ts                       # HTTP client & API service layer
        ├── types/
        │   └── api.ts                       # TypeScript type definitions
        └── assets/                          # Static assets (images, icons)
```

---

## 4. Getting Started

### Prerequisites

- **Java 17+** (JDK)
- **Node.js 18+** and **npm** (for the frontend)
- Gradle is bundled via the Gradle Wrapper — no separate install needed

### 4.1 Backend — Run Automated Tests (JUnit 5)

```powershell
cd D:\Projects\diple-engine\backend
.\gradlew.bat test
```

### 4.2 Backend — Run the Application

```powershell
cd D:\Projects\diple-engine\backend
.\gradlew.bat bootRun
```

The backend starts on **http://localhost:8080**.

### 4.3 Frontend — Install Dependencies & Dev Server

```powershell
cd D:\Projects\diple-engine\frontend
npm install
npm run dev
```

The Vite dev server starts on **http://localhost:5173** and proxies `/v1/*` API requests to the backend at `:8080`.

### 4.4 Frontend — Production Build

```powershell
cd D:\Projects\diple-engine\frontend
npm run build
```

This compiles the React app into `backend/src/main/resources/static/`, making it served directly by Spring Boot at **http://localhost:8080**.

### 4.5 End-to-End Integration & Chaos Verification

With the backend running, in a separate terminal:

```powershell
cd D:\Projects\diple-engine
powershell -ExecutionPolicy Bypass -File .\test_e2e.ps1
```

---

## 5. API Endpoints

| Method | Endpoint                                | Description                              |
|--------|-----------------------------------------|------------------------------------------|
| GET    | `/v1/accounts`                          | List all accounts with balances          |
| POST   | `/v1/payments/transfers`                | Execute a ledger transfer (idempotent)   |
| POST   | `/v1/payments/holds`                    | Create a two-phase payment hold          |
| POST   | `/v1/payments/holds/{id}/capture`       | Capture (settle) a payment hold          |
| POST   | `/v1/payments/holds/{id}/void`          | Void (release) a payment hold            |
| POST   | `/v1/chaos/run`                         | Run concurrency chaos benchmark          |
| GET    | `/v1/reconciliation/status`             | Double-entry & Merkle integrity audit    |
| GET    | `/actuator/prometheus`                  | Prometheus metrics scrape endpoint       |

All mutation endpoints require the `X-Idempotency-Key` header for exactly-once semantics.

---

## 6. Architecture Highlights

```
┌─────────────────────────────────────────────────────────────┐
│                     React Frontend (Vite)                    │
│   AccountsMatrix │ TransferConsole │ ChaosBenchmark │ Merkle │
└────────────────────────────┬────────────────────────────────┘
                             │  HTTP /v1/*
┌────────────────────────────▼────────────────────────────────┐
│                   Spring Boot REST API                       │
│  ┌─────────────┐  ┌──────────────────┐  ┌────────────────┐  │
│  │ Idempotency │  │ Ledger Transfer  │  │ Payment Holds  │  │
│  │   Engine    │  │    Service       │  │   Service      │  │
│  │  (SHA-256)  │  │ (Pessimistic     │  │ (2-Phase Auth) │  │
│  │             │  │  Lock Ordering)  │  │                │  │
│  └──────┬──────┘  └───────┬──────────┘  └───────┬────────┘  │
│         │                 │                     │            │
│  ┌──────▼─────────────────▼─────────────────────▼────────┐  │
│  │              Spring Data JPA / Hibernate               │  │
│  │         (Pessimistic Write Locks, Merkle Chain)        │  │
│  └───────────────────────┬───────────────────────────────┘  │
│  ┌───────────────────────▼───────────────────────────────┐  │
│  │        Transactional Outbox → Relay → DLQ             │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │    Security (HMAC) │ Rate Limiter │ Metrics/Telemetry │  │
│  └───────────────────────────────────────────────────────┘  │
└────────────────────────────┬────────────────────────────────┘
                             │
                    ┌────────▼────────┐
                    │  H2 / PostgreSQL │
                    └─────────────────┘
```

---

## 7. License

This project is for demonstration and educational purposes.
