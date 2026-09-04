import React from 'react';
import { CheckCircle2, AlertTriangle, AlertCircle, Info, X } from 'lucide-react';

export interface ToastMessage {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  title?: string;
  message: string;
}

interface ToastContainerProps {
  toasts: ToastMessage[];
  onDismiss: (id: string) => void;
}

export const ToastContainer: React.FC<ToastContainerProps> = ({ toasts, onDismiss }) => {
  if (toasts.length === 0) return null;

  return (
    <div className="toast-viewport">
      {toasts.map((toast) => (
        <div key={toast.id} className={`toast-item ${toast.type}`}>
          <div style={{ flexShrink: 0, marginTop: 2 }}>
            {toast.type === 'success' && <CheckCircle2 size={18} color="var(--accent-success)" />}
            {toast.type === 'error' && <AlertCircle size={18} color="var(--accent-danger)" />}
            {toast.type === 'warning' && <AlertTriangle size={18} color="var(--accent-warning)" />}
            {toast.type === 'info' && <Info size={18} color="var(--accent-primary)" />}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            {toast.title && (
              <div style={{ fontWeight: 600, color: 'var(--text-primary)', marginBottom: 2 }}>
                {toast.title}
              </div>
            )}
            <div style={{ color: 'var(--text-secondary)', fontSize: '0.8125rem', wordBreak: 'break-word' }}>
              {toast.message}
            </div>
          </div>
          <button
            onClick={() => onDismiss(toast.id)}
            style={{
              background: 'transparent',
              border: 'none',
              color: 'var(--text-muted)',
              cursor: 'pointer',
              padding: 2,
              display: 'flex',
            }}
          >
            <X size={14} />
          </button>
        </div>
      ))}
    </div>
  );
};
