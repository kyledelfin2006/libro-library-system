import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { AUDIT_ENTITY_TYPES, listAudit, type AuditEvent } from '../api/audit';
import { ApiError } from '../api/client';
import type { UserRole } from '../api/types';
import { useAuth } from '../auth/AuthProvider';
import { ROLE_LABEL } from '../auth/permissions';
import { Button, EmptyState, ErrorState, Pagination, Skeleton } from '../components/ui';
import { actionLabel, actorRoleLabel, actorRoleOptions, entityLabel, isBackwardsRange, targetRoleOptions } from '../lib/audit';
import { formatDateTime } from '../lib/dates';

const PAGE_SIZES = [12, 24, 48, 100];

/**
 * Change history for library staff. Filters live in the URL. The server scopes results by role, so a library assistant is
 * never offered filters for librarian or assistant events, and a 403 on a filter reads as a normal denial.
 */
export function Audit() {
  const { user } = useAuth();
  const [sp, setSp] = useSearchParams();
  const role = user?.userRole;

  const actor = sp.get('actor') ?? '';
  const entityType = AUDIT_ENTITY_TYPES.find((t) => t === sp.get('entityType')) ?? '';
  const actorRole = actorRoleOptions(role).find((r) => r === sp.get('actorRole')) ?? '';
  const targetRole = targetRoleOptions(role).find((r) => r === sp.get('targetRole')) ?? '';
  const from = sp.get('from') ?? '';
  const to = sp.get('to') ?? '';
  const page = Math.max(0, Number(sp.get('page')) || 0);
  const size = PAGE_SIZES.includes(Number(sp.get('size'))) ? Number(sp.get('size')) : 12;
  const openId = Number(sp.get('open')) || null;

  const [actorDraft, setActorDraft] = useState(actor);
  const backwards = isBackwardsRange(from, to);

  /** Writes filters to the URL; any filter change returns to page one and closes the panel. */
  const setParams = (changes: Record<string, string | null>, keepPage = false) => {
    const next = new URLSearchParams(sp);
    for (const [k, v] of Object.entries(changes)) {
      if (v === null || v === '') next.delete(k);
      else next.set(k, v);
    }
    if (!keepPage) { next.delete('page'); if (!('open' in changes)) next.delete('open'); }
    setSp(next);
  };

  const filters = {
    ...(actor ? { actor } : {}), ...(entityType ? { entityType } : {}), ...(actorRole ? { actorRole } : {}),
    ...(targetRole ? { targetRole: targetRole as UserRole } : {}), ...(from ? { from } : {}), ...(to ? { to } : {}), page, size,
  };
  const events = useQuery({
    queryKey: ['audit', filters],
    queryFn: () => listAudit(filters),
    enabled: !backwards,
    placeholderData: keepPreviousData,
  });
  const selected = events.data?.content.find((e) => e.id === openId) ?? null;
  const hasFilters = !!(actor || entityType || actorRole || targetRole || from || to);

  return (
    <section>
      <h1>Audit log</h1>
      <p className="muted">
        A fixed-format record of who changed what. {role === 'LIBRARIAN'
          ? 'As librarian you see every event.'
          : 'You see student and faculty account events plus book and loan events.'} Times are Manila time.
      </p>
      <form
        className="filter-panel"
        onSubmit={(e) => {
          e.preventDefault();
          setParams({ actor: actorDraft.trim() });
        }}
      >
        <div className="field">
          <label htmlFor="audit-actor">Actor ID</label>
          <input id="audit-actor" value={actorDraft} onChange={(e) => setActorDraft(e.target.value)} placeholder="2025-4321" inputMode="numeric" />
        </div>
        <div className="field">
          <label htmlFor="audit-actor-role">Actor role</label>
          <select id="audit-actor-role" value={actorRole} onChange={(e) => setParams({ actorRole: e.target.value })}>
            <option value="">Any</option>
            {actorRoleOptions(role).map((r) => <option key={r} value={r}>{actorRoleLabel(r)}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor="audit-entity">Entity</label>
          <select id="audit-entity" value={entityType} onChange={(e) => setParams({ entityType: e.target.value })}>
            <option value="">Any</option>
            {AUDIT_ENTITY_TYPES.map((t) => <option key={t} value={t}>{entityLabel(t)}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor="audit-target-role">Account role affected</label>
          <select id="audit-target-role" value={targetRole} onChange={(e) => setParams({ targetRole: e.target.value })}>
            <option value="">Any</option>
            {targetRoleOptions(role).map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor="audit-from">From</label>
          <input id="audit-from" type="date" value={from} max={to || undefined} onChange={(e) => setParams({ from: e.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="audit-to">To</label>
          <input id="audit-to" type="date" value={to} min={from || undefined} onChange={(e) => setParams({ to: e.target.value })} />
        </div>
        <div className="form-actions">
          <Button type="submit" variant="secondary">Search</Button>
          {hasFilters && (
            <Button variant="secondary" onClick={() => { setActorDraft(''); setSp(new URLSearchParams()); }}>Clear</Button>
          )}
        </div>
      </form>
      {backwards && <p role="alert" className="field-error">The start date must be on or before the end date.</p>}

      <div className="split">
        <div>
          {backwards ? null : events.isPending ? (
            <Skeleton height={240} />
          ) : events.isError && !events.data ? (
            <AuditError error={events.error} onRetry={() => void events.refetch()} />
          ) : events.data && events.data.content.length === 0 ? (
            <EmptyState title={hasFilters ? 'No events match these filters.' : 'No events recorded yet.'}>
              {hasFilters ? 'Clear a filter or widen the date range.' : 'Changes to books, loans and accounts appear here.'}
            </EmptyState>
          ) : events.data ? (
            <>
              <table className="loan-table record-cards">
                <thead>
                  <tr>
                    <th scope="col">When</th>
                    <th scope="col">Actor</th>
                    <th scope="col">Action</th>
                    <th scope="col" className="col-extra">Entity</th>
                    <th scope="col"><span className="visually-hidden">Details</span></th>
                  </tr>
                </thead>
                <tbody>
                  {events.data.content.map((e) => (
                    <tr key={e.id} aria-selected={e.id === openId} className={e.id === openId ? 'row-selected' : undefined}>
                      <th scope="row" data-label="When">{formatDateTime(e.occurredAt)}</th>
                      <td data-label="Actor">{e.actorUniversityId} <span className="muted">({actorRoleLabel(e.actorRole)})</span></td>
                      <td data-label="Action">{actionLabel(e.action, e.summary)}</td>
                      <td data-label="Entity" className="col-extra">{entityLabel(e.entityType)} {e.entityId}</td>
                      <td>
                        <Button variant="secondary" onClick={() => setParams({ open: String(e.id) }, true)}>
                          Details<span className="visually-hidden"> for event {e.id}</span>
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <Pagination
                page={events.data.number}
                size={events.data.size}
                total={events.data.totalElements}
                onPage={(p) => setParams({ page: p > 0 ? String(p) : null }, true)}
                onSize={(s) => setParams({ size: s === 12 ? null : String(s) })}
              />
            </>
          ) : null}
        </div>

        {selected && <EventPanel event={selected} onClose={() => setParams({ open: null }, true)} />}
      </div>
    </section>
  );
}

/** Side panel with every field of one event, including the server's summary. */
function EventPanel({ event: e, onClose }: { event: AuditEvent; onClose: () => void }) {
  return (
    <aside className="side-panel" aria-label={`Event ${e.id} details`}>
      <h2>Event {e.id}</h2>
      <dl className="catalog-card">
        <div><dt>When</dt><dd>{formatDateTime(e.occurredAt)}</dd></div>
        <div><dt>Actor</dt><dd>{e.actorUniversityId} ({actorRoleLabel(e.actorRole)})</dd></div>
        <div><dt>Action</dt><dd>{actionLabel(e.action, e.summary)}</dd></div>
        <div><dt>Entity</dt><dd>{entityLabel(e.entityType)} {e.entityId}</dd></div>
        {e.targetRole && <div><dt>Account role</dt><dd>{ROLE_LABEL[e.targetRole]}</dd></div>}
        <div><dt>Summary</dt><dd>{e.summary}</dd></div>
      </dl>
      <div className="form-actions">
        <Button variant="secondary" onClick={onClose}>Close</Button>
      </div>
    </aside>
  );
}

/** A 403 on a filter is a normal denial (out-of-scope events), not a failure to retry. */
function AuditError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  if (error instanceof ApiError && error.status === 403) {
    return <EmptyState title="You do not have permission to view those events.">Remove the role filter and try again.</EmptyState>;
  }
  return <ErrorState error={error} onRetry={onRetry} />;
}
