# MVP UX Improvements Plan

> **Lifecycle:** implement these improvements as part of the matching phases in the [UI Implementation Plan](implementation-plan-ui.md). This plan does not add a separate release phase.

Status: proposed. These small, client-side additions improve the first UI release while using the API and shared components already planned.

## Improvements

### Catalog search and recovery

- Debounce text search by 300 ms and reset pagination to the first page whenever a search or filter changes.
- Keep search, filters, sort, and page in the URL so a user can refresh, share, or return to the same catalog view.
- Show the result count and active filters, with a clear-all action. When no books match, offer the same clear-filters action.
- Provide a manual retry when a catalog read fails. Do not automatically repeat a borrow or another mutation.

### Forms and feedback

- Add a keyboard-accessible show/hide control to password fields.
- On failed form submission, focus the first invalid field and retain the API's field-level error text.
- Announce action success and failure messages to assistive technology; use the planned toast and error components.
- Disable a mutation's submit control while its request is in flight to prevent accidental duplicate actions.

## Scope limits

These changes use existing endpoints and browser behavior; they require no API, database, deployment, or new dependency work. Keep the MVP focused: saved searches, favorites, notifications, real-time updates, and personalized recommendations are out of scope.

## Completion checks

- Catalog search waits 300 ms after typing, and search/filter changes return to page one.
- URL state restores the catalog view; the result count, active-filter chips, and clear-all action remain usable on mobile.
- Empty and failed catalog states provide the appropriate recovery action.
- Password visibility, first-invalid-field focus, and status announcements work with keyboard navigation and screen readers.
- Tests cover the debounce, filter reset and restore, manual retry, and accessible form feedback without adding backend changes.
