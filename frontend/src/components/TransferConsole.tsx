import React, { useState } from 'react';
import { Account, TransferResponse } from '../types/api';
import { api, ApiServiceError } from '../services/api';
import { Send, Repeat, AlertOctagon, CheckCircle, RefreshCw, Zap } from 'lucide-react';

interface TransferConsoleProps {
  accounts: Account[];
  selectedSender: string;
  selectedReceiver: string;
  onSenderChange: (id: string) => void;
  onReceiverChange: (id: string) => void;
  onTransferSuccess: () => void;
  onShowToast: (msg: string, type: 'success' | 'error' | 'warning' | 'info') => void;
}

function generateUuid(): string {
  return 'key_' + 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export const TransferConsole: React.FC<TransferConsoleProps> = ({
  accounts,
  selectedSender,
  selectedReceiver,
  onSenderChange,
  onReceiverChange,
  onTransferSuccess,
  onShowToast,
}) => {
  const [amount, setAmount] = useState('25.00');
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => generateUuid());
  const [isTampering, setIsTampering] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [lastResult, setLastResult] = useState<{
    status: number;
    data?: TransferResponse;
    errorMsg?: string;
    isReplay?: boolean;
  } | null>(null);

  const handleRegenerateKey = () => {
    const newKey = generateUuid();
    setIdempotencyKey(newKey);
    onShowToast('New UUID idempotency key generated', 'info');
  };

  const handleExecute = async (overrideTamper?: boolean) => {
    if (!selectedSender || !selectedReceiver) {
      onShowToast('Please select both sender and receiver accounts', 'warning');
      return;
    }

    if (selectedSender === selectedReceiver) {
      onShowToast('Sender and receiver accounts must be distinct', 'warning');
      return;
    }

    const numAmount = parseFloat(amount);
    if (isNaN(numAmount) || numAmount <= 0) {
      onShowToast('Enter a valid transfer amount', 'warning');
      return;
    }

    let minorUnits = Math.round(numAmount * 100);
    const tamper = overrideTamper !== undefined ? overrideTamper : isTampering;
    if (tamper) {
      minorUnits += 500; // Alter payload by $5.00 to force SHA-256 hash collision
    }

    try {
      setIsSubmitting(true);
      const { data, status } = await api.executeTransfer(
        {
          senderAccountId: selectedSender,
          receiverAccountId: selectedReceiver,
          amountMinorUnits: minorUnits,
          currency: 'USD',
          description: tamper ? 'Tampered Test Transfer' : 'Console Web Transfer',
        },
        idempotencyKey
      );

      const isReplay = status === 200 || !!data.isCachedReplay;
      setLastResult({
        status,
        data,
        isReplay,
      });

      if (status === 201) {
        onShowToast(
          `Transfer settled! Tx: ${data.transactionId.substring(0, 10)}... (Fresh settlement)`,
          'success'
        );
        onTransferSuccess();
      } else if (isReplay) {
        onShowToast(
          `[HTTP 200 IDEMPOTENT REPLAY] Safe cached response returned! 0 duplicate debits.`,
          'info'
        );
      }
    } catch (err: any) {
      if (err instanceof ApiServiceError) {
        setLastResult({
          status: err.status,
          errorMsg: err.message,
        });

        if (err.status === 422) {
          onShowToast(`[HTTP 422 UNPROCESSABLE] Payload mutated under existing key!`, 'error');
        } else if (err.status === 409) {
          onShowToast(`[HTTP 409 CONFLICT] Active in-flight transfer mutex lease!`, 'warning');
        } else {
          onShowToast(`Transfer failed (${err.status}): ${err.message}`, 'error');
        }
      } else {
        onShowToast(`Network error: ${err.message}`, 'error');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const addAmount = (addVal: number) => {
    const current = parseFloat(amount) || 0;
    setAmount((current + addVal).toFixed(2));
  };

  return (
    <div className="glass-panel">
      <div className="glass-panel-header">
        <div className="glass-panel-title">
          <Zap size={18} color="var(--accent-primary)" />
          <span>Atomic Idempotent Transfer Console</span>
        </div>
        <div className="status-pill cyan">
          <span>SHA-256 Payload Guarded</span>
        </div>
      </div>

      <div className="glass-panel-body">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleExecute();
          }}
        >
          {/* Account Selectors */}
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">From Account (Debit)</label>
              <select
                className="form-select mono"
                value={selectedSender}
                onChange={(e) => onSenderChange(e.target.value)}
                required
              >
                <option value="" disabled>
                  Select sender account...
                </option>
                {accounts.map((acc) => (
                  <option key={acc.id} value={acc.id}>
                    {acc.id} (${(acc.availableBalanceMinorUnits / 100).toFixed(2)})
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">To Account (Credit)</label>
              <select
                className="form-select mono"
                value={selectedReceiver}
                onChange={(e) => onReceiverChange(e.target.value)}
                required
              >
                <option value="" disabled>
                  Select destination account...
                </option>
                {accounts.map((acc) => (
                  <option key={acc.id} value={acc.id}>
                    {acc.id} (${(acc.availableBalanceMinorUnits / 100).toFixed(2)})
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Amount and Currency */}
          <div className="form-row">
            <div className="form-group">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                <label className="form-label" style={{ marginBottom: 0 }}>
                  Amount ($ USD)
                </label>
                <div style={{ display: 'flex', gap: 4 }}>
                  {[10, 50, 100].map((v) => (
                    <button
                      key={v}
                      type="button"
                      className="btn btn-secondary btn-sm"
                      style={{ padding: '2px 6px', fontSize: '0.65rem' }}
                      onClick={() => addAmount(v)}
                    >
                      +${v}
                    </button>
                  ))}
                </div>
              </div>
              <input
                type="number"
                step="0.01"
                min="0.01"
                required
                className="form-input mono"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </div>

            <div className="form-group">
              <label className="form-label">Currency</label>
              <input
                type="text"
                value="USD"
                readOnly
                className="form-input mono"
                style={{ opacity: 0.8, cursor: 'not-allowed' }}
              />
            </div>
          </div>

          {/* Idempotency Key */}
          <div className="form-group">
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: 6,
              }}
            >
              <label className="form-label" style={{ marginBottom: 0 }}>
                X-Idempotency-Key
              </label>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                onClick={handleRegenerateKey}
                style={{ padding: '2px 8px', fontSize: '0.7rem' }}
              >
                <RefreshCw size={12} />
                Regenerate UUID
              </button>
            </div>
            <input
              type="text"
              required
              className="form-input mono"
              value={idempotencyKey}
              onChange={(e) => setIdempotencyKey(e.target.value)}
            />
          </div>

          {/* Tamper Toggle Checkbox */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '10px 14px',
              borderRadius: 'var(--radius-md)',
              background: 'rgba(245, 158, 11, 0.08)',
              border: '1px solid rgba(245, 158, 11, 0.25)',
              marginBottom: 18,
            }}
          >
            <input
              type="checkbox"
              id="tamper-check"
              checked={isTampering}
              onChange={(e) => setIsTampering(e.target.checked)}
              style={{ accentColor: 'var(--accent-warning)', cursor: 'pointer' }}
            />
            <label
              htmlFor="tamper-check"
              style={{
                fontSize: '0.75rem',
                color: 'var(--accent-warning)',
                cursor: 'pointer',
                fontWeight: 500,
              }}
            >
              Simulate Payload Tampering (Injects $5.00 mutation to trigger HTTP 422 Unprocessable Entity)
            </label>
          </div>

          {/* Action Buttons */}
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 10 }}>
            <button
              type="submit"
              className="btn btn-primary btn-block"
              disabled={isSubmitting}
            >
              <Send size={15} />
              {isSubmitting ? 'Settling Transfer...' : 'Execute Atomic Transfer'}
            </button>

            <button
              type="button"
              className="btn btn-secondary btn-block"
              disabled={isSubmitting}
              onClick={() => handleExecute(false)}
              title="Test idempotency: fire the exact same key and payload again"
            >
              <Repeat size={15} />
              Replay Key
            </button>
          </div>
        </form>

        {/* State Machine Resolution Banner */}
        {lastResult && (
          <div
            style={{
              marginTop: 18,
              padding: '12px 16px',
              borderRadius: 'var(--radius-md)',
              background:
                lastResult.status === 201
                  ? 'var(--accent-success-bg)'
                  : lastResult.status === 200
                  ? 'var(--accent-cyan-bg)'
                  : lastResult.status === 422
                  ? 'var(--accent-danger-bg)'
                  : 'rgba(255, 255, 255, 0.05)',
              border: `1px solid ${
                lastResult.status === 201
                  ? 'var(--accent-success-border)'
                  : lastResult.status === 200
                  ? 'var(--accent-cyan-border)'
                  : lastResult.status === 422
                  ? 'var(--accent-danger-border)'
                  : 'var(--border-subtle)'
              }`,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
              {lastResult.status === 201 && <CheckCircle size={16} color="var(--accent-success)" />}
              {lastResult.status === 200 && <Repeat size={16} color="var(--accent-cyan)" />}
              {(lastResult.status === 422 || lastResult.status >= 400) && (
                <AlertOctagon size={16} color="var(--accent-danger)" />
              )}

              <span
                style={{
                  fontWeight: 700,
                  fontSize: '0.8125rem',
                  color:
                    lastResult.status === 201
                      ? 'var(--accent-success)'
                      : lastResult.status === 200
                      ? 'var(--accent-cyan)'
                      : 'var(--accent-danger)',
                }}
              >
                HTTP {lastResult.status} —{' '}
                {lastResult.status === 201
                  ? 'FRESH SETTLEMENT (201 CREATED)'
                  : lastResult.status === 200
                  ? 'CACHED REPLAY SAFELY RETURNED (200 OK)'
                  : lastResult.status === 422
                  ? 'MUTATION DETECTED (422 UNPROCESSABLE ENTITY)'
                  : 'TRANSFER EXCEPTION'}
              </span>
            </div>

            <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
              {lastResult.status === 201 &&
                `Transaction ${lastResult.data?.transactionId} posted atomically. Both accounts updated without deadlock.`}
              {lastResult.status === 200 &&
                `Zero duplicate debits. The ledger safely recognized the completed idempotency key record and returned the committed result.`}
              {lastResult.status === 422 &&
                `Cryptographic SHA-256 payload mismatch! The idempotency key was previously submitted with a different payload body.`}
              {lastResult.status >= 400 && lastResult.status !== 422 && lastResult.errorMsg}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
