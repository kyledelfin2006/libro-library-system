# Web UI

The web UI in [`ui/`](../../ui) is a React 19 + strict TypeScript + Vite single-page app. It is a static build that talks to the API over the same origin, so the API's CORS stays off. It replaces the earlier UI execution plan; open items are at the end of this page.

Facts about the API stay in the controllers, OpenAPI, and the [Engineering Reference](../reference/engineering-reference.md). The loan rules are in the [Loan Implementation Guide](loan.md). The UI is not a security boundary: it hides what a role cannot do, and the server's route gates and `UserAccessPolicy` stay authoritative.

## Running and building

| Task | Command (from `ui/`) |
| --- | --- |
| Develop against a local API | `npm ci`, then `npm run dev`. Vite proxies `/app`, `/livez`, `/readyz`, `/v3/api-docs`, and `/swagger-ui` to `LIBRO_API_URL` (default `http://localhost:8080`) and strips the `Origin` header, because the API has CORS disabled. |
| Typecheck, lint, unit tests | `npm run typecheck`, `npm run lint`, `npm test` |
| Production build | `npm run build` (writes `ui/dist`) |
| Refresh the generated API types | `npm run gen:api` with the API running and `LIBRO_API_DOCS_ENABLED=true`. `ui/src/api/schema.d.ts` is generated; CI fails on drift. |
| Rebuild brand assets | `npm run build:brand` |
| Generate the release history | `npm run build:history` (needs `GITHUB_REPOSITORY` and `GITHUB_TOKEN`; empty otherwise) |
| End-to-end journey | `npm run test:e2e`, see [Testing](#testing) |

## Pages and roles

| Route | Who | What it does |
| --- | --- | --- |
| `/signin`, `/register` | Signed-out visitors | Sign in with a 429 countdown; student registration with course and major rules and a live password checklist. |
| `/change-password` | Signed in | Forced when `mustChangePassword` is true. The server does not block other routes, so this is a UI guard. |
| `/status` | Public | Polls `/livez` and `/readyz` without credentials and draws the request path. It claims only what the probes show. |
| `/catalog`, `/catalog/:id` | All roles | Grid and list views, debounced title search, filters, sort, pagination, all in the URL. Borrow on book detail for STUDENT and FACULTY only. |
| `/loans` | All roles | My Loans: loan slots, current loans with Return (patrons only), paginated history. |
| `/account` | All roles | Profile, password change, and a danger zone for STUDENT and FACULTY. |
| `/dashboard`, `/staff/loans`, `/inventory` | Staff | KPI tiles and charts; loan management with Mark as lost; inventory CRUD. |
| `/accounts` | Staff | Role-scoped directory; edit, staff password reset, delete; librarian-only creation of faculty and library assistants. |
| `/audit` | Staff | Filterable change history. The server scopes it: assistants never see events about assistants or librarians, and are not offered those filters. |
| `/version`, `/releases` | Staff | Running build from `/app/info`; release history generated at build time. |

STUDENT is a student patron, FACULTY a faculty patron, ADMINISTRATOR a library assistant, and LIBRARIAN the librarian; the UI says "library assistant", never "administrator". Staff cannot borrow or return, by the backend's design, so there is no assistant-driven borrow or return.

On screens narrower than 1024 px the primary navigation collapses behind a Menu button (Escape closes it, and choosing a link closes it). Tables become record cards below 640 px.

## How it is built

```text
ui/src/
  api/         typed client (envelope unwrapping, ErrorResponse normalization, in-memory token, 401 handling, Retry-After),
               per-feature request modules, generated schema.d.ts
  auth/        AuthProvider, route guards, permissions.ts (capability table and account-management rules)
  components/  shared UI: Button, TextField, Dialog, Toast, Pagination, charts, diagrams, Shell
  lib/         pure helpers: dates (Manila time), catalog and audit params, password generator, status model
  pages/       one file per route, with tests beside them
  styles/      design tokens and component CSS
  theme/       dark-first theme provider (dark, light, system)
```

- **Token.** The 15-minute Bearer token lives in memory only. A reload means signing in again. There are no refresh tokens, by design.
- **Role.** Navigation comes from `GET /app/users/me`, never from the token. A 401 clears state and returns to sign-in with the intended destination kept; a 404 on `/me` means the account was deleted.
- **Permissions.** `permissions.ts` mirrors `SecurityConfig` and `UserAccessPolicy` (`canManage`, `visibleRoles`, `canEdit`, `canReset`, `canDelete`, `permissionMatrix`). A test asserts the caller-by-target matrix.
- **State in the URL.** Catalog, loans, accounts, and audit filters, page, size, and the open detail panel live in the query string, so views can be shared and the back button works. Any filter change returns to page one and closes the panel.
- **Time.** Stored values are Manila local time with no offset and are formatted from the string, never through the browser's zone.
- **Errors.** Branch on HTTP status and the stable `code`, never on English text. `requestId` is shown on unexpected errors. Mutations are never retried automatically.
- **Secrets.** A staff-reset temporary password is held in local state and sent with a direct call (not `useMutation`, whose cache keeps variables); tests assert it never reaches URLs, storage, caches, or toasts.
- **Design.** One token file drives dark-first and light themes; a contrast test checks every color pair in both themes. Charts and diagrams are in-house SVG with a "View as table" alternative.

## Testing

- **Vitest and Testing Library** cover components, role gating, error mapping, URL state, the generated-type contract, and axe checks in both themes. jsdom cannot compute color contrast, so the token test covers that.
- **Playwright** (`ui/e2e/journey.spec.ts`) runs one journey in a real browser against a real API and database: a student registers, signs in, searches, borrows and returns a book, borrows a second one, and staff mark that loan lost. It also runs axe (including contrast) in both themes on the pages it visits, and checks that the navigation collapses on a phone-width screen. It creates its own books and student, but it writes to the database, so use a disposable one.
  - Locally: start the API, then set `E2E_STAFF_ID` and `E2E_STAFF_PASSWORD` for a librarian on it and run `npm run test:e2e`. Set `E2E_BROWSER_CHANNEL=chrome` (or `msedge`) to use an installed browser instead of downloading one; `npx playwright install chromium` otherwise. Vite starts on port 5173 unless `E2E_BASE_URL` points at a running UI.
  - CI: the `e2e-test` job starts PostgreSQL and the API, creates a librarian with the startup bootstrap and a random one-run password, and runs the journey.

## Delivery

- **Production.** The API image does not contain the UI. The Caddy proxy serves `ui/dist`, bind-mounted read-only at `/srv/ui`, with an SPA fallback and a strict CSP (see the [Caddyfile](../../infra/caddy/Caddyfile)). The `ui` job in [`cd.yml`](../../.github/workflows/cd.yml) builds `ui/dist` from the validated commit and, after `deploy.sh` succeeds, the deploy job streams it to the VM and runs [`scripts/install-ui.sh`](../../scripts/install-ui.sh), which updates the directory in place.
- **Release history.** `npm run build:history` writes `public/release-history.json` from GitHub Releases and successful CD runs during the CD build. The page says when it was generated. In local development, or if GitHub cannot be reached, it shows an empty state.
- **Metrics.** The optional `metrics` Compose profile runs Prometheus and Grafana against the API's private management port (see the README's Docker section). Set `VITE_METRICS_URL` when building to show a dashboard link on the Version page.
- **CSP.** `style-src` allows `'unsafe-inline'` because React sets inline styles on covers and charts. Scripts are same-origin only.

## Open items

- **5-second cover scan test (human).** In a 5-second look at 12 catalog cards, participants should find a named book and tell available from on loan. If cover text hurts that, drop the title from the `md` covers and keep it in the card text. This needs people, not code, and has not been run.
- **Release history is only as good as the CD build.** It lists runs that published an image; it cannot tell which one is deployed.
- **Seeded data on a deployed host.** The demo seed runs through `scripts/seed-demo.sh`; nothing is deployed yet.
- **Dashboard load.** The staff dashboard issues nine small requests; this has not been measured under load.
- **Audit rows written before the actor-role fix** can show "Unknown" as the actor role. New rows record the real role.
