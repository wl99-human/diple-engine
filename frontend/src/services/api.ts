import {
  Account,
  CreateAccountRequest,
  TransferRequest,
  TransferResponse,
  PaymentHold,
  CreateHoldRequest,
  CaptureHoldRequest,
  JournalPosting,
  SystemReconciliationReport,
  ChaosRunRequest,
  ChaosRunResult,
} from '../types/api';

const BASE_URL = '/v1';

export class ApiServiceError extends Error {
  status: number;
  data: any;

  constructor(message: string, status: number, data?: any) {
    super(message);
    this.name = 'ApiServiceError';
    this.status = status;
    this.data = data;
  }
}

async function handleResponse<T>(res: Response): Promise<{ data: T; status: number }> {
  let body: any = null;
  const contentType = res.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    body = await res.json().catch(() => null);
  } else {
    body = await res.text().catch(() => null);
  }

  if (!res.ok) {
    const errorMsg =
      (body && (body.message || body.error || body.detail)) ||
      `Request failed with status ${res.status}`;
    throw new ApiServiceError(errorMsg, res.status, body);
  }

  return { data: body as T, status: res.status };
}

export const api = {
  // Accounts
  async getAccounts(): Promise<Account[]> {
    const res = await fetch(`${BASE_URL}/accounts`);
    const { data } = await handleResponse<Account[]>(res);
    return data;
  },

  async getAccount(id: string): Promise<Account> {
    const res = await fetch(`${BASE_URL}/accounts/${encodeURIComponent(id)}`);
    const { data } = await handleResponse<Account>(res);
    return data;
  },

  async createAccount(req: CreateAccountRequest): Promise<Account> {
    const res = await fetch(`${BASE_URL}/accounts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
    });
    const { data } = await handleResponse<Account>(res);
    return data;
  },

  async seedAccounts(): Promise<{ message: string; accountsCount?: number }> {
    const res = await fetch(`${BASE_URL}/chaos/seed-accounts`, {
      method: 'POST',
    });
    const { data } = await handleResponse<any>(res);
    return data;
  },

  // Transfers & Idempotency
  async executeTransfer(
    payload: TransferRequest,
    idempotencyKey: string,
    clientId: string = 'react-web-dashboard'
  ): Promise<{ data: TransferResponse; status: number }> {
    const res = await fetch(`${BASE_URL}/payments/transfers`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Idempotency-Key': idempotencyKey,
        'X-Client-Id': clientId,
      },
      body: JSON.stringify(payload),
    });
    return handleResponse<TransferResponse>(res);
  },

  // Two-Phase Holds
  async createHold(
    payload: CreateHoldRequest,
    idempotencyKey?: string
  ): Promise<PaymentHold> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (idempotencyKey) {
      headers['X-Idempotency-Key'] = idempotencyKey;
    }
    const res = await fetch(`${BASE_URL}/payments/holds`, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
    });
    const { data } = await handleResponse<PaymentHold>(res);
    return data;
  },

  async captureHold(
    holdId: string,
    payload: CaptureHoldRequest,
    idempotencyKey?: string
  ): Promise<any> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (idempotencyKey) {
      headers['X-Idempotency-Key'] = idempotencyKey;
    }
    const res = await fetch(`${BASE_URL}/payments/holds/${encodeURIComponent(holdId)}/capture`, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
    });
    const { data } = await handleResponse<any>(res);
    return data;
  },

  async voidHold(holdId: string, reason?: string): Promise<PaymentHold> {
    const url = reason
      ? `${BASE_URL}/payments/holds/${encodeURIComponent(holdId)}/void?reason=${encodeURIComponent(reason)}`
      : `${BASE_URL}/payments/holds/${encodeURIComponent(holdId)}/void`;
    const res = await fetch(url, {
      method: 'POST',
    });
    const { data } = await handleResponse<PaymentHold>(res);
    return data;
  },

  // Ledger Postings & Reconciliation
  async getRecentPostings(): Promise<JournalPosting[]> {
    const res = await fetch(`${BASE_URL}/payments/postings/recent`);
    const { data } = await handleResponse<JournalPosting[]>(res);
    return data;
  },

  async runReconciliation(): Promise<SystemReconciliationReport> {
    const res = await fetch(`${BASE_URL}/reconciliation/run`, {
      method: 'POST',
    });
    const { data } = await handleResponse<SystemReconciliationReport>(res);
    return data;
  },

  // Chaos Stress Benchmark
  async runChaos(params: ChaosRunRequest): Promise<ChaosRunResult> {
    const res = await fetch(`${BASE_URL}/chaos/run`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(params),
    });
    const { data } = await handleResponse<ChaosRunResult>(res);
    return data;
  },
};
