import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getGenres, queryBooks, type CatalogQuery, type SortField } from '../api/books';
import { useAuth } from '../auth/AuthProvider';
import { can } from '../auth/permissions';
import { BookCard, BookRow } from '../components/BookViews';
import { TextField } from '../components/TextField';
import { Button, EmptyState, ErrorState, Pagination, Skeleton } from '../components/ui';
import {
  PAGE_SIZES,
  activeChips,
  clearFilters,
  parseCatalogParams,
  removeChip,
  toCatalogParams,
  type View,
} from '../lib/catalogParams';
import { useDebouncedValue } from '../lib/useDebouncedValue';

const SORTS: { value: string; label: string }[] = [
  { value: 'title,asc', label: 'Title A-Z' },
  { value: 'title,desc', label: 'Title Z-A' },
  { value: 'author,asc', label: 'Author A-Z' },
  { value: 'genre,asc', label: 'Genre A-Z' },
  { value: 'replacementCost,asc', label: 'Cost low to high' },
  { value: 'replacementCost,desc', label: 'Cost high to low' },
  { value: 'id,asc', label: 'Oldest first' },
];

/**
 * Catalog browse and search. All state (search, filters, sort, page, view) lives in the URL, so a link can be
 * shared and the back button restores it. Search waits 300 ms after typing, and any filter change returns to page one.
 */
export function Catalog() {
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const { query, view } = useMemo(() => parseCatalogParams(searchParams), [searchParams]);
  const staff = can(user?.userRole, 'manageBooks');

  const update = (next: CatalogQuery, nextView: View = view, replace = false) =>
    setSearchParams(toCatalogParams(next, nextView), { replace });

  // Search box: local text, debounced into the URL. External changes (chip removed, back button) flow back in.
  const [text, setText] = useState(query.title ?? '');
  const debounced = useDebouncedValue(text, 300).trim();
  useEffect(() => {
    if (debounced !== (query.title ?? '')) {
      const next: CatalogQuery = { ...query, page: 0 };
      if (debounced) next.title = debounced;
      else delete next.title;
      update(next, view, true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only a settled search term should write to the URL
  }, [debounced]);
  useEffect(() => {
    if ((query.title ?? '') !== debounced) setText(query.title ?? '');
    // eslint-disable-next-line react-hooks/exhaustive-deps -- sync only when the URL changes underneath the box
  }, [query.title]);

  const books = useQuery({
    queryKey: ['books', query],
    queryFn: () => queryBooks(query),
    placeholderData: keepPreviousData,
  });
  const genres = useQuery({ queryKey: ['genres'], queryFn: getGenres, staleTime: 5 * 60_000 });

  const [filtersOpen, setFiltersOpen] = useState(false);
  const chips = activeChips(query);
  const page = books.data;

  return (
    <section>
      <header className="page-header">
        <h1>Catalog</h1>
        <div className="view-toggle" role="group" aria-label="View">
          {(['grid', 'list'] as const).map((v) => (
            <Button key={v} variant="secondary" aria-pressed={view === v} onClick={() => update(query, v)}>
              {v === 'grid' ? 'Grid' : 'List'}
            </Button>
          ))}
        </div>
      </header>

      <div className="toolbar">
        <div className="field toolbar-search">
          <label htmlFor="catalog-search">Search titles</label>
          <input
            id="catalog-search"
            type="search"
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Search by title"
          />
        </div>
        <Button variant="secondary" aria-expanded={filtersOpen} onClick={() => setFiltersOpen((o) => !o)}>
          Filters{chips.filter((c) => c.key !== 'title').length > 0 ? ` (${chips.filter((c) => c.key !== 'title').length})` : ''}
        </Button>
        <div className="field">
          <label htmlFor="catalog-sort">Sort</label>
          <select
            id="catalog-sort"
            value={`${query.sort},${query.dir}`}
            onChange={(e) => {
              const [sort, dir] = e.target.value.split(',') as [SortField, 'asc' | 'desc'];
              update({ ...query, sort, dir, page: 0 });
            }}
          >
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {filtersOpen && (
        <FilterPanel
          key={searchParams.toString()}
          query={query}
          genres={Object.keys(genres.data ?? {}).sort()}
          onApply={(next) => {
            update(next);
            setFiltersOpen(false);
          }}
        />
      )}

      {chips.length > 0 && (
        <div className="chips" aria-label="Active filters">
          {chips.map((c) => (
            <button key={c.key} type="button" className="chip chip-removable" onClick={() => update(removeChip(query, c.key))}>
              {c.label} <span aria-hidden="true">×</span>
              <span className="visually-hidden"> remove filter</span>
            </button>
          ))}
          <Button variant="secondary" onClick={() => update(clearFilters(query))}>
            Clear all
          </Button>
        </div>
      )}

      <p className="result-count" role="status" aria-live="polite">
        {page ? `${page.totalElements} ${page.totalElements === 1 ? 'book' : 'books'}` : ' '}
      </p>

      {books.isError && !page ? (
        <ErrorState error={books.error} onRetry={() => void books.refetch()} />
      ) : books.isPending ? (
        <ul className="book-grid" aria-busy="true" aria-label="Loading books">
          {Array.from({ length: query.size > 12 ? 12 : query.size }, (_, i) => (
            <li key={i}>
              <Skeleton height={240} />
            </li>
          ))}
        </ul>
      ) : page && page.content.length === 0 ? (
        <EmptyState
          title="No books match these filters."
          action={chips.length > 0 ? <Button onClick={() => update(clearFilters(query))}>Clear filters</Button> : undefined}
        >
          Clear a filter or try a different word.
        </EmptyState>
      ) : page ? (
        <>
          {view === 'grid' ? (
            <ul className="book-grid">
              {page.content.map((b) => (
                <BookCard key={b.id} book={b} showCost={staff} />
              ))}
            </ul>
          ) : (
            <table className="book-table">
              <thead>
                <tr>
                  <th scope="col"><span className="visually-hidden">Cover</span></th>
                  <th scope="col">Title</th>
                  <th scope="col">Author</th>
                  <th scope="col">Genre</th>
                  {staff && <th scope="col">Cost</th>}
                  <th scope="col">Status</th>
                </tr>
              </thead>
              <tbody>
                {page.content.map((b) => (
                  <BookRow key={b.id} book={b} showCost={staff} />
                ))}
              </tbody>
            </table>
          )}
          {books.isError && <ErrorState error={books.error} onRetry={() => void books.refetch()} />}
          <Pagination
            page={page.number}
            size={page.size}
            total={page.totalElements}
            sizes={PAGE_SIZES}
            onPage={(p) => update({ ...query, page: p })}
            onSize={(s) => update({ ...query, size: s, page: 0 })}
          />
        </>
      ) : null}
    </section>
  );
}

/** Filters panel with its own draft state; applying writes everything to the URL at once and returns to page one. */
function FilterPanel({
  query,
  genres,
  onApply,
}: {
  query: CatalogQuery;
  genres: string[];
  onApply: (q: CatalogQuery) => void;
}) {
  const [author, setAuthor] = useState(query.author ?? '');
  const [genre, setGenre] = useState(query.genre ?? '');
  const [min, setMin] = useState(query.minReplacementCost ?? '');
  const [max, setMax] = useState(query.maxReplacementCost ?? '');
  const [available, setAvailable] = useState(query.available ?? false);
  const [error, setError] = useState<string | undefined>();

  function submit(e: FormEvent) {
    e.preventDefault();
    if (min && max && Number(min) > Number(max)) {
      setError('The minimum cost cannot be higher than the maximum.');
      return;
    }
    const next: CatalogQuery = { ...query, page: 0 };
    for (const [key, value] of [['author', author], ['genre', genre], ['minReplacementCost', min], ['maxReplacementCost', max]] as const) {
      if (value.trim()) next[key] = value.trim();
      else delete next[key];
    }
    if (available) next.available = true;
    else delete next.available;
    onApply(next);
  }

  return (
    <form className="filter-panel" onSubmit={submit} aria-label="Filters">
      <TextField label="Author" value={author} onChange={(e) => setAuthor(e.target.value)} />
      <TextField label="Genre" value={genre} onChange={(e) => setGenre(e.target.value)} list="genre-options" />
      <datalist id="genre-options">
        {genres.map((g) => (
          <option key={g} value={g} />
        ))}
      </datalist>
      <TextField label="Minimum cost" inputMode="decimal" value={min} onChange={(e) => setMin(e.target.value)} />
      <TextField label="Maximum cost" inputMode="decimal" value={max} onChange={(e) => setMax(e.target.value)} error={error} />
      <label className="checkbox">
        <input type="checkbox" checked={available} onChange={(e) => setAvailable(e.target.checked)} /> Available only
      </label>
      <Button type="submit">Apply filters</Button>
    </form>
  );
}
