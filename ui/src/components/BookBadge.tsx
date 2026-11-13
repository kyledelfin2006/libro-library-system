import type { BookResponse } from '../api/types';
import { Chip } from './ui';

/** Availability chip: icon plus text. Availability is advisory; checkout is still decided by the server. */
export function BookBadge({ book }: { book: Pick<BookResponse, 'copyStatus' | 'available'> }) {
  if (book.copyStatus === 'LOST') return <Chip tone="lost">Lost</Chip>;
  return book.available ? <Chip tone="returned">Available</Chip> : <Chip tone="active">On loan</Chip>;
}
