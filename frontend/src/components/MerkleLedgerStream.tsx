import React, { useState } from 'react';
import { JournalPosting, SystemReconciliationReport } from '../types/api';
import { api } from '../services/api';
import * as Tooltip from '@radix-ui/react-tooltip';
import { ShieldCheck, Search, Copy, Check, Hash, FileCheck, ArrowUpRight, ArrowDownLeft } from 'lucide-react';

interface MerkleLedgerStreamProps {
  postings: JournalPosting[];
  onAuditComplete?: (report: SystemReconciliationReport) => void;
  onShowToast: (msg: string, type: 'success' | 'error' | 'warning' | 'info') => void;
}

export const MerkleLedgerStream: React.FC<MerkleLedgerStreamProps> = ({
  postings,
  onAuditComplete,
  onShowToast,
}) => {
  const [filterQuery, setFilterQuery] = useState('');
  const [copiedHash, setCopiedHash] = useState<string | null>(null);
  const [isAuditing, setIsAuditing] = useState(false);
  const [auditReport, setAuditReport] = useState<SystemReconciliationReport | null>(null);

  const handleCopy = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    setCopiedHash(text);
    onShowToast(`Copied ${label} to clipboard`, 'info');
    setTimeout(() => setCopiedHash(null), 2000);
  };

  const handleRunReconciliation = async () => {
    try {
      setIsAuditing(true);
      const report = await api.runReconciliation();
      setAuditReport(report);
      if (onAuditComplete) {
        onAuditComplete(report);
      }

      if (report.status === 'HEALTHY_100_PERCENT_CONSERVED') {
        onShowToast(
          `Cryptographic Audit Passed! All ${report.totalAccountsAudited} accounts 100% conserved. Merkle root chain intact!`,
          'success'
        );
      } else {
        onShowToast(`Audit Warning: Discrepancy detected in double-entry ledger!`, 'error');
      }
    } catch (err: any) {
      onShowToast(`Audit reconciliation failed: ${err.message}`, 'error');
    } finally {
      setIsAuditing(false);
    }
  };

  const filteredPostings = postings.filter(
    (p) =>
      p.accountId.toLowerCase().includes(filterQuery.toLowerCase()) ||
      p.transactionId.toLowerCase().includes(filterQuery.toLowerCase()) ||
      p.entryHash.toLowerCase().includes(filterQuery.toLowerCase())
  );

  return (
    <div className="glass-panel" style={{ marginBottom: 24 }}>
      <div className="glass-panel-header">
        <div className="glass-panel-title">
          <Hash size={18} color="var(--accent-cyan)" />
          <span>Immutable Double-Entry Ledger Stream & Merkle Audit Trail</span>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 400 }}>
            ({filteredPostings.length} postings)
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
          <div style={{ position: 'relative', width: 220 }}>
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
              placeholder="Filter by account, Tx, hash..."
              value={filterQuery}
              onChange={(e) => setFilterQuery(e.target.value)}
              className="form-input"
              style={{ paddingLeft: 30, paddingRight: 8, height: 34, fontSize: '0.75rem' }}
            />
          </div>

          <button
            className="btn btn-cyan btn-sm"
            onClick={handleRunReconciliation}
            disabled={isAuditing}
          >
            <ShieldCheck size={14} />
            {isAuditing ? 'Verifying Merkle Roots...' : 'Verify Cryptographic Audit Chain'}
          </button>
        </div>
      </div>

      {/* Audit Report Banner if ran */}
      {auditReport && (
        <div
          style={{
            padding: '12px 20px',
            borderBottom: '1px solid var(--border-subtle)',
            background:
              auditReport.status === 'HEALTHY_100_PERCENT_CONSERVED'
                ? 'rgba(6, 182, 212, 0.08)'
                : 'var(--accent-danger-bg)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 10,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <FileCheck size={18} color="var(--accent-cyan)" />
            <div>
              <span style={{ fontWeight: 700, fontSize: '0.8125rem', color: 'var(--accent-cyan)' }}>
                AUDIT STATUS: {auditReport.status}
              </span>
              <div style={{ fontSize: '0.725rem', color: 'var(--text-secondary)' }}>
                Total Accounts Audited: {auditReport.totalAccountsAudited} • Sum of Debits == Sum of Credits • SHA-256 Chaining Verified
              </div>
            </div>
          </div>
          <span style={{ fontSize: '0.725rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
            Audited: {new Date(auditReport.auditedAt).toLocaleTimeString()}
          </span>
        </div>
      )}

      <div className="glass-panel-body" style={{ padding: 0 }}>
        <div className="table-container" style={{ border: 'none', borderRadius: 0 }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Transaction ID</th>
                <th>Account</th>
                <th>Direction</th>
                <th>Amount</th>
                <th>Balance After</th>
                <th>SHA-256 Merkle Hash Node</th>
                <th>Timestamp</th>
              </tr>
            </thead>
            <tbody>
              {filteredPostings.length === 0 ? (
                <tr>
                  <td colSpan={8} style={{ textAlign: 'center', padding: '32px', color: 'var(--text-muted)' }}>
                    No postings found. Execute transfers to populate the immutable double-entry ledger.
                  </td>
                </tr>
              ) : (
                filteredPostings.map((p) => {
                  const isDebit = p.direction === 'DEBIT';
                  const isCopied = copiedHash === p.entryHash;
                  return (
                    <tr key={p.id}>
                      <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                        #{p.id}
                      </td>
                      <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem' }}>
                        <Tooltip.Root>
                          <Tooltip.Trigger asChild>
                            <span
                              style={{
                                cursor: 'pointer',
                                color: 'var(--text-secondary)',
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 4,
                              }}
                              onClick={() => handleCopy(p.transactionId, 'Transaction ID')}
                            >
                              {p.transactionId.substring(0, 12)}...
                              <Copy size={10} />
                            </span>
                          </Tooltip.Trigger>
                          <Tooltip.Portal>
                            <Tooltip.Content className="radix-tooltip-content" sideOffset={5}>
                              <div>Transaction UUID:</div>
                              <div style={{ color: 'var(--text-primary)' }}>{p.transactionId}</div>
                              <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)', marginTop: 2 }}>Click to copy</div>
                              <Tooltip.Arrow className="radix-tooltip-arrow" />
                            </Tooltip.Content>
                          </Tooltip.Portal>
                        </Tooltip.Root>
                      </td>
                      <td style={{ fontFamily: 'var(--font-mono)' }}>
                        <strong>{p.accountId}</strong>
                      </td>
                      <td>
                        <span
                          className={`status-pill ${isDebit ? 'danger' : 'success'}`}
                          style={{ fontSize: '0.7rem', padding: '2px 8px' }}
                        >
                          {isDebit ? <ArrowUpRight size={11} /> : <ArrowDownLeft size={11} />}
                          {p.direction}
                        </span>
                      </td>
                      <td
                        style={{
                          fontWeight: 600,
                          fontFamily: 'var(--font-mono)',
                          color: isDebit ? 'var(--accent-danger)' : 'var(--accent-success)',
                        }}
                      >
                        {isDebit ? '-' : '+'}${(p.amount / 100).toFixed(2)}
                      </td>
                      <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8125rem' }}>
                        ${(p.accountBalanceAfter / 100).toFixed(2)}
                      </td>
                      <td>
                        <Tooltip.Root>
                          <Tooltip.Trigger asChild>
                            <div
                              style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 6,
                                cursor: 'pointer',
                              }}
                              onClick={() => handleCopy(p.entryHash, 'SHA-256 Hash')}
                            >
                              <span className="hash-cell">{p.entryHash}</span>
                              {isCopied ? (
                                <Check size={12} color="var(--accent-success)" />
                              ) : (
                                <Copy size={12} color="var(--accent-cyan)" />
                              )}
                            </div>
                          </Tooltip.Trigger>
                          <Tooltip.Portal>
                            <Tooltip.Content className="radix-tooltip-content" sideOffset={5}>
                              <div>SHA-256 Merkle Entry Hash:</div>
                              <div style={{ color: 'var(--accent-cyan)' }}>{p.entryHash}</div>
                              <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)', marginTop: 2 }}>Click to copy hash</div>
                              <Tooltip.Arrow className="radix-tooltip-arrow" />
                            </Tooltip.Content>
                          </Tooltip.Portal>
                        </Tooltip.Root>
                      </td>
                      <td style={{ fontSize: '0.75rem', color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                        {new Date(p.createdAt).toLocaleTimeString()}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
