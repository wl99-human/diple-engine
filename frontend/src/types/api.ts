export type AccountType = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE';

export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';

export interface Account {
  id: string;
  ownerId: string;
  accountType: AccountType;
  currency: string;
  postedBalanceMinorUnits: number;
  pendingDebitsMinorUnits: number;
  availableBalanceMinorUnits: number;
  status: AccountStatus;
  createdAt: string;
}

export interface CreateAccountRequest {
  id: string;
  ownerId: string;
  accountType: AccountType;
  currency: string;
  initialBalanceMinorUnits: number;
}

export interface TransferRequest {
  senderAccountId: string;
  receiverAccountId: string;
  amountMinorUnits: number;
  currency: string;
  description: string;
}

export interface TransferResponse {
  transactionId: string;
  idempotencyKey: string;
  senderAccountId: string;
  receiverAccountId: string;
  amountMinorUnits: number;
  currency: string;
  senderBalanceAfter: number;
  receiverBalanceAfter: number;
  status: string;
  postedAt: string;
  isCachedReplay?: boolean;
}

export type HoldStatus = 'ACTIVE' | 'CAPTURED' | 'VOIDED' | 'EXPIRED';

export interface CreateHoldRequest {
  accountId: string;
  amountMinorUnits: number;
  currency: string;
  referenceId: string;
  description: string;
  holdDurationMinutes?: number;
}

export interface PaymentHold {
  id: string;
  idempotencyKey: string;
  accountId: string;
  amount: number;
  currency: string;
  status: HoldStatus;
  expiresAt: string;
  createdAt: string;
}

export interface CaptureHoldRequest {
  destinationAccountId: string;
  description: string;
}

export interface JournalPosting {
  id: number;
  transactionId: string;
  accountId: string;
  direction: 'DEBIT' | 'CREDIT';
  amount: number;
  currency: string;
  entrySequence: number;
  accountBalanceAfter: number;
  entryHash: string;
  createdAt: string;
}

export interface AccountAuditItem {
  accountId: string;
  verified: boolean;
  calculatedBalance: number;
  actualBalance: number;
  merkleHash: string;
  message?: string;
}

export interface SystemReconciliationReport {
  status: 'HEALTHY_100_PERCENT_CONSERVED' | 'INVARIANT_VIOLATION' | string;
  totalAccountsAudited: number;
  auditedAt: string;
  accountAudits?: AccountAuditItem[];
}

export interface ChaosRunRequest {
  totalTransfers: number;
  concurrencyLevel: number;
  duplicateRatePercent: number;
  useHotMerchant: boolean;
}

export interface ChaosRunResult {
  totalRequests: number;
  successfulTransfers: number;
  duplicateReplays: number;
  totalDurationMs: number;
  requestsPerSecond: number;
  p50LatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  verificationSummary: string;
}

export interface ApiError {
  status: number;
  error: string;
  message: string;
  timestamp?: string;
}
