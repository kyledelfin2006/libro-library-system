import type { ReactNode } from 'react';

export interface SlotLoan {
  title: string;
  /** Whole days until due; negative when the server reports the loan overdue. */
  daysLeft: number;
  overdue: boolean;
}

export const MAX_ACTIVE_LOANS = 3;

/** Loan slots diagram: three slots that mirror the server rule (3 active loans; an overdue loan blocks checkout). */
export function LoanSlots({ loans }: { loans: SlotLoan[] }) {
  const blocked = loans.some((l) => l.overdue);
  const slots: ReactNode[] = [];
  for (let i = 0; i < MAX_ACTIVE_LOANS; i++) {
    const loan = loans[i];
    if (loan) {
      // Amber under 3 days, coral when overdue; the label repeats the state in words.
      const tone = loan.overdue ? 'lost' : loan.daysLeft < 3 ? 'overdue' : 'active';
      const label = loan.overdue ? `${Math.abs(loan.daysLeft)} days overdue` : `due in ${loan.daysLeft} days`;
      slots.push(
        <li key={i} className="slot slot-filled">
          <strong>{loan.title}</strong>
          <span className={`slot-bar slot-${tone}`} aria-hidden="true" />
          <span>{label}</span>
        </li>,
      );
    } else {
      slots.push(
        <li key={i} className="slot slot-free">
          {blocked && <span aria-hidden="true">🔒 </span>}Free
        </li>,
      );
    }
  }
  return (
    <section aria-label="Loan slots">
      <ul className="slots">{slots}</ul>
      <p className="muted">
        {blocked
          ? 'Return your overdue book to borrow.'
          : `${loans.length} of ${MAX_ACTIVE_LOANS} loan slots used.`}
      </p>
    </section>
  );
}

/** Lifecycle diagram: checkout, ACTIVE, then RETURNED or LOST. Overdue is a state of ACTIVE, not a status. */
export function LoanLifecycle() {
  return (
    <figure className="diagram">
      <svg viewBox="0 0 520 110" role="img" aria-labelledby="lifecycle-desc" className="chart-svg">
        <title id="lifecycle-desc">
          Checkout creates an ACTIVE loan due 14 days later. The borrower returns it (RETURNED) or staff records it as lost (LOST).
        </title>
        {[
          [10, 'Checkout', 'var(--color-border-input)'],
          [150, 'ACTIVE', 'var(--status-active)'],
          [350, 'RETURNED', 'var(--status-returned)'],
          [350, 'LOST', 'var(--status-lost)'],
        ].map(([x, label, color], i) => (
          <g key={String(label)}>
            <rect x={Number(x)} y={i === 3 ? 66 : i === 2 ? 8 : 36} width="120" height="36" rx="6" fill="none" stroke={String(color)} strokeWidth="2" />
            <text x={Number(x) + 60} y={(i === 3 ? 66 : i === 2 ? 8 : 36) + 23} textAnchor="middle" className="chart-axis">
              {String(label)}
            </text>
          </g>
        ))}
        <path d="M130 54 H150 M270 54 L310 54 L310 26 H350 M310 54 L310 84 H350" fill="none" stroke="var(--color-text-muted)" strokeWidth="1.5" />
      </svg>
      <figcaption className="muted">Due 14 calendar days after checkout. Overdue is a state of ACTIVE, not a separate status.</figcaption>
    </figure>
  );
}
