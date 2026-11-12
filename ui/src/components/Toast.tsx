import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';

interface ToastContextValue {
  /** Announces a message through an aria-live region and shows it briefly. */
  notify: (message: string, kind?: 'success' | 'error') => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ id: number; message: string; kind: 'success' | 'error' } | null>(null);

  const notify = useCallback((message: string, kind: 'success' | 'error' = 'success') => {
    const id = Date.now();
    setToast({ id, message, kind });
    window.setTimeout(() => setToast((t) => (t?.id === id ? null : t)), 6000);
  }, []);

  return (
    <ToastContext.Provider value={{ notify }}>
      {children}
      {/* The live region stays mounted so assistive technology announces each new message. */}
      <div className="toast-region" role="status" aria-live="polite">
        {toast && <div className={`toast toast-${toast.kind}`}>{toast.message}</div>}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside ToastProvider');
  return ctx;
}
