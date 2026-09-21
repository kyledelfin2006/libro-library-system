# Libro Domain Decisions

## Book means one physical borrowable copy

For the Libro prototype, a `Book` record represents one physical copy held by the library, not an abstract title with an inventory count.

This means:

- one database `Book` row corresponds to one physical item;
- one physical item can be borrowed by only one person at a time;
- separate physical copies of the same title are stored as separate `Book` records with different generated IDs;
- duplicate title/author combinations are therefore allowed and do not represent accidental duplicates by themselves;
- the future loan model should reference `Book.id` and enforce at most one active loan for each book copy.

### Institutional interpretation

This decision matches the current institutional prototype context: the library tracks the individual item that is issued to a borrower. A loan is therefore an assignment of one available physical copy to one borrower, rather than a reservation against a title-level stock count.

### Consequences for future loans

Before loan endpoints are implemented, the system should add a loan entity that references both the borrower and the physical `Book` record. The persistence model should enforce the one-active-loan rule through application validation and, where practical, a database constraint or partial unique index over active loans for the same `book_id`.

If future requirements need title-level inventory, reporting, ISBN metadata, or multiple editions, introduce a separate title/work model and link individual `Book` copies to it. Do not add `availableCopies` or `totalCopies` directly to the current `Book` entity without revisiting this decision.
