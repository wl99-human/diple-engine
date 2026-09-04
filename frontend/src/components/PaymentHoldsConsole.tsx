import React, { useState } from 'react';
import { Account, PaymentHold } from '../types/api';
import { api, ApiServiceError } from '../services/api';
import * as Dialog from '@radix-ui/react-dialog';
import { ShieldCheck, Clock, CheckCircle2, XCircle, ArrowRight, X } from 'lucide-react';

interface PaymentHoldsConsoleProps {
  accounts: Account[];
  onHoldSuccess: () => void;
  onShowToast: (msg: string, type: 'success' | 'error' | 'warning' | 'info') => void;
}

export const PaymentHoldsConsole: React.FC<PaymentHoldsConsoleProps> = ({
  accounts,
  onHoldSuccess,
  onShowToast,
}) => {
  const [accountId, setAccountId] = useState(accounts[0]?.id || '');
  const [amount, setAmount] = useState('35.00');
  const [refId, setRefId] = useState('order_ref_' + Math.floor(Math.random() * 90000 + 10000));
  const [description, setDescription] = useState('E-Commerce 2-Phase Hold');
  const [durationMinutes, setDurationMinutes] = useState(15);
  const [isAuthorizing, setIsAuthorizing] = useState(false);

  // Managed active holds list in session
  const [holds, setHolds] = useState<PaymentHold[]>([]);

  // Capture modal state
  const [captureModalHold, setCaptureModalHold] = useState<PaymentHold | null>(null);
  const [destinationAccount, setDestinationAccount] = useState<string>('');
  const [isCapturing, setIsCapturing] = useState(false);

  const handleAuthorizeHold = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!accountId) {
      onShowToast('Please select an account for the hold', 'warning');
      return;
    }

    const numAmount = parseFloat(amount);
    if (isNaN(numAmount) || numAmount <= 0) {
      onShowToast('Enter a valid amount', 'warning');
      return;
    }

    try {
      setIsAuthorizing(true);
      const hold = await api.createHold({
        accountId,
        amountMinorUnits: Math.round(numAmount * 100),
        currency: 'USD',
        referenceId: refId,
        description,
        holdDurationMinutes: durationMinutes,
      });

      setHolds((prev) => [hold, ...prev]);
      onShowToast(`2-Phase Hold ${hold.id.substring(0, 8)}... authorized! Pending debits reserved.`, 'success');
      setRefId('order_ref_' + Math.floor(Math.random() * 90000 + 10000));
      onHoldSuccess();
    } catch (err: any) {
      const msg = err instanceof ApiServiceError ? err.message : err.message;
      onShowToast(`Hold authorization failed: ${msg}`, 'error');
    } finally {
      setIsAuthorizing(false);
    }
  };

  const handleOpenCapture = (hold: PaymentHold) => {
    setCaptureModalHold(hold);
    const otherAccount = accounts.find((a) => a.id !== hold.accountId);
    setDestinationAccount(otherAccount ? otherAccount.id : '');
  };

  const handleExecuteCapture = async () => {
    if (!captureModalHold || !destinationAccount) return;

    try {
      setIsCapturing(true);
      await api.captureHold(captureModalHold.id, {
        destinationAccountId: destinationAccount,
        description: `Settlement capture for ${captureModalHold.id}`,
      });

      setHolds((prev) =>
        prev.map((h) => (h.id === captureModalHold.id ? { ...h, status: 'CAPTURED' } : h))
      );

      onShowToast(`Hold ${captureModalHold.id.substring(0, 8)}... captured and settled to ${destinationAccount}!`, 'success');
      setCaptureModalHold(null);
      onHoldSuccess();
    } catch (err: any) {
      onShowToast(`Capture failed: ${err.message}`, 'error');
    } finally {
      setIsCapturing(false);
    }
  };

  const handleVoidHold = async (hold: PaymentHold) => {
    try {
      await api.voidHold(hold.id, 'User cancelled authorization');
      setHolds((prev) =>
        prev.map((h) => (h.id === hold.id ? { ...h, status: 'VOIDED' } : h))
      );
      onShowToast(`Hold ${hold.id.substring(0, 8)}... voided. Reserved funds returned to available balance.`, 'info');
      onHoldSuccess();
    } catch (err: any) {
      onShowToast(`Void failed: ${err.message}`, 'error');
    }
  };

  return (
    <div className="glass-panel" style={{ marginBottom: 24 }}>
      <div className="glass-panel-header">
        <div className="glass-panel-title">
          <Clock size={18} color="var(--accent-warning)" />
          <span>Two-Phase Payment Holds & Escrow Engine</span>
        </div>
        <div className="status-pill warning">
          <span>Authorize • Capture • Void (TTL Guarded)</span>
        </div>
      </div>

      <div className="glass-panel-body">
        <p style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', marginBottom: 20 }}>
          Phase 1 <strong>Authorizes</strong> a hold against an account's available balance without posting an immediate journal entry.
          Phase 2 <strong>Captures</strong> funds to the merchant or <strong>Voids</strong> the hold to restore available liquidity.
        </p>

        <form onSubmit={handleAuthorizeHold} style={{ marginBottom: 24 }}>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Account to Reserve Funds (Debit Source)</label>
              <select
                className="form-select mono"
                value={accountId}
                onChange={(e) => setAccountId(e.target.value)}
                required
              >
                <option value="" disabled>
                  Select account...
                </option>
                {accounts.map((acc) => (
                  <option key={acc.id} value={acc.id}>
                    {acc.id} (Avail: ${(acc.availableBalanceMinorUnits / 100).toFixed(2)})
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">Hold Amount ($ USD)</label>
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
              <label className="form-label">Hold TTL Duration (Minutes)</label>
              <select
                className="form-select"
                value={durationMinutes}
                onChange={(e) => setDurationMinutes(parseInt(e.target.value))}
              >
                <option value={5}>5 Minutes</option>
                <option value={15}>15 Minutes</option>
                <option value={30}>30 Minutes</option>
                <option value={60}>60 Minutes</option>
              </select>
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Reference ID (Merchant / Order)</label>
              <input
                type="text"
                required
                className="form-input mono"
                value={refId}
                onChange={(e) => setRefId(e.target.value)}
              />
            </div>

            <div className="form-group">
              <label className="form-label">Description / Note</label>
              <input
                type="text"
                required
                className="form-input"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>
          </div>

          <button
            type="submit"
            className="btn btn-primary"
            disabled={isAuthorizing}
          >
            <ShieldCheck size={16} />
            {isAuthorizing ? 'Authorizing Hold...' : 'Authorize 2-Phase Hold (Phase 1)'}
          </button>
        </form>

        {/* Active Holds List */}
        <div>
          <div
            style={{
              fontSize: '0.8125rem',
              fontWeight: 600,
              color: 'var(--text-secondary)',
              marginBottom: 10,
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
            }}
          >
            Session Payment Holds ({holds.length})
          </div>

          {holds.length === 0 ? (
            <div
              style={{
                padding: '24px',
                textAlign: 'center',
                background: 'rgba(255, 255, 255, 0.02)',
                borderRadius: 'var(--radius-md)',
                color: 'var(--text-muted)',
                fontSize: '0.8125rem',
              }}
            >
              No payment holds authorized in this session yet. Use the form above to authorize a pre-auth hold.
            </div>
          ) : (
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Hold ID</th>
                    <th>Account</th>
                    <th>Amount</th>
                    <th>Status</th>
                    <th>Expires At</th>
                    <th>Actions (Phase 2)</th>
                  </tr>
                </thead>
                <tbody>
                  {holds.map((h) => {
                    const isHoldActive = h.status === 'ACTIVE';
                    return (
                      <tr key={h.id}>
                        <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem' }}>
                          {h.id.substring(0, 14)}...
                        </td>
                        <td style={{ fontFamily: 'var(--font-mono)' }}>
                          <strong>{h.accountId}</strong>
                        </td>
                        <td style={{ fontWeight: 600 }}>
                          ${(h.amount / 100).toFixed(2)} {h.currency}
                        </td>
                        <td>
                          <span
                            className={`status-pill ${
                              h.status === 'ACTIVE'
                                ? 'warning'
                                : h.status === 'CAPTURED'
                                ? 'success'
                                : 'danger'
                            }`}
                            style={{ fontSize: '0.7rem' }}
                          >
                            {h.status}
                          </span>
                        </td>
                        <td style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                          {new Date(h.expiresAt).toLocaleTimeString()}
                        </td>
                        <td>
                          {isHoldActive ? (
                            <div style={{ display: 'flex', gap: 6 }}>
                              <button
                                className="btn btn-success btn-sm"
                                onClick={() => handleOpenCapture(h)}
                                title="Capture hold and transfer funds to destination account"
                              >
                                <CheckCircle2 size={12} />
                                Capture
                              </button>
                              <button
                                className="btn btn-danger btn-sm"
                                onClick={() => handleVoidHold(h)}
                                title="Void hold and release funds back to available balance"
                              >
                                <XCircle size={12} />
                                Void
                              </button>
                            </div>
                          ) : (
                            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                              Settled
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {/* Radix UI Dialog: Capture Hold */}
      <Dialog.Root
        open={!!captureModalHold}
        onOpenChange={(open) => {
          if (!open) setCaptureModalHold(null);
        }}
      >
        <Dialog.Portal>
          <Dialog.Overlay className="radix-dialog-overlay" />
          <Dialog.Content className="radix-dialog-content">
            <div className="glass-panel-header">
              <Dialog.Title className="glass-panel-title">
                <CheckCircle2 size={18} color="var(--accent-success)" />
                <span>Capture 2-Phase Payment Hold</span>
              </Dialog.Title>
              <Dialog.Close asChild>
                <button
                  className="btn btn-secondary btn-sm"
                  style={{ padding: '2px 8px' }}
                  aria-label="Close"
                >
                  <X size={14} />
                </button>
              </Dialog.Close>
            </div>
            <div className="glass-panel-body">
              {captureModalHold && (
                <>
                  <p style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', marginBottom: 16 }}>
                    Capturing will debit <strong>${(captureModalHold.amount / 100).toFixed(2)}</strong> from{' '}
                    <code className="mono">{captureModalHold.accountId}</code> and credit the selected destination account.
                  </p>

                  <div className="form-group">
                    <label className="form-label">Destination Settlement Account (Credit)</label>
                    <select
                      className="form-select mono"
                      value={destinationAccount}
                      onChange={(e) => setDestinationAccount(e.target.value)}
                      required
                    >
                      <option value="" disabled>
                        Select destination merchant/settlement account...
                      </option>
                      {accounts
                        .filter((a) => a.id !== captureModalHold.accountId)
                        .map((acc) => (
                          <option key={acc.id} value={acc.id}>
                            {acc.id} ({acc.accountType})
                          </option>
                        ))}
                    </select>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 20 }}>
                    <Dialog.Close asChild>
                      <button type="button" className="btn btn-secondary">
                        Cancel
                      </button>
                    </Dialog.Close>
                    <button
                      type="button"
                      className="btn btn-success"
                      onClick={handleExecuteCapture}
                      disabled={isCapturing || !destinationAccount}
                    >
                      <ArrowRight size={14} />
                      {isCapturing ? 'Executing Capture...' : 'Confirm Capture & Settle'}
                    </button>
                  </div>
                </>
              )}
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  );
};
