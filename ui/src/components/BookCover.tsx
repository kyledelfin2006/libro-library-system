import type { BookResponse } from '../api/types';

/** Eight muted cloth colors; each holds ivory text at 7.0:1 or better (plan section 5.7). */
export const CLOTH_COLORS = [
  '#1F4D3A',
  '#2E4A62',
  '#5A2E3A',
  '#4A3B6B',
  '#674619',
  '#3A4A22',
  '#1F5560',
  '#5C3A2A',
] as const;

const IVORY = '#F4EFE0';
const BRASS = '#D2AE63';

/** Stable, non-cryptographic hash (FNV-1a) so a genre always maps to the same cloth on every device. */
export function hashString(value: string): number {
  let h = 2166136261;
  for (let i = 0; i < value.length; i++) {
    h ^= value.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

export function clothIndex(genre: string): number {
  return hashString(genre.trim().toLowerCase()) % CLOTH_COLORS.length;
}

/** The copy ID picks one of four brass treatments: plain, double border, ruled lines, corner ornaments. */
export function patternIndex(id: number): number {
  return Math.abs(id) % 4;
}

const SIZES = { sm: [40, 56], md: [160, 240], lg: [240, 360] } as const;

type CoverBook = Pick<BookResponse, 'id' | 'title' | 'author' | 'genre' | 'copyStatus' | 'available'>;

/** Splits a title into at most four lines of about `max` characters; the last line is ellipsized. */
export function wrapTitle(title: string, max: number, maxLines = 4): string[] {
  const lines: string[] = [];
  let current = '';
  for (const word of title.split(/\s+/).filter(Boolean)) {
    const next = current ? `${current} ${word}` : word;
    if (next.length <= max || !current) current = next;
    else {
      lines.push(current);
      current = word;
    }
  }
  if (current) lines.push(current);
  if (lines.length > maxLines) {
    const kept = lines.slice(0, maxLines);
    kept[maxLines - 1] = `${kept[maxLines - 1]?.slice(0, max - 1)}…`;
    return kept;
  }
  return lines;
}

/**
 * Generated cloth-bound cover drawn from data the book already has. It is decoration only (aria-hidden): the
 * title, author, and status are real text beside it. Available copies carry no ribbon so the default view is quiet.
 */
export function BookCover({ book, size = 'md' }: { book: CoverBook; size?: keyof typeof SIZES }) {
  const [w, h] = SIZES[size];
  const cloth = CLOTH_COLORS[clothIndex(book.genre)];
  const pattern = patternIndex(book.id);
  const lost = book.copyStatus === 'LOST';
  const onLoan = !lost && !book.available;
  const compact = size === 'sm';
  const surname = book.author.trim().split(/\s+/).pop()?.toUpperCase() ?? '';
  const lines = compact ? [] : wrapTitle(book.title, size === 'lg' ? 14 : 11);

  return (
    <svg
      viewBox="0 0 160 240"
      width={w}
      height={h}
      aria-hidden="true"
      focusable="false"
      data-cloth={cloth}
      data-pattern={pattern}
      style={lost ? { filter: 'saturate(0.2)' } : undefined}
    >
      <rect width="160" height="240" rx="2" fill={cloth} />
      {/* Spine band with the genre initial. */}
      <rect width="22" height="240" fill="#000" opacity="0.25" />
      <text x="11" y="126" textAnchor="middle" fill={IVORY} fontSize="14" fontFamily="serif">
        {book.genre.trim().charAt(0).toUpperCase()}
      </text>
      {pattern === 1 && <rect x="32" y="12" width="116" height="216" fill="none" stroke={BRASS} strokeWidth="1.5" />}
      {pattern === 1 && <rect x="37" y="17" width="106" height="206" fill="none" stroke={BRASS} strokeWidth="0.75" />}
      {pattern === 2 &&
        [40, 48, 192, 200].map((y) => <line key={y} x1="34" x2="148" y1={y} y2={y} stroke={BRASS} strokeWidth="0.75" />)}
      {pattern === 3 &&
        [
          [34, 14, 1, 1],
          [148, 14, -1, 1],
          [34, 226, 1, -1],
          [148, 226, -1, -1],
        ].map(([x, y, dx, dy]) => (
          <path
            key={`${x}-${y}`}
            d={`M${x} ${(y ?? 0) + (dy ?? 1) * 18} L${x} ${y} L${(x ?? 0) + (dx ?? 1) * 18} ${y}`}
            fill="none"
            stroke={BRASS}
            strokeWidth="1.5"
          />
        ))}
      {lines.map((line, i) => (
        <text
          key={i}
          x="91"
          y={92 + i * 20}
          textAnchor="middle"
          fill={IVORY}
          fontSize={size === 'lg' ? 15 : 16}
          fontFamily="'Source Serif 4 Variable', Georgia, serif"
          fontWeight="600"
        >
          {line}
        </text>
      ))}
      {!compact && (
        <text x="91" y="208" textAnchor="middle" fill={BRASS} fontSize="9" letterSpacing="1.5">
          {surname.slice(0, 16)}
        </text>
      )}
      {(onLoan || lost) && (
        <g>
          <path d="M126 0 h22 v34 l-11 -9 l-11 9 z" fill={lost ? '#FF8A80' : '#FFB84D'} />
          {!compact && (
            <text
              x="137"
              y="0"
              transform="rotate(90 137 0) translate(8 -2)"
              fill="#1A1A1A"
              fontSize="6.5"
              fontWeight="700"
            >
              {lost ? 'LOST' : 'ON LOAN'}
            </text>
          )}
        </g>
      )}
    </svg>
  );
}
