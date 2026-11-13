import { Link } from 'react-router-dom';
import type { BookResponse } from '../api/types';
import { BookBadge } from './BookBadge';
import { BookCover } from './BookCover';
import { Chip } from './ui';

/** Replacement cost with two decimals and no currency symbol until the owner chooses one (plan section 5.8). */
export function formatCost(value: number | undefined): string {
  return value === undefined ? 'Not recorded' : value.toFixed(2);
}

/** Grid card: generated cover plus real text (title, author, genre, status), so the cover stays decoration. */
export function BookCard({ book, showCost }: { book: BookResponse; showCost: boolean }) {
  return (
    <li className="book-card">
      <Link to={`/catalog/${book.id}`} className="book-card-link">
        <BookCover book={cover(book)} size="md" />
        <span className="book-title">{book.title}</span>
        <span className="muted">{book.author}</span>
      </Link>
      <div className="book-meta">
        <Chip>{book.genre}</Chip>
        <BookBadge book={book} />
        {showCost && <span className="muted">Cost {formatCost(book.replacementCost)}</span>}
      </div>
    </li>
  );
}

/** List row using the small "spine" cover. */
export function BookRow({ book, showCost }: { book: BookResponse; showCost: boolean }) {
  return (
    <tr>
      <td className="cell-cover">
        <BookCover book={cover(book)} size="sm" />
      </td>
      <th scope="row" data-label="Title">
        <Link to={`/catalog/${book.id}`}>{book.title}</Link>
      </th>
      <td data-label="Author">{book.author}</td>
      <td data-label="Genre">{book.genre}</td>
      {showCost && <td data-label="Cost">{formatCost(book.replacementCost)}</td>}
      <td data-label="Status">
        <BookBadge book={book} />
      </td>
    </tr>
  );
}

/** Narrows a response to the fields the cover needs, defaulting the optional status fields. */
export function cover(book: BookResponse) {
  return {
    id: book.id,
    title: book.title,
    author: book.author,
    genre: book.genre,
    copyStatus: book.copyStatus ?? ('IN_COLLECTION' as const),
    available: book.available ?? false,
  };
}
