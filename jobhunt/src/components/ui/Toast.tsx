import { createContext, useCallback, useContext, useMemo, useState, ReactNode } from 'react';
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';

export type ToastVariant = 'success' | 'error' | 'info';

interface Toast {
  id: number;
  message: string;
  variant: ToastVariant;
}

interface ToastContextValue {
  showToast: (message: string, variant?: ToastVariant) => void;
}

const ToastContext = createContext<ToastContextValue | undefined>(undefined);

const VARIANT_STYLES: Record<ToastVariant, { container: string; icon: typeof Info }> = {
  success: { container: 'bg-white border-green-200', icon: CheckCircle2 },
  error: { container: 'bg-white border-red-200', icon: AlertCircle },
  info: { container: 'bg-white border-gray-200', icon: Info },
};

const ICON_COLOURS: Record<ToastVariant, string> = {
  success: 'text-green-600',
  error: 'text-red-600',
  info: 'text-gray-500',
};

const DISMISS_AFTER_MS = 5000;

/**
 * Minimal toast system. Deliberately dependency-free and announced through an
 * `aria-live` region so screen readers read messages as they appear.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const showToast = useCallback((message: string, variant: ToastVariant = 'info') => {
    const id = Date.now() + Math.random();
    setToasts((current) => [...current, { id, message, variant }]);
    window.setTimeout(() => {
      setToasts((current) => current.filter((toast) => toast.id !== id));
    }, DISMISS_AFTER_MS);
  }, []);

  const dismiss = (id: number) => setToasts((current) => current.filter((toast) => toast.id !== id));

  const value = useMemo(() => ({ showToast }), [showToast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        aria-live="polite"
        aria-atomic="false"
        className="fixed bottom-4 right-4 z-[60] flex w-[calc(100%-2rem)] max-w-sm flex-col gap-2 sm:w-full"
      >
        {toasts.map((toast) => {
          const { container, icon: Icon } = VARIANT_STYLES[toast.variant];
          return (
            <div
              key={toast.id}
              role="status"
              className={`flex items-start gap-3 rounded-xl border shadow-lg px-4 py-3 ${container}`}
            >
              <Icon className={`w-5 h-5 flex-shrink-0 mt-0.5 ${ICON_COLOURS[toast.variant]}`} />
              <p className="flex-1 text-sm text-gray-800 break-words">{toast.message}</p>
              <button
                onClick={() => dismiss(toast.id)}
                aria-label="Dismiss notification"
                className="p-1 -m-1 text-gray-400 hover:text-gray-600 rounded transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (context === undefined) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
}
