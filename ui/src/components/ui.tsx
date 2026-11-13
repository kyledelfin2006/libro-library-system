import { useEffect, useRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthProvider';
import { can, type Capability } from '../auth/permissions';

/** Primary / secondary / danger button. Buttons default to type="button" so they never submit forms by accident. */
export function Button({
  variant = 'primary',
  type = 'button',
  className = '',
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'danger' }) {
  return <button type={type} className={`btn btn-${variant} ${className}`.trim()} {...rest} />;
}

export type ChipTone = 'neutral' | 'active' | 'overdue' | 'returned' | 'lost';
const CHIP_ICON: Record<ChipTone, string> = { neutral: '•', active: '◷', overdue: '!', returned: '✓', lost: '⚑' };

/** Status chip: always an icon plus a text label, so color is never the only signal. */
export function Chip({ tone = 'neutral', children }: { tone?: ChipTone; children: ReactNode }) {
  return (
    <span className={`chip chip-${tone}`}>
      <span aria-hidden="true">{CHIP_ICON[tone]}</span> {children}
    </span>
  );
}

/** Placeholder that holds the final layout's size so nothing jumps while loading. */
export function Skeleton({ width = '100%', height = 16 }: { width?: number | string; height?: number | string }) {
  return <div className="skeleton" style={{ width, height }} aria-hidden="true" />;
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="state state-empty">
      <h2>{title}</h2>
      {children && <p className="muted">{children}</p>}
      {action}
    </div>
  );
}

/** Error panel with a retry action for reads. Unexpected errors show the requestId so a user can quote it. */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const apiError = error instanceof ApiError ? error : null;
  const message =
    apiError?.status === 403 ? 'You do not have permission to view this.'
    : apiError?.status === 404 ? 'This could not be found.'
    : apiError?.status === 429 ? `Too many requests. Try again in ${apiError.retryAfterSeconds ?? 60} seconds.`
    : apiError && apiError.status < 500 && apiError.status !== 0 ? apiError.message
    : apiError?.status === 0 ? apiError.message
    : 'Something went wrong on our side.';
  const unexpected = !apiError || apiError.status >= 500;
  return (
    <div className="state state-error" role="alert">
      <p>{message}</p>
      {unexpected && apiError?.requestId && <p className="muted">Reference: {apiError.requestId.slice(0, 8)}</p>}
      {onRetry && (
        <Button variant="secondary" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

/** Renders children only when the signed-in role holds the capability. The server stays authoritative. */
export function Can({ capability, children, fallback = null }: { capability: Capability; children: ReactNode; fallback?: ReactNode }) {
  const { user } = useAuth();
  return <>{can(user?.userRole, capability) ? children : fallback}</>;
}

/** Page selector and page-size control for the API's page, size and totalElements contract. */
export function Pagination({
  page,
  size,
  total,
  onPage,
  onSize,
  sizes = [12, 24, 48, 100],
}: {
  page: number; // zero-based, as the API
  size: number;
  total: number;
  onPage: (page: number) => void;
  onSize: (size: number) => void;
  sizes?: number[];
}) {
  const pages = Math.max(1, Math.ceil(total / size));
  const from = total === 0 ? 0 : page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  return (
    <nav className="pagination" aria-label="Pagination">
      <span>
        Showing {from}-{to} of {total}
      </span>
      <Button variant="secondary" disabled={page <= 0} onClick={() => onPage(page - 1)}>
        Previous
      </Button>
      <span aria-live="polite">
        Page {page + 1} of {pages}
      </span>
      <Button variant="secondary" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>
        Next
      </Button>
      <label>
        Per page{' '}
        <select value={size} onChange={(e) => onSize(Number(e.target.value))}>
          {sizes.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </label>
    </nav>
  );
}

/**
 * Modal confirmation built on the native dialog element, which traps focus and closes on Escape. Used for delete,
 * mark-lost, and self-delete. The confirm button is disabled while the request is in flight.
 */
export function Dialog({
  open,
  title,
  children,
  confirmLabel,
  busy = false,
  danger = false,
  confirmDisabled = false,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel: string;
  busy?: boolean;
  danger?: boolean;
  confirmDisabled?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (open && !el.open) el.showModal?.();
    if (!open && el.open) el.close?.();
  }, [open]);
  return (
    <dialog ref={ref} className="dialog" aria-labelledby="dialog-title" onClose={onClose}>
      <h2 id="dialog-title">{title}</h2>
      <div>{children}</div>
      <div className="dialog-actions">
        <Button variant="secondary" onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button variant={danger ? 'danger' : 'primary'} onClick={onConfirm} disabled={busy || confirmDisabled}>
          {confirmLabel}
        </Button>
      </div>
    </dialog>
  );
}
