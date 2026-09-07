# DIPLE Frontend

Interactive glassmorphic dashboard for the Distributed Idempotent Payment & Ledger Engine.

---

## Tech Stack

| Technology      | Purpose                                 |
|-----------------|-----------------------------------------|
| React 18        | UI framework                            |
| TypeScript      | Type-safe application logic             |
| Vite 5          | Dev server with HMR & production builds |
| Radix UI        | Accessible dialog & tooltip primitives  |
| Lucide React    | Icon library                            |
| Sonner          | Toast notifications                     |

---

## Project Structure

```
frontend/
├── index.html                   # HTML entry point
├── package.json                 # Dependencies & scripts
├── vite.config.ts               # Vite config (proxy & build output)
├── tsconfig.json                # Root TypeScript config
├── tsconfig.app.json            # App TypeScript config
├── tsconfig.node.json           # Node TypeScript config
├── eslint.config.js             # ESLint configuration
└── src/
    ├── main.tsx                 # React entry point
    ├── App.tsx                  # Root component & tab navigation
    ├── App.css                  # App-specific styles
    ├── index.css                # Global styles & design tokens
    ├── vite-env.d.ts            # Vite type declarations
    ├── components/
    │   ├── Header.tsx           # App header & branding
    │   ├── AccountsMatrix.tsx   # Live account balance matrix grid
    │   ├── TransferConsole.tsx   # Transfer simulator with payload tampering toggle
    │   ├── PaymentHoldsConsole.tsx # Two-phase payment holds (auth/capture/void)
    │   ├── ChaosBenchmark.tsx   # 1-click concurrency chaos benchmark runner
    │   ├── MerkleLedgerStream.tsx # Streaming Merkle SHA-256 audit trail inspector
    │   └── ToastContainer.tsx   # Notification toast system
    ├── services/
    │   └── api.ts               # HTTP client & API service layer
    ├── types/
    │   └── api.ts               # Shared TypeScript type definitions
    └── assets/                  # Static assets (images, icons)
```

---

## Getting Started

### Prerequisites

- **Node.js 18+** and **npm**
- Backend running on `http://localhost:8080` (see [root README](../README.md))

### Install Dependencies

```bash
npm install
```

### Development Server

```bash
npm run dev
```

Starts the Vite dev server at **http://localhost:5173**. All `/v1/*` API requests are proxied to the backend at `http://localhost:8080`.

### Production Build

```bash
npm run build
```

Compiles and outputs the optimized bundle to `../backend/src/main/resources/static/`, so the Spring Boot backend serves the frontend directly at **http://localhost:8080**.

### Lint

```bash
npm run lint
```

---

## Components

| Component             | Description                                                                 |
|-----------------------|-----------------------------------------------------------------------------|
| `Header`              | Application header with branding and navigation                             |
| `AccountsMatrix`      | Real-time grid displaying all accounts with posted/pending/available balances|
| `TransferConsole`     | Form to execute ledger transfers with optional payload tampering toggle      |
| `PaymentHoldsConsole` | Manager for two-phase payment holds: create, capture, and void operations    |
| `ChaosBenchmark`      | One-click runner for high-concurrency chaos benchmarks with latency metrics  |
| `MerkleLedgerStream`  | Live streaming inspector for the SHA-256 Merkle audit chain                  |
| `ToastContainer`      | Toast notification system for success/error feedback                         |

---

## API Proxy

In development, Vite proxies API requests to the backend:

```typescript
// vite.config.ts
server: {
  port: 5173,
  proxy: {
    '/v1': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
}
```

In production, the built frontend is served by Spring Boot directly — no proxy needed.
