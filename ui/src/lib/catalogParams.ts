import { SORT_FIELDS, type CatalogQuery, type SortField } from '../api/books';

export const PAGE_SIZES = [12, 24, 48, 100];
export const DEFAULT_QUERY: CatalogQuery = { page: 0, size: 12, sort: 'title', dir: 'asc' };
export type View = 'grid' | 'list';

/** Reads catalog state from the URL, ignoring anything invalid, so a shared link or the back button restores it. */
export function parseCatalogParams(sp: URLSearchParams): { query: CatalogQuery; view: View } {
  const sortRaw = sp.get('sort') ?? '';
  const [field, direction] = sortRaw.split(',');
  const size = Number(sp.get('size'));
  const page = Number(sp.get('page'));
  const query: CatalogQuery = {
    page: Number.isInteger(page) && page >= 0 ? page : 0,
    size: PAGE_SIZES.includes(size) ? size : DEFAULT_QUERY.size,
    sort: (SORT_FIELDS as readonly string[]).includes(field ?? '') ? (field as SortField) : DEFAULT_QUERY.sort,
    dir: direction === 'desc' ? 'desc' : 'asc',
  };
  const text = (k: string) => sp.get(k)?.trim() || undefined;
  const title = text('q');
  const author = text('author');
  const genre = text('genre');
  const min = text('min');
  const max = text('max');
  if (title) query.title = title;
  if (author) query.author = author;
  if (genre) query.genre = genre;
  if (min) query.minReplacementCost = min;
  if (max) query.maxReplacementCost = max;
  if (sp.get('available') === 'true') query.available = true;
  return { query, view: sp.get('view') === 'list' ? 'list' : 'grid' };
}

/** Serializes catalog state to URL parameters, omitting defaults so URLs stay short. */
export function toCatalogParams(query: CatalogQuery, view: View): URLSearchParams {
  const sp = new URLSearchParams();
  if (query.title) sp.set('q', query.title);
  if (query.author) sp.set('author', query.author);
  if (query.genre) sp.set('genre', query.genre);
  if (query.minReplacementCost) sp.set('min', query.minReplacementCost);
  if (query.maxReplacementCost) sp.set('max', query.maxReplacementCost);
  if (query.available) sp.set('available', 'true');
  if (query.sort !== DEFAULT_QUERY.sort || query.dir !== DEFAULT_QUERY.dir) sp.set('sort', `${query.sort},${query.dir}`);
  if (query.size !== DEFAULT_QUERY.size) sp.set('size', String(query.size));
  if (query.page > 0) sp.set('page', String(query.page));
  if (view === 'list') sp.set('view', 'list');
  return sp;
}

export interface FilterChip {
  key: 'title' | 'author' | 'genre' | 'cost' | 'available';
  label: string;
}

/** Active filters as removable chips. Search text is shown too, so "clear all" has a visible target. */
export function activeChips(q: CatalogQuery): FilterChip[] {
  const chips: FilterChip[] = [];
  if (q.title) chips.push({ key: 'title', label: `Search: ${q.title}` });
  if (q.author) chips.push({ key: 'author', label: `Author: ${q.author}` });
  if (q.genre) chips.push({ key: 'genre', label: `Genre: ${q.genre}` });
  if (q.minReplacementCost || q.maxReplacementCost) {
    chips.push({ key: 'cost', label: `Cost: ${q.minReplacementCost ?? 'any'} to ${q.maxReplacementCost ?? 'any'}` });
  }
  if (q.available) chips.push({ key: 'available', label: 'Available only' });
  return chips;
}

/** Returns the query with one filter removed and pagination reset to the first page. */
export function removeChip(q: CatalogQuery, key: FilterChip['key']): CatalogQuery {
  const next: CatalogQuery = { ...q, page: 0 };
  if (key === 'title') delete next.title;
  if (key === 'author') delete next.author;
  if (key === 'genre') delete next.genre;
  if (key === 'cost') {
    delete next.minReplacementCost;
    delete next.maxReplacementCost;
  }
  if (key === 'available') delete next.available;
  return next;
}

export function clearFilters(q: CatalogQuery): CatalogQuery {
  return { page: 0, size: q.size, sort: q.sort, dir: q.dir };
}
