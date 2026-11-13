import { request } from './client';
import type { Page, UserRole } from './types';

/**
 * One fixed-format change record (`GET /app/audit`). The server writes the summary from a fixed action token, never
 * from user-entered text, so it is safe to show as plain text. `occurredAt` is Manila local time with no offset.
 */
export interface AuditEvent {
  id: number;
  occurredAt: string;
  actorUniversityId: string;
  /** A role name, or SYSTEM / ANONYMOUS / UNKNOWN for events with no signed-in staff or patron actor. */
  actorRole: string;
  targetRole?: UserRole | null;
  action: string;
  entityType: string;
  entityId: string;
  summary: string;
}

/** Entity types the server records today. */
export const AUDIT_ENTITY_TYPES = ['BOOK', 'LOAN', 'USER'] as const;

/** Filters the audit endpoint accepts. Dates are calendar days (YYYY-MM-DD) and are widened to whole days below. */
export interface AuditFilters {
  actor?: string;
  entityType?: string;
  actorRole?: string;
  targetRole?: UserRole;
  /** First day to include, YYYY-MM-DD. */
  from?: string;
  /** Last day to include, YYYY-MM-DD. */
  to?: string;
  page: number;
  size: number;
}

const DAY = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Builds the query string: empty filters are dropped, size is clamped to the API maximum, and a day range becomes the
 * server's local date-times (start of the first day to the last second of the final day) so the whole day is included.
 */
export function auditQueryString(f: AuditFilters): string {
  const p = new URLSearchParams({
    page: String(Math.max(0, f.page)),
    size: String(Math.min(100, Math.max(1, f.size))),
  });
  if (f.actor?.trim()) p.set('actor', f.actor.trim());
  if (f.entityType) p.set('entityType', f.entityType);
  if (f.actorRole) p.set('actorRole', f.actorRole);
  if (f.targetRole) p.set('targetRole', f.targetRole);
  if (f.from && DAY.test(f.from)) p.set('from', `${f.from}T00:00:00`);
  if (f.to && DAY.test(f.to)) p.set('to', `${f.to}T23:59:59`);
  return p.toString();
}

export const listAudit = (f: AuditFilters) => request<Page<AuditEvent>>('GET', `/app/audit?${auditQueryString(f)}`);
