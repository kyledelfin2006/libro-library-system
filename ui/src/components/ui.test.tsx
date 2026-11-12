import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../api/client';
import { HBarChart, LineChart, StackedBar } from './charts';
import { LoanLifecycle, LoanSlots } from './LoanSlots';
import { Chip, EmptyState, ErrorState, Pagination } from './ui';

describe('shared components', () => {
  it('chip pairs an icon with its label', () => {
    render(<Chip tone="overdue">Overdue</Chip>);
    expect(screen.getByText('Overdue').closest('span')).toHaveTextContent('! Overdue');
  });

  it('error state shows the request reference only for unexpected errors and offers retry', async () => {
    const retry = vi.fn();
    const { rerender } = render(<ErrorState error={new ApiError(500, null, 'x', {}, '3f2b8c1e-aaaa')} onRetry={retry} />);
    expect(screen.getByText(/Reference: 3f2b8c1e/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(retry).toHaveBeenCalled();
    rerender(<ErrorState error={new ApiError(403, null, 'x')} />);
    expect(screen.getByRole('alert')).toHaveTextContent('permission');
    expect(screen.queryByText(/Reference/)).toBeNull();
  });

  it('empty state renders its recovery action', () => {
    render(<EmptyState title="No books match these filters." action={<button>Clear filters</button>} />);
    expect(screen.getByRole('button', { name: 'Clear filters' })).toBeInTheDocument();
  });

  it('pagination reports the range and disables the ends', async () => {
    const onPage = vi.fn();
    render(<Pagination page={0} size={12} total={87} onPage={onPage} onSize={() => {}} />);
    expect(screen.getByText('Showing 1-12 of 87')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(onPage).toHaveBeenCalledWith(1);
  });
});

describe('charts and diagrams', () => {
  it('every chart can be viewed as a table with the same numbers', async () => {
    const data = [{ label: 'Dystopian', value: 18 }, { label: 'Sci-fi', value: 14 }];
    render(<><HBarChart data={data} caption="Top genres" /><StackedBar data={data} caption="Mix" /><LineChart data={data} caption="Weekly" /></>);
    for (const button of screen.getAllByRole('button', { name: 'View as table' })) await userEvent.click(button);
    expect(screen.getAllByRole('table')).toHaveLength(3);
    expect(screen.getAllByRole('cell', { name: '18' })).toHaveLength(3);
  });

  it('shows an explanation instead of an empty chart', () => {
    render(<HBarChart data={[]} caption="Nothing" />);
    expect(screen.getByText('No data to show.')).toBeInTheDocument();
  });

  it('loan slots lock free slots while a loan is overdue', () => {
    render(<LoanSlots loans={[{ title: 'Dune', daysLeft: -2, overdue: true }]} />);
    expect(screen.getByText('2 days overdue')).toBeInTheDocument();
    expect(screen.getByText('Return your overdue book to borrow.')).toBeInTheDocument();
    expect(screen.getAllByText(/Free/)).toHaveLength(2);
  });

  it('loan slots count used slots when nothing is overdue', () => {
    render(<LoanSlots loans={[{ title: '1984', daysLeft: 9, overdue: false }]} />);
    expect(screen.getByText('1 of 3 loan slots used.')).toBeInTheDocument();
  });

  it('lifecycle diagram has a text alternative', () => {
    render(<LoanLifecycle />);
    expect(screen.getByRole('img')).toHaveAccessibleName(/ACTIVE loan due 14 days later/);
  });
});
