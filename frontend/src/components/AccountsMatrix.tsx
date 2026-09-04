import React, { useState } from 'react';
import { Account, AccountType, CreateAccountRequest } from '../types/api';
import * as Dialog from '@radix-ui/react-dialog';
import * as Tooltip from '@radix-ui/react-tooltip';
import { Copy, Plus, Search, ArrowUpRight, ArrowDownLeft, ShieldAlert, Check, X } from 'lucide-react';

interface AccountsMatrixProps {
  accounts: Account[];
  onSelectSender: (accountId: string) => void;
  onSelectReceiver: (accountId: string) => void;
  onCreateAccount: (req: CreateAccountRequest) => Promise<void>;
  onShowToast: (msg: string, type: 'success' | 'error' | 'warning' | 'info') => void;
}

export const AccountsMatrix: React.FC<AccountsMatrixProps> = ({
  accounts,
  onSelectSender,
  onSelectReceiver,
  onCreateAccount,
  onShowToast,
}) => {
  const [filterType, setFilterType] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // New Account Form State
  const [newId, setNewId] = useState('');
  const [newOwner, setNewOwner] = useState('');
  const [newType, setNewType] = useState<AccountType>('ASSET');
  const [newBalance, setNewBalance] = useState('500.00');
  const [isCreating, setIsCreating] = useState(false);

  const handleCopy = (id: string) => {
    navigator.clipboard.writeText(id);
    setCopiedId(id);
    onShowToast(`Copied ${id} to clipboard`, 'info');
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleFormSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newId || !newOwner) return;

    try {
      setIsCreating(true);
      const minorUnits = Math.round(parseFloat(newBalance || '0') * 100);
      await onCreateAccount({
        id: newId,
        ownerId: newOwner,
        accountType: newType,
        currency: 'USD',
        initialBalanceMinorUnits: minorUnits,
      });
      setIsModalOpen(false);
      setNewId('');
      setNewOwner('');
      setNewBalance('500.00');
    } catch {
      // Handled in parent
    } finally {
      setIsCreating(false);
    }
  };

  const filteredAccounts = accounts.filter((acc) => {
    const matchesType = filterType === 'ALL' || acc.accountType === filterType;
    const matchesSearch =
      acc.id.toLowerCase().includes(searchQuery.toLowerCase()) ||
      acc.ownerId.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesType && matchesSearch;
  });

  const formatUsd = (cents: number) => {
    return (cents / 100).toLocaleString('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
    });
  };

  return (
    <div className="glass-panel" style={{ marginBottom: 24 }}>
      <div className="glass-panel-header">
        <div className="glass-panel-title">
          <span style={{ fontSize: '1.25rem' }}>💳</span>
          <span>Chart of Accounts & Real-Time Balance Invariants</span>
          <span
            style={{
              fontSize: '0.75rem',
              color: 'var(--text-muted)',
              fontWeight: 400,
              marginLeft: 6,
            }}
          >
            ({filteredAccounts.length} displayed)
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
          <div style={{ position: 'relative', width: 200 }}>
            <Search
              size={14}
              style={{
                position: 'absolute',
                left: 10,
                top: '50%',
                transform: 'translateY(-50%)',
                color: 'var(--text-muted)',
              }}
            />
            <input
              type="text"
              placeholder="Search accounts..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="form-input"
              style={{ paddingLeft: 30, paddingRight: 8, height: 34, fontSize: '0.75rem' }}
            />
          </div>

          {/* Radix UI Dialog for New Account */}
          <Dialog.Root open={isModalOpen} onOpenChange={setIsModalOpen}>
            <Dialog.Trigger asChild>
              <button className="btn btn-primary btn-sm">
                <Plus size={14} />
                <span>New Account</span>
              </button>
            </Dialog.Trigger>
            <Dialog.Portal>
              <Dialog.Overlay className="radix-dialog-overlay" />
              <Dialog.Content className="radix-dialog-content">
                <div className="glass-panel-header">
                  <Dialog.Title className="glass-panel-title">
                    <span>➕</span>
                    <span>Open New Ledger Account</span>
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
                <form onSubmit={handleFormSubmit} className="glass-panel-body">
                  <div className="form-group">
                    <label className="form-label">Account ID</label>
                    <input
                      type="text"
                      required
                      placeholder="e.g. acc_customer_alice"
                      className="form-input mono"
                      value={newId}
                      onChange={(e) => setNewId(e.target.value)}
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label">Owner ID</label>
                    <input
                      type="text"
                      required
                      placeholder="e.g. user_alice_101"
                      className="form-input"
                      value={newOwner}
                      onChange={(e) => setNewOwner(e.target.value)}
                    />
                  </div>

                  <div className="form-row">
                    <div className="form-group">
                      <label className="form-label">Account GAAP Type</label>
                      <select
                        className="form-select"
                        value={newType}
                        onChange={(e) => setNewType(e.target.value as AccountType)}
                      >
                        <option value="ASSET">ASSET (Cash / Receivable)</option>
                        <option value="LIABILITY">LIABILITY (Customer Deposit / Wallet)</option>
                        <option value="EQUITY">EQUITY (Settlement / Reserve)</option>
                        <option value="REVENUE">REVENUE (Interchange Fees)</option>
                        <option value="EXPENSE">EXPENSE (Transaction Processing)</option>
                      </select>
                    </div>

                    <div className="form-group">
                      <label className="form-label">Initial Balance ($ USD)</label>
                      <input
                        type="number"
                        step="0.01"
                        min="0"
                        className="form-input mono"
                        value={newBalance}
                        onChange={(e) => setNewBalance(e.target.value)}
                      />
                    </div>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 16 }}>
                    <Dialog.Close asChild>
                      <button type="button" className="btn btn-secondary">
                        Cancel
                      </button>
                    </Dialog.Close>
                    <button
                      type="submit"
                      className="btn btn-primary"
                      disabled={isCreating}
                    >
                      {isCreating ? 'Provisioning...' : 'Create Account'}
                    </button>
                  </div>
                </form>
              </Dialog.Content>
            </Dialog.Portal>
          </Dialog.Root>
        </div>
      </div>

      {/* Account Type Filters */}
      <div
        style={{
          display: 'flex',
          gap: 6,
          padding: '12px 20px',
          borderBottom: '1px solid var(--border-subtle)',
          overflowX: 'auto',
        }}
      >
        {['ALL', 'ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'].map((t) => (
          <button
            key={t}
            onClick={() => setFilterType(t)}
            style={{
              padding: '4px 10px',
              fontSize: '0.7rem',
              fontWeight: 600,
              borderRadius: 'var(--radius-sm)',
              background: filterType === t ? 'rgba(99, 102, 241, 0.2)' : 'transparent',
              color: filterType === t ? '#818cf8' : 'var(--text-muted)',
              border: filterType === t ? '1px solid var(--border-accent)' : '1px solid transparent',
              cursor: 'pointer',
              transition: 'all 0.15s ease',
            }}
          >
            {t}
          </button>
        ))}
      </div>

      <div className="glass-panel-body">
        {filteredAccounts.length === 0 ? (
          <div
            style={{
              padding: '40px 20px',
              textAlign: 'center',
              color: 'var(--text-muted)',
              fontSize: '0.875rem',
            }}
          >
            No accounts match the current filter.
          </div>
        ) : (
          <div className="grid-3">
            {filteredAccounts.map((acc) => {
              const hasHolds = acc.pendingDebitsMinorUnits > 0;
              return (
                <div
                  key={acc.id}
                  style={{
                    background: 'var(--bg-subtle)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-md)',
                    padding: '16px',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                    gap: 12,
                    transition: 'all 0.2s ease',
                  }}
                >
                  <div>
                    {/* Header with Type & Copy */}
                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'flex-start',
                        marginBottom: 8,
                      }}
                    >
                      <div style={{ minWidth: 0 }}>
                        <div
                          style={{
                            fontFamily: 'var(--font-mono)',
                            fontSize: '0.8125rem',
                            fontWeight: 600,
                            color: 'var(--text-primary)',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 6,
                          }}
                        >
                          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {acc.id}
                          </span>
                          <Tooltip.Root>
                            <Tooltip.Trigger asChild>
                              <button
                                onClick={() => handleCopy(acc.id)}
                                style={{
                                  background: 'transparent',
                                  border: 'none',
                                  color: copiedId === acc.id ? 'var(--accent-success)' : 'var(--text-muted)',
                                  cursor: 'pointer',
                                  padding: 2,
                                  display: 'inline-flex',
                                }}
                                aria-label="Copy Account ID"
                              >
                                {copiedId === acc.id ? <Check size={12} /> : <Copy size={12} />}
                              </button>
                            </Tooltip.Trigger>
                            <Tooltip.Portal>
                              <Tooltip.Content className="radix-tooltip-content" sideOffset={5}>
                                Click to copy ID
                                <Tooltip.Arrow className="radix-tooltip-arrow" />
                              </Tooltip.Content>
                            </Tooltip.Portal>
                          </Tooltip.Root>
                        </div>
                        <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                          Owner: {acc.ownerId}
                        </div>
                      </div>

                      <span
                        className="status-pill"
                        style={{
                          fontSize: '0.65rem',
                          padding: '2px 8px',
                          textTransform: 'uppercase',
                          letterSpacing: '0.05em',
                          borderColor:
                            acc.accountType === 'ASSET'
                              ? 'var(--accent-success-border)'
                              : acc.accountType === 'LIABILITY'
                              ? 'var(--accent-danger-border)'
                              : 'var(--border-subtle)',
                          color:
                            acc.accountType === 'ASSET'
                              ? 'var(--accent-success)'
                              : acc.accountType === 'LIABILITY'
                              ? 'var(--accent-danger)'
                              : 'var(--text-secondary)',
                        }}
                      >
                        {acc.accountType}
                      </span>
                    </div>

                    {/* Available Balance */}
                    <div style={{ marginTop: 6 }}>
                      <div style={{ fontSize: '0.7rem', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        Available Balance
                      </div>
                      <div
                        style={{
                          fontSize: '1.35rem',
                          fontWeight: 700,
                          fontFamily: 'var(--font-mono)',
                          color: acc.availableBalanceMinorUnits < 0 ? 'var(--accent-danger)' : 'var(--text-primary)',
                          marginTop: 2,
                        }}
                      >
                        {formatUsd(acc.availableBalanceMinorUnits)}
                      </div>
                    </div>

                    {/* Secondary Invariants Row */}
                    <div
                      style={{
                        marginTop: 8,
                        paddingTop: 8,
                        borderTop: '1px solid var(--border-subtle)',
                        display: 'flex',
                        justifyContent: 'space-between',
                        fontSize: '0.725rem',
                        color: 'var(--text-muted)',
                      }}
                    >
                      <span>Posted: {formatUsd(acc.postedBalanceMinorUnits)}</span>
                      {hasHolds ? (
                        <span style={{ color: 'var(--accent-warning)', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                          <ShieldAlert size={12} />
                          Held: {formatUsd(acc.pendingDebitsMinorUnits)}
                        </span>
                      ) : (
                        <span style={{ color: 'var(--accent-success)' }}>Holds: $0.00</span>
                      )}
                    </div>
                  </div>

                  {/* Actions */}
                  <div
                    style={{
                      display: 'flex',
                      gap: 6,
                      marginTop: 4,
                    }}
                  >
                    <button
                      className="btn btn-secondary btn-sm"
                      style={{ flex: 1, padding: '4px 6px', fontSize: '0.7rem' }}
                      onClick={() => onSelectSender(acc.id)}
                      title="Set as Sender Account"
                    >
                      <ArrowUpRight size={12} color="var(--accent-danger)" />
                      Send From
                    </button>
                    <button
                      className="btn btn-secondary btn-sm"
                      style={{ flex: 1, padding: '4px 6px', fontSize: '0.7rem' }}
                      onClick={() => onSelectReceiver(acc.id)}
                      title="Set as Destination Account"
                    >
                      <ArrowDownLeft size={12} color="var(--accent-success)" />
                      Receive To
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
