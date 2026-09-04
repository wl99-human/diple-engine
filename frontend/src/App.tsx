import React, { useState, useEffect, useCallback } from 'react';
import { Account, JournalPosting, CreateAccountRequest } from './types/api';
import { api } from './services/api';
import { Header } from './components/Header';
import { AccountsMatrix } from './components/AccountsMatrix';
import { TransferConsole } from './components/TransferConsole';
import { PaymentHoldsConsole } from './components/PaymentHoldsConsole';
import { ChaosBenchmark } from './components/ChaosBenchmark';
import { MerkleLedgerStream } from './components/MerkleLedgerStream';
import * as Tooltip from '@radix-ui/react-tooltip';
import { Toaster, toast } from 'sonner';
import { Zap, Clock, Flame, ScrollText } from 'lucide-react';

type TabType = 'transfers' | 'holds' | 'chaos' | 'ledger';

export const App: React.FC = () => {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [postings, setPostings] = useState<JournalPosting[]>([]);
  const [isConserved, setIsConserved] = useState<boolean>(true);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isAutoRefresh, setIsAutoRefresh] = useState<boolean>(true);
  const [isSeeding, setIsSeeding] = useState<boolean>(false);
  const [activeTab, setActiveTab] = useState<TabType>('transfers');

  // Shared Account Selection
  const [selectedSender, setSelectedSender] = useState<string>('');
  const [selectedReceiver, setSelectedReceiver] = useState<string>('');

  const showToast = useCallback(
    (message: string, type: 'success' | 'error' | 'warning' | 'info' = 'info') => {
      switch (type) {
        case 'success':
          toast.success(message);
          break;
        case 'error':
          toast.error(message);
          break;
        case 'warning':
          toast.warning(message);
          break;
        case 'info':
        default:
          toast.info(message);
          break;
      }
    },
    []
  );

  const loadData = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      const [accs, recentPosts] = await Promise.all([
        api.getAccounts(),
        api.getRecentPostings(),
      ]);
      setAccounts(accs);
      setPostings(recentPosts);

      // Auto-set sender/receiver if not set
      if (accs.length > 0) {
        setSelectedSender((curr) => (curr && accs.some((a) => a.id === curr) ? curr : accs[0].id));
      }
      if (accs.length > 1) {
        setSelectedReceiver((curr) => {
          if (curr && accs.some((a) => a.id === curr)) return curr;
          const target = accs.find((a) => a.id.includes('merchant')) || accs[1];
          return target.id;
        });
      }
    } catch (err: any) {
      if (!silent) {
        toast.error(`Failed to load engine data: ${err.message}`);
      }
    } finally {
      if (!silent) setIsLoading(false);
    }
  }, []);

  // Initial load
  useEffect(() => {
    loadData();
  }, [loadData]);

  // Periodic Auto-refresh
  useEffect(() => {
    if (!isAutoRefresh) return;
    const timer = setInterval(() => {
      loadData(true);
    }, 4000);
    return () => clearInterval(timer);
  }, [isAutoRefresh, loadData]);

  const handleSeedAccounts = async () => {
    try {
      setIsSeeding(true);
      await api.seedAccounts();
      toast.success('Genesis accounts and settlement funds seeded successfully!');
      await loadData();
    } catch (err: any) {
      toast.error(`Seeding failed: ${err.message}`);
    } finally {
      setIsSeeding(false);
    }
  };

  const handleCreateAccount = async (req: CreateAccountRequest) => {
    try {
      const created = await api.createAccount(req);
      toast.success(`Account ${created.id} provisioned with $${(created.postedBalanceMinorUnits / 100).toFixed(2)}`);
      await loadData();
    } catch (err: any) {
      toast.error(`Account creation failed: ${err.message}`);
      throw err;
    }
  };

  return (
    <Tooltip.Provider delayDuration={150}>
      <div className="app-container">
        <Header
          accountsCount={accounts.length}
          isConserved={isConserved}
          isLoading={isLoading}
          isAutoRefresh={isAutoRefresh}
          onToggleAutoRefresh={() => setIsAutoRefresh((v) => !v)}
          onRefresh={() => loadData(false)}
          onSeedAccounts={handleSeedAccounts}
          isSeeding={isSeeding}
        />

        {/* Navigation Tabs */}
        <nav className="nav-tabs">
          <button
            className={`nav-tab-btn ${activeTab === 'transfers' ? 'active' : ''}`}
            onClick={() => setActiveTab('transfers')}
          >
            <Zap size={16} />
            <span>Transfers & Idempotency</span>
          </button>

          <button
            className={`nav-tab-btn ${activeTab === 'holds' ? 'active' : ''}`}
            onClick={() => setActiveTab('holds')}
          >
            <Clock size={16} />
            <span>2-Phase Holds & Escrow</span>
          </button>

          <button
            className={`nav-tab-btn ${activeTab === 'chaos' ? 'active' : ''}`}
            onClick={() => setActiveTab('chaos')}
          >
            <Flame size={16} />
            <span>Chaos Stress Benchmark</span>
          </button>

          <button
            className={`nav-tab-btn ${activeTab === 'ledger' ? 'active' : ''}`}
            onClick={() => setActiveTab('ledger')}
          >
            <ScrollText size={16} />
            <span>Double-Entry Merkle Stream</span>
          </button>
        </nav>

        {/* Main Tab Content */}
        <main>
          {activeTab === 'transfers' && (
            <div>
              <div className="grid-2" style={{ marginBottom: 24 }}>
                <TransferConsole
                  accounts={accounts}
                  selectedSender={selectedSender}
                  selectedReceiver={selectedReceiver}
                  onSenderChange={setSelectedSender}
                  onReceiverChange={setSelectedReceiver}
                  onTransferSuccess={() => loadData(true)}
                  onShowToast={showToast}
                />
                <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
                  <MerkleLedgerStream
                    postings={postings.slice(0, 10)}
                    onAuditComplete={(r) => setIsConserved(r.status === 'HEALTHY_100_PERCENT_CONSERVED')}
                    onShowToast={showToast}
                  />
                </div>
              </div>

              <AccountsMatrix
                accounts={accounts}
                onSelectSender={setSelectedSender}
                onSelectReceiver={setSelectedReceiver}
                onCreateAccount={handleCreateAccount}
                onShowToast={showToast}
              />
            </div>
          )}

          {activeTab === 'holds' && (
            <div>
              <PaymentHoldsConsole
                accounts={accounts}
                onHoldSuccess={() => loadData(true)}
                onShowToast={showToast}
              />

              <AccountsMatrix
                accounts={accounts}
                onSelectSender={setSelectedSender}
                onSelectReceiver={setSelectedReceiver}
                onCreateAccount={handleCreateAccount}
                onShowToast={showToast}
              />
            </div>
          )}

          {activeTab === 'chaos' && (
            <div>
              <ChaosBenchmark
                onBenchmarkComplete={() => loadData(true)}
                onShowToast={showToast}
              />

              <AccountsMatrix
                accounts={accounts}
                onSelectSender={setSelectedSender}
                onSelectReceiver={setSelectedReceiver}
                onCreateAccount={handleCreateAccount}
                onShowToast={showToast}
              />
            </div>
          )}

          {activeTab === 'ledger' && (
            <div>
              <MerkleLedgerStream
                postings={postings}
                onAuditComplete={(r) => setIsConserved(r.status === 'HEALTHY_100_PERCENT_CONSERVED')}
                onShowToast={showToast}
              />

              <AccountsMatrix
                accounts={accounts}
                onSelectSender={setSelectedSender}
                onSelectReceiver={setSelectedReceiver}
                onCreateAccount={handleCreateAccount}
                onShowToast={showToast}
              />
            </div>
          )}
        </main>

        <Toaster
          theme="dark"
          position="bottom-right"
          richColors
          closeButton
          toastOptions={{
            style: {
              background: '#0d131f',
              border: '1px solid var(--border-medium)',
              color: 'var(--text-primary)',
            },
          }}
        />
      </div>
    </Tooltip.Provider>
  );
};

export default App;
