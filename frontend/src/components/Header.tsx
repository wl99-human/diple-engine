import React from 'react';
import { ShieldCheck, RefreshCw, Database, Activity, Lock } from 'lucide-react';

interface HeaderProps {
  accountsCount: number;
  isConserved: boolean;
  isLoading: boolean;
  isAutoRefresh: boolean;
  onToggleAutoRefresh: () => void;
  onRefresh: () => void;
  onSeedAccounts: () => void;
  isSeeding: boolean;
}

export const Header: React.FC<HeaderProps> = ({
  accountsCount,
  isConserved,
  isLoading,
  isAutoRefresh,
  onToggleAutoRefresh,
  onRefresh,
  onSeedAccounts,
  isSeeding,
}) => {
  return (
    <header
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: 16,
        paddingBottom: 24,
        marginBottom: 20,
        borderBottom: '1px solid var(--border-subtle)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        <div
          style={{
            background: 'linear-gradient(135deg, #6366f1 0%, #06b6d4 100%)',
            padding: '8px 14px',
            borderRadius: 'var(--radius-md)',
            fontWeight: 800,
            fontSize: '1.25rem',
            letterSpacing: '0.05em',
            color: '#fff',
            boxShadow: '0 4px 14px rgba(99, 102, 241, 0.4)',
          }}
        >
          DIPLE
        </div>
        <div>
          <h1 style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)', letterSpacing: '-0.02em' }}>
            Distributed Idempotent Payment & Ledger Engine
          </h1>
          <p style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
            Real-Time Double-Entry Accounting • Deterministic Pessimistic Locks • Merkle Audit Chain
          </p>
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
        {/* Status Pills */}
        <div className={`status-pill ${isConserved ? 'success' : 'danger'}`}>
          <span className="pulse-dot"></span>
          <ShieldCheck size={14} />
          <span>{isConserved ? 'Ledger Conserved' : 'Invariant Alert'}</span>
        </div>

        <div className="status-pill cyan">
          <Lock size={13} />
          <span>Deterministic Lock Order</span>
        </div>

        <div className="status-pill">
          <Database size={13} />
          <span>{accountsCount} Accounts Active</span>
        </div>

        {/* Action Controls */}
        <button
          className="btn btn-secondary btn-sm"
          onClick={onSeedAccounts}
          disabled={isSeeding}
          title="Seed standard test accounts and genesis ledger entries"
        >
          <Activity size={14} className={isSeeding ? 'animate-spin' : ''} />
          {isSeeding ? 'Seeding...' : 'Seed Accounts'}
        </button>

        <button
          className={`btn btn-sm ${isAutoRefresh ? 'btn-secondary' : 'btn-secondary'}`}
          onClick={onToggleAutoRefresh}
          style={{
            borderColor: isAutoRefresh ? 'var(--accent-success)' : undefined,
            color: isAutoRefresh ? 'var(--accent-success)' : undefined,
          }}
          title="Toggle 4s periodic background synchronization"
        >
          <span className="pulse-dot" style={{ display: isAutoRefresh ? 'inline-block' : 'none', color: 'var(--accent-success)' }}></span>
          {isAutoRefresh ? 'Live Sync (4s)' : 'Sync Paused'}
        </button>

        <button
          className="btn btn-primary btn-sm"
          onClick={onRefresh}
          disabled={isLoading}
          title="Refresh balances and recent ledger postings"
        >
          <RefreshCw size={14} style={{ animation: isLoading ? 'spin 1s linear infinite' : 'none' }} />
          Refresh
        </button>
      </div>

      <style>{`
        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
      `}</style>
    </header>
  );
};
