ALTER TABLE books RENAME COLUMN price TO replacement_cost;
ALTER INDEX idx_books_price RENAME TO idx_books_replacement_cost;

ALTER TABLE books
    ADD COLUMN description VARCHAR(2000);
