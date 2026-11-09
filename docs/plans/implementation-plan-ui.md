# UI Implementation Plan

> **Lifecycle:** when this plan has been fully executed, delete this file in the same change and drop its links from the other plans, the plans README, and the Roadmap.

Status: proposed; technical decisions confirmed by the project owner on 2026-11-09. This is one of the independent plans:

| Plan | Scope |
| --- | --- |
| **This plan** | The web UI. |
| [OCI Idle-Reclamation Plan](implementation-plan-oci-idle-reclamation.md) | Keeping the OCI Always Free VM from being reclaimed. |

Facts about the live API stay in the controllers, OpenAPI, and the [engineering reference](../reference/engineering-reference.md). The loan lifecycle is in the [Loan Implementation Guide](../guides/loan.md). Backend work found while planning is **not** listed here; it was tracked in the now-executed backend plan, and each UI phase below names the backend item it depends on.

## 1. Goals and principles

1. **Ship the core library journey first**: sign in, browse, borrow, return, then staff tools.
2. **Follow the real backend flow.** Every page maps to an endpoint that exists today, or names the backend item that adds it. No page is built against an imagined API.
3. **Use standard tools for operations** (Prometheus/Grafana, Swagger UI). Build custom pages only where no standard tool fits.
4. **The UI is not a security boundary.** It hides what a role cannot do; the server's route gates and `UserAccessPolicy` stay authoritative.
5. **Academic, dark-first and accessible**: the Libro logo and its greens drive a calm reading-room design (section 5), dark by default, and every color pair meets WCAG 2.2 AA.

## 2. Confirmed technical decisions

| Decision | Choice | Notes |
| --- | --- | --- |
| Framework | React 19 + TypeScript (strict) + Vite | Static build output. |
| Location | `ui/` in this repository | UI and API change in one PR when needed. |
| Data fetching | TanStack Query, typed client generated from the OpenAPI document | Generated types are checked in CI to stop DTO drift. |
| Routing | React Router with role-based route guards | |
| Token storage | In memory only | 15-minute Bearer token, no refresh tokens by design. A reload means signing in again. |
| Serving | Static build served by the existing Caddy proxy on the same origin as `/app/*` | No CORS needed. Local dev uses the Vite proxy, so `LIBRO_CORS_ALLOWED_ORIGINS` stays unset. |
| Styling | CSS variables from one token file, plus CSS modules; dark theme is the default | Section 5. Charts and diagrams are in-house SVG components (5.8, 5.9). |
| Tests | Vitest and Testing Library for components; Playwright for the smoke journey; axe checks in Playwright | |

## 3. The real backend flow the UI must follow

These facts come from the controllers, DTOs, and the loan guide, not from assumptions.

**Authentication and identity**
1. `POST /app/auth/login` takes `universityId` (`####-####`) and `password`, and returns `accessToken`, `tokenType`, `expiresIn` (900) and `mustChangePassword`. It does **not** return the role or profile.
2. After login, call `GET /app/users/me`. It returns the profile including `userRole`, course, major, and `mustChangePassword`. Role-aware navigation is built from that response, never from the token.
3. If `mustChangePassword` is true, route to the change-password screen and keep the user there. The server does not block other routes, so this is a UI guard.
4. Any 401 clears memory state and returns to sign-in with a "session expired" notice and the intended destination preserved. A 404 on `/me` means the account was deleted: sign out.

**Registration** (`POST /app/users/signup`, creates a STUDENT only)
- Fields: `universityId`, `password`, `firstName`, `lastName` (max 50 each), optional one-character `middleInitial`, `email` (max 150), `userCourse` (`IT`, `EMC`, `IS`), and `major` (`SE`, `SMBPO`, `IST`, `HN`) shown **only** when the course is `IT`. Any supplied role is ignored, so the form never offers one.
- Password policy: 8 to 72 characters, with upper case, lower case, a number, and a symbol, and at most 72 UTF-8 bytes. The form checks bytes, not characters.
- A duplicate university ID or email returns 400; a concurrent duplicate returns 409. Both appear as a field or form error, never a crash.

**Passwords**
- Owner change: `PUT /app/users/{id}/password` with `currentPassword` and `newPassword`. Success clears `mustChangePassword`.
- Staff reset: `PUT /app/users/{id}/password/reset` with a `temporaryPassword` that **staff types or accepts from a generator**. The server never returns it. The UI may offer a client-side generator (`crypto.getRandomValues`) that satisfies the policy, shows it once for hand-off, and never stores or logs it. Staff cannot reset their own or a librarian account.
- There is no forgot-password email flow.

**Catalog**
- Each book is one **physical copy**. Search and filtering use `GET /app/books/query` (title, author, genre, `minReplacementCost`, `maxReplacementCost`, page, size, sort). Allowed sorts: `id`, `title`, `author`, `genre`, `replacementCost`. Default page size 12, maximum 100.
- A copy has `copyStatus` `IN_COLLECTION` or `LOST`. Whether a copy is on loan right now is not yet in the response; the backend plan adds `available` (BE-1). Until then, the catalog shows copy status only and Borrow relies on the server's 409.

**Loans** (see the loan guide for the full state machine)
- Only STUDENT and FACULTY borrow and return. `POST /app/loans/{bookId}` creates a loan due 14 calendar days later (Asia/Manila). Rules enforced server-side: copy available, borrower has no overdue loan, fewer than 3 active loans.
- Return is `PUT /app/loans/{loanId}/return`. Returning another borrower's loan returns 404 by design, so the UI shows "not found" and never confirms existence.
- Staff (ADMINISTRATOR, LIBRARIAN) list loans with `GET /app/loans` (filters `status`, `overdue`, `borrower`) and resolve an `ACTIVE` loan as lost with `PUT /app/loans/{loanId}/lost`. Staff cannot borrow or return. Assistants and librarians can still see their own history.
- A loan's `overdue` flag is computed by the server. The UI must not compute overdue from the browser clock.
- Deleting a book or user with loan history returns 409; show the retention reason.

**Errors**
- The error body is `ErrorResponse`: `error`, `details`, `statusCode`, `timestamp`, `fieldErrors` (field to message, with `_global` for object-level errors) and `requestId`. Map `fieldErrors` onto form fields and show `details` otherwise. Show `requestId` on unexpected errors so a user can quote it.
- Branch on HTTP status plus, once the backend plan's BE-2 ships, the stable `code`. Do not match English message text in the client.
- Successes use the `ApiResponse<T>` envelope; unwrap it once in the client.
- 429 on login or signup: read `Retry-After` and show a countdown. Do not retry automatically.

**Time**: stored values are Manila local time with no offset. Format them explicitly as Manila time, whatever the browser's time zone.

## 4. Information architecture by role

| Role | Home | Navigation |
| --- | --- | --- |
| STUDENT, FACULTY | Catalog | Catalog, My Loans, My Account |
| ADMINISTRATOR (library assistant) | Staff dashboard | Dashboard, Catalog, Loans, Inventory, Accounts (students and faculty), Audit (scoped, when built), Version, My Account |
| LIBRARIAN | Staff dashboard | All of the above plus Accounts for library assistants and the full Audit log |

Public: Sign in, Register, Status. The route guard table lives in one `permissions.ts` that mirrors `SecurityConfig` and is tested against the backend role matrix in the [Role and JWT guide](../reference/security-roles-and-jwt-guide.md).

## 5. Design concepts

Owner direction (2026-11-09): an **academic feel**, **dark theme preferred**, dashboards, charts and diagrams, and a cover-image fallback that does not hurt the experience. Page layouts are decided in section 6.

### 5.1 Concept: "the reading room at night"

Libro looks like a quiet, lamp-lit university reading room: near-black green surfaces (drawn from the logo's deep green), warm ivory text like paper under a reading lamp, thin brass rules like the trim on a card catalog, and serif headings like a printed monograph. It is calm, precise, and a little formal, never playful and never gamified.

| Principle | What it means in the UI |
| --- | --- |
| **Calm over clever** | Few colors, generous margins, no motion beyond 150 ms fades. Bright green appears only where the user can act or where status matters. |
| **Evidence first** | Staff screens show the numbers and the table behind every chart. Nothing decorative stands in for data. |
| **Catalog-card heritage** | Book detail and loan details use a ruled "catalog card" panel (label and value rows with thin brass dividers) instead of generic cards. |
| **Rules stay visible** | The 14-day term, the 3-book limit and the overdue block are shown near the Borrow button and on My Loans as a small diagram, so the server's 409 is rarely a surprise. |
| **Two densities** | Patron pages are spacious (reading and browsing). Staff pages are denser (scanning and acting). Same tokens, different spacing scale. |
| **Honest states** | Availability is advisory, overdue comes from the server, and an unknown status is shown as unknown, never guessed. |

### 5.2 Voice and microcopy

Plain, exact, courteous. Say what happened, why, and what to do next. Never blame the user, never use exclamation marks, and never show raw status codes as the message (show the `requestId` as a reference).

| Situation | Copy |
| --- | --- |
| `LOAN_COPY_UNAVAILABLE` | "This copy is on loan. Check back after it is returned." |
| `LOAN_BORROWER_OVERDUE` | "Return your overdue book before you borrow another." |
| `LOAN_LIMIT_REACHED` | "You have 3 books checked out, the most the library allows. Return one to borrow another." |
| Successful borrow | "Borrowed. Due Monday 23 November 2026, 14 days from today." |
| 429 | "Too many attempts. You can try again in 42 seconds." |
| 401 mid-session | "Your session ended. Sign in again to continue." |
| Empty search | "No books match these filters. Clear a filter or try a different word." |
| Forced password change | "Your password was reset by library staff. Choose a new one to continue." |
| Unexpected error | "Something went wrong on our side. Reference: 3f2b8c1e." |

Dates read "Mon 23 Nov 2026" with the relative form beside them ("in 9 days", "2 days overdue"). Times are Manila time and say so once per page. Use "copy" when a physical item matters ("This copy") and "book" otherwise. The UI uses "library assistant", not "administrator", for the ADMINISTRATOR role.

### 5.3 The logo

The asset is [assets/LIBRO_LOGO.png](../../assets/LIBRO_LOGO.png): 1292 by 1218 px, transparent background, about 958 KB. It shows an open book with a bookmark and the lowercase wordmark "libro" in a rounded bold face, with a green power-button hexagon at lower right. It has a white outline around its shapes, so it reads on dark and light surfaces.

Dominant colors measured from the file: deep green near `#008040`, bright green near `#4DB82A`, and white. The palette in 5.4 derives from these. The bookmark in the logo is reused as the "On loan" ribbon on covers (5.7).

Asset pipeline (done in Phase 0, committed under `ui/public/brand/`, source file left untouched):
- Generate optimized PNG or WebP at 48, 96 and 192 px wide for the header, 256 px for the sign-in panel, and 512 px for the document icon. The original is too large to ship in a page.
- Favicon set: crop to the **book glyph only** (the wordmark is unreadable at 16 px); export 16, 32, 180 (apple-touch) and 512 px, plus `favicon.ico`. Cropping a derivative is allowed; do not recolor, stretch, or redraw it.
- A 1200 by 630 social image for link previews.
- A vector version is out of scope unless the owner supplies one; do not auto-trace the logo.
- Meaningful `alt` text on the lockup ("Libro"), empty `alt` where the logo repeats nearby text.

Placement:
- **Header**: lockup about 44 px tall, links to the role's home, with clear space of at least the height of the "l".
- **Sign-in and register**: lockup about 160 px above the form.
- **Footer and Version page**: small lockup.
- **Dark theme (default)**: place the lockup directly on the dark surface; its white outline carries it. Verify visually; only if it clips, place it on an ivory chip rather than altering the file.

### 5.4 Color tokens (dark first)

Defined once as CSS variables. **Dark is the default theme** for every visitor, whatever their operating-system setting. Light ("paper") is a supported alternative, and a theme switch (Dark, Light, System) stores only the theme name in `localStorage`, which is non-sensitive. All ratios below were computed with the WCAG 2.2 formula.

| Token | Dark (default) | Light (paper) | Use and contrast |
| --- | --- | --- | --- |
| `--color-bg` | `#0C1611` | `#F7F4EC` | Page background |
| `--color-surface` | `#121F17` | `#FFFDF8` | Cards, tables, inputs |
| `--color-surface-raised` | `#192A20` | `#FFFFFF` | Dialogs, menus, hovered rows |
| `--color-text` | `#EDE9DC` | `#1A241D` | Body text: dark 15.2:1 on bg, 14.0:1 on surface; light 14.5:1 on bg |
| `--color-text-muted` | `#B4BFB4` | `#4D5A50` | Secondary text: dark 9.7:1 on bg, 8.0:1 on raised; light 6.6:1 on bg |
| `--color-primary` | `#4DB82A` | `#008040` | Buttons, links, active nav. Dark 6.65:1 on surface; light 4.96:1 on surface, 4.59:1 on bg |
| `--color-primary-hover` | `#6FD04A` | `#006B36` | Hover and pressed (dark 8.75:1; light 6.05:1) |
| `--color-on-primary` | `#0B1A10` | `#FFFFFF` | Text on a primary fill (dark 7.02:1; light 5.05:1) |
| `--color-primary-subtle` | `#1B3324` | `#E3F3E8` | Selected rows, soft banners (dark: `#EDE9DC` text 11.2:1) |
| `--color-brass` | `#D2AE63` | `#7A5A12` | Ornament: rules, catalog-card dividers, one chart hue, small-caps labels. Dark 8.1:1 on surface; light 6.3:1 |
| `--color-border` | `#27392E` | `#D9D3C3` | Decorative dividers only |
| `--color-border-input` | `#7F9486` | `#6B7B70` | Control outlines (dark 5.3:1; light 4.4:1 on surface), meeting the 3:1 non-text rule |
| `--color-danger` | `#FF8A80` | `#B3261E` | Errors, destructive actions (dark 7.5:1; light 6.4:1) |
| `--color-warning` / `--color-warning-bg` | `#FFD27A` / `#2A2310` | `#7A4F00` / `#FFF3D6` | Overdue and caution (dark 12.0:1; light 6.5:1) |
| `--color-info` | `#8AB8F0` | `#1F5FA8` | Neutral information (dark 8.3:1; light 6.3:1) |
| `--focus-ring` | `#6FD04A` | `#008040` | 2 px outline with 2 px offset on every focusable element (dark 8.75:1) |

Rules:
- **Green is the brand; brass is ornament.** Deep and bright green carry action and identity. Brass is a quiet support color for rules, labels and one chart series; it is never a button, link or status color.
- **Color is never the only signal.** Every status has an icon and a text label as well.
- Status mapping (chips and charts): `ACTIVE` green with a clock icon; **Overdue** amber (`#FFB84D` dark, `#7A4F00` light) with an alert icon and the word "Overdue"; `RETURNED` sage (`#8FA597`) with a check; `LOST` coral (`#FF8A80`) with a flag. Copy `LOST` uses the same coral chip.
- Light-theme chart and status values are derived the same way and checked by the token test in 5.10.

### 5.5 Typography, spacing and shape

- **Headings, book titles and descriptions**: Source Serif 4 (variable), weights 600 and 700, so reading content feels like print. **Interface text, tables, forms and numbers**: the system UI font stack with tabular numerals, for speed and legibility. **Labels** in the catalog card use small caps with letter-spacing in brass. Self-host the font as a subsetted `woff2` through `@fontsource-variable/source-serif-4`; load nothing from a third-party host, which keeps the Content Security Policy strict.
- Type scale 14, 16, 18, 22, 28, 40 px; body 16 px; line height 1.55 for reading text and 1.4 for interface text; maximum reading measure 68 characters.
- Spacing on a 4 px base (4, 8, 12, 16, 24, 32, 48, 64). Patron pages use the upper half of the scale, staff pages the lower half.
- Radius 6 px on controls and chips, 10 px on panels, 2 px on book covers (a book is nearly square-cornered). Thin 1 px brass or `--color-border` rules do most of the structure; shadows are used only on dialogs and menus.
- Minimum touch target 44 px. Respect `prefers-reduced-motion`; motion is limited to 150 ms fades and focus transitions.

### 5.6 Layout and components

- Mobile first, breakpoints at 640, 1024 and 1280 px. Patron pages use a top bar; staff pages add a left rail from 1024 px and a drawer below.
- Tables are real `<table>` elements with sortable headers wired to the API's allow-listed sort fields; pagination shows page, page size (12, 24, 48, 100) and total.
- **Below 640 px, tables become record cards**: each row is a card of label and value pairs with the primary action at the bottom. Staff can read, filter and mark loans lost on a phone; inventory and account editing are optimized for tablets and up.
- Shared components: Button, TextField, Select, Chip, DataTable, Pagination, Dialog (confirmations for delete, lost and self-delete), SidePanel (record details), CatalogCard, BookCover, Chart primitives, Diagram components, Toast, Skeleton, EmptyState, ErrorState (with `requestId`), and a role-gated `<Can>` wrapper.
- Every data view has explicit loading, empty, error, 401, 403, 404, 409 and 429 states. Skeletons match the final layout size so nothing jumps.

### 5.7 Book covers without cover images

**Problem.** The data model has no cover image (`BookResponseDTO` has id, title, author, genre, description, replacement cost, copy status), yet a catalog of identical grey boxes would look broken and make cards hard to scan.

**Solution: generated "cloth-bound" covers.** A `BookCover` component draws a cover from data the book already has. It needs no image, no network call and no backend change, renders on first paint at a fixed 2:3 ratio so there is no layout shift, and weighs a few hundred bytes of SVG per cover.

| Element | Derived from | Detail |
| --- | --- | --- |
| Cloth color | `genre` | A stable hash of the lowercased genre picks one of eight muted cloth colors: `#1F4D3A`, `#2E4A62`, `#5A2E3A`, `#4A3B6B`, `#6B4A1F`, `#3A4A22`, `#1F5560`, `#5C3A2A`. Each holds ivory text (`#F4EFE0`) at 7.0:1 or better. Same genre, same color, so a shelf of one genre reads as a set. |
| Pattern | `id` | The copy ID picks one of four quiet brass-on-cloth treatments (plain, double border, ruled lines, corner ornaments), so duplicate titles and neighbors look different without noise. |
| Title | `title` | Source Serif 4, centered, up to four lines with an ellipsis; the full title is always in the card text below, so nothing is lost by clamping. |
| Author | `author` | Surname in brass small caps near the foot. |
| Spine band | `genre` | A narrow band at the left edge with the genre initial, which also gives list rows a recognizable "spine" at 40 by 56 px. |
| **On loan** | `available` (BE-1) | A small bookmark ribbon in the top-right corner, echoing the logo's bookmark. Available copies get **no** ribbon, so the default view stays quiet. |
| **Lost** | `copyStatus` | The cover desaturates and shows a "Lost" ribbon. |

Sizes: `sm` (40 by 56, list rows and tables), `md` (about 160 by 240, catalog grid), `lg` (about 240 by 360, book detail).

Rules that protect the experience:
- The cover is decoration: it is `aria-hidden`, and the title, author and status are real text beside it, so screen readers, search-in-page and copy-paste work.
- Genre colors are never the only signal, because the genre chip repeats the name. Collisions between genres are acceptable.
- The same book always renders the same cover, on every device, with no randomness.
- A user test gates the design: in a 5-second scan of 12 cards, participants find a named book and tell available from on loan. If cover text hurts that, drop the title from `md` covers and keep it in the card text.

Alternatives rejected: a neutral grey placeholder (looks unfinished and repeats), fetching covers from a third-party service in the browser (the model has no ISBN, it breaks the strict CSP, and it would leak patrons' browsing), generated imagery (cost, inconsistency, and it can mislead about a real book), and an initials avatar (not book-like).

**Later, if real covers are wanted.** `BookCover` already takes an optional `coverUrl` and prefers it over the generated cover. Supplying it needs a backend decision (an ISBN or cover field and, if an outside source is used, server-side fetching and caching so patrons' browsers never contact it). That is deferred and recorded as deferred work (the backend plan was executed and removed).

### 5.8 Dashboards and charts

**Technology.** Small in-house SVG chart primitives (bar, stacked bar, line or area), roughly 300 lines, reading colors from CSS tokens. This avoids a chart library's bundle weight, inline styles and theming gaps. If chart needs outgrow bars, stacked bars and one line, switch to a library in a separate decision.

**Chart colors** (dark first; all at least 3:1 against `--color-surface`, as non-text graphics must be):

| Use | Dark | On surface |
| --- | --- | --- |
| Series 1 (primary) | `#4DB82A` | 6.65:1 |
| Series 2 (brass) | `#D2AE63` | 8.08:1 |
| Series 3 (sky) | `#6FA8DC` | 6.73:1 |
| Series 4 (violet) | `#B59CE6` | 7.18:1 |
| Series 5 (teal) | `#45C2B0` | 7.78:1 |
| Axis text | `#B4BFB4` | 8.96:1 |
| Gridlines | `#27392E` | decorative |
| Status, fixed meaning | ACTIVE `#4DB82A`, Overdue `#FFB84D`, RETURNED `#8FA597`, LOST `#FF8A80` | 6.65, 9.90, 6.48, 7.45 |

Rules:
- At most five series. Status charts always use the fixed status colors; category charts (genre) use series order. Brass is not used in a status chart.
- Each chart is a `<figure>` whose caption states the **takeaway**, computed from the data ("12 of 40 active loans are overdue"), not just a label.
- Direct value labels on bars; legends only when there are several series; a **"View as table"** toggle on every chart; keyboard-focusable marks with a tooltip on focus and hover; no animation under `prefers-reduced-motion`.
- Horizontal bars for category comparisons (genre names are long), a stacked bar for part-to-whole status mix, a line for change over time. No pie or donut charts, no 3D, no dual axes.
- Numbers: counts as integers, replacement cost with two decimals and **no currency symbol until the owner chooses one** (the API returns a bare decimal). Dates in Manila time.
- Loading shows a skeleton of the same size; empty shows an explanation ("No loans in the last 12 weeks"); an error keeps the figure's frame and shows a retry.

**Staff dashboard charts and their data**

| Chart or tile | Data source | Backend need |
| --- | --- | --- |
| KPI tiles: copies, active loans, overdue loans, lost loans, total replacement value | `GET /app/books/stats`; `GET /app/loans?status=ACTIVE&size=1`, `overdue=true&size=1`, `status=LOST&size=1`, reading `totalElements` | None |
| Loan status mix (stacked bar: active, overdue, returned, lost) | The same count calls plus `status=RETURNED&size=1` | None |
| Collection by genre (horizontal bar, top 8 plus "Other") | `GET /app/books/genre` | None |
| Overdue by days late (buckets 1 to 3, 4 to 7, 8 to 14, 15 or more) | `GET /app/loans?overdue=true&size=100`, bucketed in the client; shows "first 100 of N" when more exist | None |
| Loans per week, last 12 weeks (line: checkouts and returns) | Needs an aggregate | **BE-6** |
| Most borrowed titles (top 5 bars) | Needs an aggregate | **BE-6** |
| Needs attention (table of the 5 longest-overdue loans) | `GET /app/loans?overdue=true&sort=dueAt,asc&size=5` | None |

All counts respect the caller's role scope automatically, because the server applies it. The dashboard issues about eight small requests in parallel; measure it in Phase 4.

**Patron visuals** (My Loans): the loan-slot diagram and due-date bars in 5.9. No charts for patrons.

### 5.9 Diagrams

Diagrams are inline SVG components styled by the same tokens, with a text alternative, so they adapt to the theme and are testable.

| Diagram | Where | What it shows |
| --- | --- | --- |
| **Loan slots** | My Loans, top; compact form beside Borrow on book detail | Three slots. A filled slot shows the title and a due countdown bar (green, amber when under 3 days, coral label "overdue" when late); an empty slot reads "Free". An overdue loan puts a lock mark on the empty slots with the text "Return your overdue book to borrow." Mirrors the real rule: at most 3 active loans, and an overdue loan blocks new checkout. |
| **Loan lifecycle** | "How borrowing works" disclosure on My Loans | Checkout, then ACTIVE, which leads to RETURNED (borrower returns) or LOST (staff records). Notes: due 14 calendar days after checkout; overdue is a state of ACTIVE, not a separate status. |
| **Staff resolution** | Loan management, help disclosure | Open patron history, select an ACTIVE loan, mark lost, copy becomes LOST and cannot be borrowed again. |
| **Request path** | Status page | Browser, proxy, API, database as a left-to-right chain. It shows only what `/readyz` reports (overall ready or not) and never claims per-component health the probes do not give. |
| **Who can do what** | Accounts help panel | A small matrix diagram of the four roles and the actions in the permission table, generated from `permissions.ts` so it cannot drift. |

### 5.10 Accessibility and quality gates

- Keyboard-complete flows, visible focus, labelled inputs, error summaries that move focus, `aria-live` for toasts and form results, and focus management on route change.
- Charts and diagrams have a text or table alternative; nothing is conveyed by color alone.
- A token test computes every foreground and background pair above for both themes and fails below 4.5:1 for text and 3:1 for non-text.
- axe runs in Playwright on every page in both themes; no serious or critical violations.
- A visual snapshot test covers `BookCover` for all eight cloth colors and four patterns, in both themes.

## 6. Wireframes

Layout decisions for every page. Wireframes show the 1280 px desktop layout; the notes under each say what changes on tablet and phone. They fix structure and hierarchy, not final pixels. All screens use the dark theme.

### 6.1 App shell

```
+--------------------------------------------------------------------------+
| [libro]   Catalog   My Loans              [ Search books...  ]  (o) Alex v|
+--------------------------------------------------------------------------+
|                                                                          |
|   page content, max width 1120 px, centered                              |
|                                                                          |
+--------------------------------------------------------------------------+
| libro · Times shown in Manila time              Status · API docs        |
+--------------------------------------------------------------------------+
```

Staff shell (from 1024 px) adds a 220 px left rail: Dashboard, Catalog, Loans, Inventory, Accounts, Audit, Version. Below 1024 px the rail becomes a drawer opened from a menu button; below 640 px the top search collapses to an icon. The header search submits to the catalog.

### 6.2 Sign in and register

```
+--------------------------------+-----------------------------------------+
|                                |  Sign in to the library                 |
|          [ libro logo ]        |                                         |
|            (large)             |  University ID   [ 2025-4321         ]  |
|                                |  Password        [ ************  show ]  |
|   A quiet place to borrow,     |                                         |
|   return and keep track of     |  [ Sign in ]                            |
|   your books.                  |                                         |
|                                |  New student? Create an account         |
+--------------------------------+-----------------------------------------+
```

Register is the same split with the form fields in order: University ID, First name, Last name, Middle initial (optional), Email, Course; Major appears only when the course is IT; Password with a live checklist (8 to 72 characters, upper, lower, number, symbol). Below 1024 px the brand panel shrinks to the logo above the form. Errors appear under the field they belong to (`fieldErrors`), plus a summary at the top that receives focus.

### 6.3 Catalog

```
 Catalog                                              [ Grid | List ]
 -----------------------------------------------------------------------
 [ Search title, author or genre...                 ]   [ Filters (2) ]
 Genre: Dystopian x    Available only x                  Sort: Title A-Z v
 -----------------------------------------------------------------------
 +--------+  +--------+  +--------+  +--------+  +--------+  +--------+
 | cover  |  | cover  |  | cover  |  | cover  |  | cover  |  | cover  |
 | 2:3    |  |   [ribbon: On loan]                                    |
 +--------+  +--------+  +--------+  +--------+  +--------+  +--------+
 1984         Dune         Emma ...
 G. Orwell    F. Herbert
 [Dystopian]  [Sci-fi]
 -----------------------------------------------------------------------
 Showing 1-12 of 87        < 1 2 3 ... 8 >          Per page: 12 v
```

- Patron default is the grid (six columns at 1280 px, four at 1024, two at 640, with list rows below 640 so titles are readable). The List view uses `sm` spine covers in a table-like row with title, author, genre and status.
- **Filters** open a right-hand drawer: title, author, genre (text), replacement-cost range, and "Available only" (when BE-1 ships). Active filters show as removable chips and live in the URL, so a search can be shared and the back button works.
- Replacement cost appears on cards for staff only; patrons see it on the book detail page.
- Empty result shows the microcopy in 5.2 and a "Clear filters" button.

### 6.4 Book detail

```
 < Back to catalog
 +------------+   1984                                    (serif, 40 px)
 |            |   by George Orwell  ·  [Dystopian]
 |   cover    |   -------------------------------------------------
 |   (lg)     |   A dystopian novel about surveillance and ...
 |            |   (description, serif body, 68-character measure)
 +------------+
                  +- CATALOG CARD ----------------------------------+
                  | COPY NO.            42                          |
                  | -----------------------------------------------|
                  | STATUS              Available                   |
                  | -----------------------------------------------|
                  | REPLACEMENT COST    19.99                       |
                  +-------------------------------------------------+
                  [ Borrow this copy ]     Loan slots: [#][ ][ ]  1 of 3 used
                  Due 14 days after checkout (Mon 23 Nov 2026).
```

- The catalog card uses brass small-caps labels and thin brass rules.
- Borrow is a primary button for STUDENT and FACULTY only; library assistants and librarians see **Edit** and **Delete** instead, and no Borrow control. A lost copy shows "This copy was reported lost" and no action.
- If the borrower is blocked (overdue or at the limit), the button is disabled with the reason beside it, taken from the same rules as the loan-slot diagram. The server's code still decides if the state is stale.
- Below 1024 px the cover stacks above the title; the catalog card and button follow in one column.

### 6.5 My Loans

```
 My loans
 +- LOAN SLOTS -------------------------------------------------------+
 | [# 1984        due in 9 days ====-----]  [# Dune  2 days  ==---- !] |
 | [  Free slot                          ]                              |
 +--------------------------------------------------------------------+
 Current (2)   History
 ---------------------------------------------------------------------
 Book          Borrowed        Due                 Status      
 1984          Mon 9 Nov       Mon 23 Nov (9 d)    Active      [ Return ]
 Dune          Fri 6 Nov       Mon 20 Nov (2 d)    Active      [ Return ]
 ---------------------------------------------------------------------
 v How borrowing works   (lifecycle diagram, collapsed by default)
```

History is paginated (`borrowedAt` descending by default) and sortable by the API's allowed fields. Return opens a confirmation dialog naming the book; success shows a toast with the return time. Staff roles see their own history here too, with no Return button. Below 640 px the table becomes record cards.

### 6.6 Staff dashboard

```
 Dashboard                          As of 09 Nov 2026 14:05 Manila  [Refresh]
 +----------+ +----------+ +----------+ +----------+ +----------------+
 | COPIES   | | ACTIVE   | | OVERDUE  | | LOST     | | REPLACEMENT    |
 |   87     | |   40     | |   12     | |    3     | | VALUE  1,684.10|
 +----------+ +----------+ +----------+ +----------+ +----------------+
 +- 12 of 40 active loans are overdue -----+ +- Overdue by days late --+
 | [ stacked bar: active | overdue |        | | 1-3  ####               |
 |   returned | lost ]                      | | 4-7  ###                |
 +-----------------------------------------+ | 8-14 ##                 |
 +- Loans per week, last 12 weeks ---------+ | 15+  #                  |
 | line: checkouts / returns               | +-------------------------+
 +-----------------------------------------+ +- Collection by genre ---+
                                             | Dystopian  ######  18   |
 +- Needs attention -----------------------+ | Sci-fi     #####   14   |
 | Borrower   Book   Due       Days late   | | ...                     |
 | 2025-4321  Dune   Mon 2 N   7  [Open]   | +-------------------------+
 +-----------------------------------------+
```

KPI tiles are links that open the Loans page with the matching filter. Each chart has a "View as table" toggle. Tiles and charts hold their size while loading. From 1024 px down the grid becomes two columns, and below 640 px one column with the tiles in a horizontally scrollable strip.

### 6.7 Loan management

```
 Loans
 Status [All v]   [ ] Overdue only   Borrower [____-____]        [Clear]
 -----------------------------------------------------------------------
 Loan  Borrower    Book              Borrowed   Due        Status
 81    2025-4321   1984              9 Nov      23 Nov     Active
 80    2025-1177   Dune              6 Nov      20 Nov     Overdue (2 d)
 ...                                                    Per page: 12 v
```

Selecting a row opens a **side panel** with the loan's details in the catalog-card style, the borrower summary, a link to that patron's loan history, and, for ACTIVE loans, **Mark as lost**. That action opens a dialog stating the consequence ("The loan closes and this copy can no longer be borrowed.") before it submits. An unknown borrower ID shows a not-found message; an out-of-scope one shows a permission message. Below 640 px rows become record cards.

### 6.8 Inventory

```
 Inventory                                                [ + Add copy ]
 [ Search... ]   Genre [All v]   Cost [min]-[max]
 -----------------------------------------------------------------------
 [sp] Title            Author        Genre       Cost    Status     Actions
 [sp] 1984             G. Orwell     Dystopian   19.99   On loan    Edit  Delete
 [sp] Dune             F. Herbert    Sci-fi      24.50   Available  Edit  Delete
```

Add and Edit open a right-hand sheet with the book form (title 100 characters, author 50, genre 50, description 2000 with a counter, replacement cost greater than zero). Field errors map to fields. Delete opens a confirmation; a 409 for a copy with loan history shows "This copy has loan history and must be kept." Below 1024 px the sheet becomes a full-screen page.

### 6.9 Accounts

```
 Accounts                                      [ + New faculty / assistant ]
 [ Search name, ID or email... ]   Role [All v]
 -----------------------------------------------------------------------
 ID          Name              Role        Course/Major     Email
 2025-4321   Alex Rivera       Student     IT / SE          alex@...
```

A row opens a side panel with the profile and the actions the caller may take: **Edit profile**, **Reset password** (a dialog with the one-time password generator and the note "Share this with the owner. It is shown once and not stored."), and **Delete account** (a confirmation that names the account). Actions the caller's role does not allow are not shown. The "New" button appears for the LIBRARIAN only, and its dialog offers faculty or library assistant. The role filter lists only roles in the caller's scope. A "Who can do what" help diagram sits behind a disclosure.

### 6.10 My Account

A two-column page: Profile (read view with an Edit button that turns the fields editable) and Security (change password with the policy checklist). A **Danger zone** panel at the foot, shown to STUDENT and FACULTY only, holds "Delete my account" with a confirmation that requires typing the university ID. Library assistants and librarians see no delete control.

### 6.11 Status, Version and Audit

- **Status**: the request-path diagram with one overall state ("All systems ready" or "Not ready"), the time of the last check, and a manual refresh. It polls `/readyz` every 30 seconds while visible.
- **Version**: a catalog-card panel (version, build time, commit, environment) and links to API docs and metrics.
- **Audit**: a filter bar (actor, entity type, date range) above a paginated table with time, actor, action and entity; a row opens a side panel with the summary. Assistants see only the scoped events the server returns.

## 7. Pages, access, and endpoints

| Page | Roles | Endpoints | Backend dependency | Phase |
| --- | --- | --- | --- | --- |
| Sign in | Public | `POST /app/auth/login`, then `GET /app/users/me` | None (better messages with BE-2) | 1 |
| Register (student) | Public | `POST /app/users/signup` | None | 1 |
| Forced password change | Any, when `mustChangePassword` | `PUT /app/users/{id}/password` | None | 1 |
| Catalog search and browse | All | `GET /app/books/query`, `/app/books/genre` | BE-1 for "available only" | 2 |
| Book detail with Borrow | All; Borrow for STUDENT and FACULTY | `GET /app/books/{id}`, `POST /app/loans/{bookId}` | BE-1, BE-2 | 2 |
| My Account | All | `GET /app/users/me`, `PATCH /app/users/{id}`, password change, `DELETE /app/users/{id}` (STUDENT and FACULTY only) | None | 3 |
| My Loans and history | All | `GET /app/loans/me` | None | 3 |
| Return | STUDENT, FACULTY | `PUT /app/loans/{loanId}/return` | BE-2 | 3 |
| Staff dashboard | ADMINISTRATOR, LIBRARIAN | `GET /app/loans` (overdue, status, `size=1` counts), `/app/books/stats`, `/app/books/genre` | BE-6 for the weekly-loans and most-borrowed charts only | 4 |
| Loan management | ADMINISTRATOR, LIBRARIAN | `GET /app/loans`, `GET /app/loans/users/{id}`, `PUT /app/loans/{id}/lost` | BE-2 | 4 |
| Inventory | ADMINISTRATOR, LIBRARIAN | `POST /app/books/add`, `PATCH`, `PUT`, `DELETE /app/books/{id}` | BE-1 | 4 |
| Accounts | ADMINISTRATOR (students, faculty), LIBRARIAN (also assistants) | `GET /app/users`, `POST /app/users/faculty`, `POST /app/users/administrators` (LIBRARIAN), `PATCH`/`PUT`, password reset, `DELETE` | BE-3 | 5 |
| Status | Public | `GET /livez`, `GET /readyz` | None | 6 |
| Version | ADMINISTRATOR, LIBRARIAN | `GET /app/info` | BE-4 | 6 |
| API docs link | Staff | Swagger UI when `LIBRO_API_DOCS_ENABLED=true` | None | 6 |
| Audit log | ADMINISTRATOR (scoped to student, faculty, book and loan events) and LIBRARIAN (all events) | `GET /app/audit` | BE-5 | 7 |

The book form fields and limits: title max 100, author max 50, genre max 50, description max 2000 and optional, replacement cost a positive decimal.

## 8. Cross-cutting behavior

- **Stale permissions**: a role change or account deletion does not revoke an issued token for about 15 minutes. Treat an unexpected 403 on a previously allowed action as a reason to refresh `/me`, and a 404 on `/me` as a sign-out.
- **Security headers**: set a strict CSP (no inline scripts, self-hosted fonts), `frame-ancestors 'none'`, `Referrer-Policy: no-referrer` and `X-Content-Type-Options` in `infra/caddy/Caddyfile` for the UI.
- **No secrets in the browser**: never store tokens or passwords in web storage, URLs, logs, or analytics. Never put a university ID or token in a query string that is not already part of an API contract.
- **Idempotent buttons**: disable a submit button while its request is in flight so a double click cannot double-borrow; the server stays the authority.

## 9. Phased delivery

Each phase ends in a mergeable PR with tests and a working demo against `seed-demo.sh` data.

### Phase 0: Foundations
- Scaffold `ui/`; strict TypeScript, lint, formatter.
- Generate API types from OpenAPI; build the client with envelope unwrapping, `ErrorResponse` normalization and 401 handling.
- Token file with dark as the default theme and a theme switch (dark, light, system), fonts, brand asset pipeline (5.3), `BookCover` (5.7), chart and diagram primitives (5.8, 5.9), shared components, layout shell, `permissions.ts`.
- Vite dev proxy; CI job `ui-test` (typecheck, lint, unit tests, token contrast test, build).
- Caddy serves `ui/dist` and proxies `/app/*`; the CD pipeline builds the UI into the deployment.
- Done when: an empty authenticated shell with the logo, both themes, the `BookCover` snapshot set and a passing axe check loads through Caddy.

### Phase 1: Authentication (no backend change needed)
- Sign in, register with the course and major logic and live password hints, forced password change, sign out.
- Tests: wrong password, 429 countdown, expired-token redirect, forced-change guard, field-error mapping.

### Phase 2: Catalog and book detail (needs BE-1 and BE-2 for the full experience)
- Catalog grid and list views with generated covers, filters drawer, sort and pagination with URL-synced state; book detail with the catalog card and loan-slot diagram; Borrow with each rule failure explained from its error code. Run the 5-second cover scan test (5.7) before finishing the phase.
- Ship first with copy status only if BE-1 is not ready; add availability when it lands.

### Phase 3: My account and loans
- Profile view and edit, password change, self-delete with confirmation (STUDENT and FACULTY only).
- My Loans with the loan-slot diagram, due-date bars, history, return action, and the lifecycle diagram disclosure.

### Phase 4: Staff tools
- Dashboard per 6.6: KPI tiles, loan status mix, genre bars, overdue-by-days-late and the needs-attention table from existing endpoints, each chart with a "View as table" toggle. Add the weekly-loans line and most-borrowed bars when BE-6 ships; measure the request count first.
- Loan management with status, overdue and borrower filters; mark-lost with confirmation.
- Inventory CRUD with field errors mapped; handle 409 on delete.

### Phase 5: Accounts (needs BE-3)
- Role-scoped directory with search and role filter; create faculty and assistants (LIBRARIAN only); staff password reset with the one-time generator; delete with scope rules.

### Phase 6: Operations pages (Version needs BE-4)
1. **Status**: poll `/readyz` and draw the request-path diagram (5.9); show only what the probes expose. Do not fake per-service checks.
2. **Version**: version, build time, commit, environment.
3. **Metrics**: add Prometheus and Grafana as an optional Compose profile scraping the private management port, and link to it. This replaces Spring Boot Admin, which would need broader Actuator exposure.
4. **Release history** (optional): a static page generated at build time from GitHub Releases and workflow runs.

### Phase 7: Audit log (needs BE-5)
- Filter by actor, entity and date. The server scopes results by role, so the UI shows no filter option for events an assistant cannot see and treats a 403 on a filter as a normal denial.

## 10. Explicitly out of scope

- Runtime log viewer and log-level control (needs a privileged write surface; separate security decision).
- Refresh tokens, a token denylist, or server sessions (see AGENTS.md "Don't").
- Self-service email password reset (no mail infrastructure).
- Feature flags, backup and restore status pages, native mobile apps.
- Redrawing or vectorizing the logo.

## 11. Definition of done

- Each page works for exactly the roles in section 7, follows its wireframe in section 6, and has loading, empty, error, 401, 403, 404, 409 and 429 states.
- Component tests cover role gating and error mapping; one Playwright journey covers register, sign in, search, borrow, return, and a staff lost-resolution.
- `permissions.ts` has a test asserting it matches the backend matrix for every listed route.
- The contrast test and axe checks pass in both themes, and the `BookCover` snapshot test covers every cloth color and pattern.
- CI runs `ui-test`; CD ships the UI in the deployed Caddy configuration.
- No tokens or passwords in storage, logs, URLs, or source; staff demo credentials stay private ([Demo Accounts](../guides/demo-accounts.md)).
- README, the engineering reference and the roadmap are updated only for facts that changed. When the UI ships, replace this plan with as-built documentation and remove the file, following the repository's convention.

## 12. Risks

| Risk | Mitigation |
| --- | --- |
| UI and API drift | Generated types from OpenAPI, checked in CI. |
| Availability is advisory and can be stale | Borrow stays server-authoritative; the UI handles the 409 and refreshes the copy. |
| Memory-only token annoys users on reload | Accepted; revisit only with a threat-model decision, not ad hoc. |
| Logo raster is large and has no vector source | Derived, sized variants; the original stays untouched; a vector version is the owner's call. |
| Dark theme looks different on real screens than the computed ratios suggest | Contrast test and axe in both themes, plus a manual review on a laptop and a phone before merge. |
| Generated covers hurt scanning or look gimmicky | The 5-second scan test (5.7) gates Phase 2; fall back to spine-only covers in the grid. |
| Chart scope grows past what in-house SVG handles | Revisit with a separate decision before adopting a chart library (5.8). |
| Replacement-cost currency is unknown | Show bare decimals until the owner chooses a currency. |
| Scope creep from operations pages | Phase 6 uses existing tools; Phase 7 is gated on earlier phases. |
