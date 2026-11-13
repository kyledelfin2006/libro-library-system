import { request } from './client';
import type { BookResponse, Page } from './types';

/** Catalog filters. Text filters are case-insensitive substrings on the server; cost bounds are inclusive. */
export interface CatalogFilters {
  title?: string;
  author?: string;
  genre?: string;
  minReplacementCost?: string;
  maxReplacementCost?: string;
  available?: boolean;
}

/** The only sort fields the API accepts (anything else returns 400), so the UI never offers another. */
export const SORT_FIELDS = ['id', 'title', 'author', 'genre', 'replacementCost'] as const;
export type SortField = (typeof SORT_FIELDS)[number];

export interface CatalogQuery extends CatalogFilters {
  page: number; // zero-based
  size: number;
  sort: SortField;
  dir: 'asc' | 'desc';
}

/** Builds the /app/books/query string, leaving out empty filters. */
export function toQueryString(q: CatalogQuery): string {
  const p = new URLSearchParams();
  for (const key of ['title', 'author', 'genre', 'minReplacementCost', 'maxReplacementCost'] as const) {
    const value = q[key]?.trim();
    if (value) p.set(key, value);
  }
  if (q.available) p.set('available', 'true');
  p.set('page', String(q.page));
  p.set('size', String(q.size));
  p.set('sort', `${q.sort},${q.dir}`);
  return p.toString();
}

export const queryBooks = (q: CatalogQuery) =>
  request<Page<BookResponse>>('GET', `/app/books/query?${toQueryString(q)}`);

export const getBook = (id: number) => request<BookResponse>('GET', `/app/books/${id}`);

export const getGenres = () => request<Record<string, number>>('GET', '/app/books/genre');
