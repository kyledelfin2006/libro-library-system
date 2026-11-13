import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type FormEvent } from 'react';
import { getGenres, queryBooks, type CatalogQuery } from '../api/books';
import { ApiError } from '../api/client';
import { createBook, deleteBook, replaceBook } from '../api/staff';
import type { BookRequest, BookResponse } from '../api/types';
import { BookBadge } from '../components/BookBadge';
import { BookCover } from '../components/BookCover';
import { cover, formatCost } from '../components/BookViews';
import { TextField } from '../components/TextField';
import { useToast } from '../components/Toast';
import { Button, Dialog, EmptyState, ErrorState, Pagination, Skeleton } from '../components/ui';
import { describeError, useFocusFirstInvalid } from '../lib/forms';
import { useDebouncedValue } from '../lib/useDebouncedValue';

/** Inventory (library staff): search and filter copies, add, edit, and delete. Each row is one physical copy. */
export function Inventory() {
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const [text, setText] = useState('');
  const title = useDebouncedValue(text, 300).trim();
  const [genre, setGenre] = useState('');
  const [min, setMin] = useState('');
  const [max, setMax] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(12);
  const [editing, setEditing] = useState<BookResponse | 'new' | null>(null);
  const [deleting, setDeleting] = useState<BookResponse | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const query: CatalogQuery = {
    page, size, sort: 'title', dir: 'asc',
    ...(title ? { title } : {}), ...(genre ? { genre } : {}),
    ...(min ? { minReplacementCost: min } : {}), ...(max ? { maxReplacementCost: max } : {}),
  };
  const books = useQuery({ queryKey: ['books', 'inventory', query], queryFn: () => queryBooks(query), placeholderData: keepPreviousData });
  const genres = useQuery({ queryKey: ['genres'], queryFn: getGenres });
  const refresh = () => Promise.all(['books', 'book', 'genres', 'bookStats'].map((k) => queryClient.invalidateQueries({ queryKey: [k] })));
  const resetPage = <T,>(set: (v: T) => void) => (v: T) => { set(v); setPage(0); };

  const remove = useMutation({
    mutationFn: (b: BookResponse) => deleteBook(b.id),
    onSuccess: (_d, b) => {
      notify(`Deleted ${b.title}.`);
      setDeleting(null);
    },
    onError: (e) =>
      setDeleteError(
        e instanceof ApiError && e.code === 'RESOURCE_HAS_HISTORY' ? 'This copy has loan history and must be kept.' : describeError(e),
      ),
    onSettled: () => refresh(),
  });

  const costError = min && max && Number(min) > Number(max) ? 'The minimum cost cannot be higher than the maximum.' : undefined;

  return (
    <section>
      <header className="page-header">
        <h1>Inventory</h1>
        <Button onClick={() => setEditing('new')}>Add copy</Button>
      </header>

      <div className="toolbar">
        <div className="field toolbar-search">
          <label htmlFor="inv-search">Search titles</label>
          <input id="inv-search" type="search" value={text} onChange={(e) => { setText(e.target.value); setPage(0); }} />
        </div>
        <div className="field">
          <label htmlFor="inv-genre">Genre</label>
          <select id="inv-genre" value={genre} onChange={(e) => resetPage(setGenre)(e.target.value)}>
            <option value="">All</option>
            {Object.keys(genres.data ?? {}).sort().map((g) => <option key={g} value={g}>{g}</option>)}
          </select>
        </div>
        <TextField label="Min cost" inputMode="decimal" value={min} onChange={(e) => resetPage(setMin)(e.target.value)} />
        <TextField label="Max cost" inputMode="decimal" value={max} onChange={(e) => resetPage(setMax)(e.target.value)} error={costError} />
      </div>

      {books.isPending ? (
        <Skeleton height={240} />
      ) : books.isError && !books.data ? (
        <ErrorState error={books.error} onRetry={() => void books.refetch()} />
      ) : books.data && books.data.content.length === 0 ? (
        <EmptyState title={title || genre || min || max ? 'No copies match these filters.' : 'No copies yet.'}>
          {title || genre || min || max ? 'Clear a filter or try a different word.' : 'Add the first copy to start the catalog.'}
        </EmptyState>
      ) : books.data ? (
        <>
          <table className="book-table record-cards">
            <thead>
              <tr>
                <th scope="col"><span className="visually-hidden">Cover</span></th>
                <th scope="col">Title</th>
                <th scope="col">Author</th>
                <th scope="col">Genre</th>
                <th scope="col">Cost</th>
                <th scope="col">Status</th>
                <th scope="col"><span className="visually-hidden">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {books.data.content.map((b) => (
                <tr key={b.id}>
                  <td className="cell-cover"><BookCover book={cover(b)} size="sm" /></td>
                  <th scope="row" data-label="Title">{b.title}</th>
                  <td data-label="Author">{b.author}</td>
                  <td data-label="Genre">{b.genre}</td>
                  <td data-label="Cost">{formatCost(b.replacementCost)}</td>
                  <td data-label="Status"><BookBadge book={b} /></td>
                  <td className="row-actions">
                    <Button variant="secondary" onClick={() => setEditing(b)}>Edit<span className="visually-hidden"> {b.title}</span></Button>
                    <Button variant="danger" onClick={() => { setDeleteError(null); setDeleting(b); }}>Delete<span className="visually-hidden"> {b.title}</span></Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={books.data.number} size={books.data.size} total={books.data.totalElements}
            onPage={setPage} onSize={(s) => { setSize(s); setPage(0); }} />
        </>
      ) : null}

      {editing && (
        <BookSheet
          key={editing === 'new' ? 'new' : editing.id}
          book={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={async (b, created) => {
            notify(created ? `Added ${b.title}.` : `Saved ${b.title}.`);
            setEditing(null);
            await refresh();
          }}
        />
      )}

      <Dialog
        open={deleting !== null}
        title={`Delete ${deleting?.title ?? 'this copy'}?`}
        confirmLabel="Delete copy"
        danger
        busy={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting)}
        onClose={() => !remove.isPending && setDeleting(null)}
      >
        <p>This removes copy {deleting?.id} from the catalog. It cannot be undone.</p>
        {deleteError && <p className="form-error" role="alert">{deleteError}</p>}
      </Dialog>
    </section>
  );
}

const LIMITS = { title: 100, author: 50, genre: 50, description: 2000 };

/** Add/edit form in a right-hand sheet. Server field errors map onto fields; the button is disabled while saving. */
function BookSheet({
  book, onClose, onSaved,
}: {
  book: BookResponse | null;
  onClose: () => void;
  onSaved: (book: BookResponse, created: boolean) => void | Promise<void>;
}) {
  const form = useRef<HTMLFormElement>(null);
  const [v, setV] = useState({
    title: book?.title ?? '', author: book?.author ?? '', genre: book?.genre ?? '',
    description: book?.description ?? '', replacementCost: book?.replacementCost !== undefined ? String(book.replacementCost) : '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useFocusFirstInvalid(form, attempt);
  const set = (k: keyof typeof v) => (e: { target: { value: string } }) => setV((s) => ({ ...s, [k]: e.target.value }));

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (saving) return;
    const found: Record<string, string> = {};
    if (!v.title.trim()) found.title = 'Title cannot be empty.';
    if (!v.author.trim()) found.author = 'Author cannot be empty.';
    if (!v.genre.trim()) found.genre = 'Genre cannot be empty.';
    if (!(Number(v.replacementCost) > 0)) found.replacementCost = 'Replacement cost must be greater than 0.';
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) {
      setAttempt((n) => n + 1);
      return;
    }
    const body: BookRequest = {
      title: v.title.trim(), author: v.author.trim(), genre: v.genre.trim(),
      replacementCost: Number(v.replacementCost),
      ...(v.description.trim() ? { description: v.description.trim() } : {}),
    };
    setSaving(true);
    try {
      const saved = book ? await replaceBook(book.id, body) : await createBook(body);
      await onSaved(saved, !book);
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        const { _global, ...fields } = err.fieldErrors;
        setErrors(fields);
        if (_global) setFormError(_global);
        setAttempt((n) => n + 1);
      } else {
        setFormError(describeError(err));
      }
      setSaving(false);
    }
  }

  return (
    <aside className="sheet" aria-label={book ? `Edit ${book.title}` : 'Add copy'}>
      <h2>{book ? 'Edit copy' : 'Add copy'}</h2>
      <form ref={form} onSubmit={submit} noValidate>
        {formError && <p className="form-error" role="alert">{formError}</p>}
        <TextField label="Title" value={v.title} onChange={set('title')} error={errors.title} maxLength={LIMITS.title} />
        <TextField label="Author" value={v.author} onChange={set('author')} error={errors.author} maxLength={LIMITS.author} />
        <TextField label="Genre" value={v.genre} onChange={set('genre')} error={errors.genre} maxLength={LIMITS.genre} />
        <div className="field">
          <label htmlFor="book-description">Description (optional)</label>
          <textarea id="book-description" rows={5} value={v.description} onChange={set('description')} maxLength={LIMITS.description}
            aria-invalid={errors.description ? true : undefined} />
          <div className="field-hint">{v.description.length} / {LIMITS.description}</div>
          {errors.description && <div className="field-error">{errors.description}</div>}
        </div>
        <TextField label="Replacement cost" inputMode="decimal" value={v.replacementCost} onChange={set('replacementCost')} error={errors.replacementCost} />
        <div className="form-actions">
          <Button type="submit" disabled={saving}>{saving ? 'Saving…' : book ? 'Save changes' : 'Add copy'}</Button>
          <Button variant="secondary" onClick={onClose} disabled={saving}>Cancel</Button>
        </div>
      </form>
    </aside>
  );
}
