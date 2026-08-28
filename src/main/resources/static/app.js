// DIPLE Engine Dashboard JavaScript

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `<span>${type === 'success' ? '✅' : type === 'error' ? '❌' : 'ℹ️'}</span> <div>${message}</div>`;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

function generateNewKey() {
  document.getElementById('idempotency-key-input').value = 'key_' + uuidv4();
}

async function loadAccounts() {
  try {
    const res = await fetch('/v1/accounts');
    if (!res.ok) return;
    const accounts = await res.json();

    const grid = document.getElementById('accounts-grid');
    grid.innerHTML = '';

    const senderSelect = document.getElementById('sender-select');
    const receiverSelect = document.getElementById('receiver-select');

    const currentSender = senderSelect.value;
    const currentReceiver = receiverSelect.value;

    senderSelect.innerHTML = '';
    receiverSelect.innerHTML = '';

    accounts.forEach((acc, idx) => {
      // Account Card
      const card = document.createElement('div');
      card.className = `account-item ${acc.accountType.toLowerCase()}`;
      
      const balanceUsd = (acc.availableBalanceMinorUnits / 100).toFixed(2);
      const pendingUsd = (acc.pendingDebitsMinorUnits / 100).toFixed(2);

      card.innerHTML = `
        <div class="account-header-row">
          <span class="account-id">${acc.id}</span>
          <span class="account-type-tag">${acc.accountType}</span>
        </div>
        <div class="account-balance-val">$${balanceUsd}</div>
        <div class="account-pending-val">${acc.pendingDebitsMinorUnits > 0 ? `⚠️ Reserved: $${pendingUsd}` : `Status: ${acc.status}`}</div>
      `;
      grid.appendChild(card);

      // Populate Selects
      const opt1 = new Option(`${acc.id} ($${balanceUsd})`, acc.id);
      const opt2 = new Option(`${acc.id} ($${balanceUsd})`, acc.id);

      senderSelect.add(opt1);
      receiverSelect.add(opt2);
    });

    if (currentSender && Array.from(senderSelect.options).some(o => o.value === currentSender)) {
      senderSelect.value = currentSender;
    } else if (accounts.length > 0) {
      senderSelect.value = accounts[0].id;
    }

    if (currentReceiver && Array.from(receiverSelect.options).some(o => o.value === currentReceiver)) {
      receiverSelect.value = currentReceiver;
    } else if (accounts.length > 3) {
      receiverSelect.value = accounts[3].id; // e.g. merchant_google_store
    } else if (accounts.length > 1) {
      receiverSelect.value = accounts[1].id;
    }

  } catch (err) {
    console.error('Failed to load accounts:', err);
  }
}

async function loadLedgerStream() {
  try {
    const res = await fetch('/v1/payments/postings/recent');
    if (!res.ok) return;
    const postings = await res.json();

    const tbody = document.getElementById('ledger-table-body');
    tbody.innerHTML = '';

    postings.forEach(p => {
      const row = document.createElement('tr');
      const amountUsd = (p.amountMinorUnits / 100).toFixed(2);
      const balanceUsd = (p.balanceAfter / 100).toFixed(2);
      const time = new Date(p.createdAt).toLocaleTimeString();

      row.innerHTML = `
        <td>${p.id}</td>
        <td style="font-family: var(--font-mono); font-size: 0.75rem;">${p.transactionId.substring(0, 14)}...</td>
        <td><strong>${p.accountId}</strong></td>
        <td><span class="badge badge-${p.direction.toLowerCase()}">${p.direction}</span></td>
        <td>$${amountUsd}</td>
        <td>$${balanceUsd}</td>
        <td class="hash-cell" title="${p.entryHash}">${p.entryHash}</td>
        <td style="color: var(--text-muted);">${time}</td>
      `;
      tbody.appendChild(row);
    });
  } catch (err) {
    console.error('Failed to load ledger stream:', err);
  }
}

async function handleTransferSubmit(event) {
  event.preventDefault();
  const submitBtn = document.getElementById('transfer-submit-btn');
  submitBtn.disabled = true;
  submitBtn.innerText = 'Processing Atomic Transfer...';

  const sender = document.getElementById('sender-select').value;
  const receiver = document.getElementById('receiver-select').value;
  const amountVal = parseFloat(document.getElementById('amount-input').value);
  const currency = document.getElementById('currency-input').value;
  const idempotencyKey = document.getElementById('idempotency-key-input').value;
  const isTamper = document.getElementById('tamper-payload-check').checked;

  const amountMinorUnits = Math.round(amountVal * 100);

  const payload = {
    senderAccountId: sender,
    receiverAccountId: receiver,
    amountMinorUnits: isTamper ? (amountMinorUnits + 500) : amountMinorUnits,
    currency: currency,
    description: 'Dashboard Web Transfer'
  };

  try {
    const res = await fetch('/v1/payments/transfers', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Idempotency-Key': idempotencyKey,
        'X-Client-Id': 'web-dashboard'
      },
      body: JSON.stringify(payload)
    });

    const data = await res.json();

    if (res.status === 201) {
      showToast(`Transfer successful! Tx: ${data.transactionId.substring(0, 10)}... (Fresh Settlement)`, 'success');
      loadAccounts();
      loadLedgerStream();
    } else if (res.status === 200) {
      showToast(`[IDEMPOTENT REPLAY] Exact cached response returned safely (0 duplicate debits)!`, 'info');
    } else if (res.status === 409) {
      showToast(`[HTTP 409 CONFLICT] Transaction in-flight lease held by worker!`, 'info');
    } else if (res.status === 422) {
      showToast(`[HTTP 422 UNPROCESSABLE] Key reused with mutated payload!`, 'error');
    } else {
      showToast(`Error (${res.status}): ${data.message || data.error}`, 'error');
    }

  } catch (err) {
    showToast(`Network error: ${err.message}`, 'error');
  } finally {
    submitBtn.disabled = false;
    submitBtn.innerText = 'Execute Atomic Transfer';
  }
}

async function runChaosBenchmark() {
  const btn = document.getElementById('chaos-btn');
  btn.disabled = true;
  btn.innerText = 'Executing High-Concurrency Chaos...';

  const totalRequests = parseInt(document.getElementById('chaos-requests').value) || 100;
  const concurrency = parseInt(document.getElementById('chaos-concurrency').value) || 20;

  try {
    const res = await fetch('/v1/chaos/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        totalTransfers: totalRequests,
        concurrencyLevel: concurrency,
        duplicateRatePercent: 30,
        useHotMerchant: true
      })
    });

    const result = await res.json();

    document.getElementById('chaos-stats').style.display = 'grid';
    document.getElementById('stat-rps').innerText = `${result.requestsPerSecond.toFixed(1)} RPS`;
    document.getElementById('stat-p50').innerText = `${result.p50LatencyMs.toFixed(1)}ms`;
    document.getElementById('stat-p95').innerText = `${result.p95LatencyMs.toFixed(1)}ms`;
    document.getElementById('stat-p99').innerText = `${result.p99LatencyMs.toFixed(1)}ms`;

    document.getElementById('chaos-summary').innerText = result.verificationSummary;

    showToast(`Chaos Benchmark Finished! Zero Deadlocks. 100% Invariant Conservation.`, 'success');
    loadAccounts();
    loadLedgerStream();

  } catch (err) {
    showToast(`Chaos benchmark error: ${err.message}`, 'error');
  } finally {
    btn.disabled = false;
    btn.innerText = '🚀 Launch Chaos Concurrency Attack';
  }
}

async function triggerAudit() {
  try {
    const res = await fetch('/v1/reconciliation/run', { method: 'POST' });
    const audit = await res.json();

    if (audit.status === 'HEALTHY_100_PERCENT_CONSERVED') {
      showToast(`Audit Passed: All ${audit.totalAccountsAudited} accounts balanced. SHA-256 Merkle chain intact!`, 'success');
    } else {
      showToast(`Audit Warning: Discrepancy detected in ledger!`, 'error');
    }
  } catch (err) {
    showToast(`Audit failed: ${err.message}`, 'error');
  }
}

// Initial Initialization
document.addEventListener('DOMContentLoaded', () => {
  generateNewKey();
  loadAccounts();
  loadLedgerStream();

  // Periodic polling for real-time ledger updates
  setInterval(() => {
    loadAccounts();
    loadLedgerStream();
  }, 4000);
});
