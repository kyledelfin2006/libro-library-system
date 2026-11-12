export interface paths {
    "/app/audit": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List audit events
         * @description Librarians see all events. Assistants see student and faculty account events plus book and loan events. Dates use Asia/Manila local time.
         */
        get: operations["list"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/auth/login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Log in and receive an access token
         * @description Authenticates a university ID and password. Invalid credentials receive the same generic 401 response. The access token expires within 15 minutes and is returned with no-store caching.
         */
        post: operations["login"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/books/add": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Add a book
         * @description Library assistants and the librarian may add books. Creates a physical book-copy record; duplicate titles are allowed and each copy receives its own generated ID. Description is optional; replacement cost is required.
         */
        post: operations["addBook"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/books/all": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List books with pagination
         * @description Page numbering starts at zero. The default is page 0, size 12, sorted by id ascending; the maximum page size is 100. Sort with sort=property,direction using id, title, author, genre, or replacementCost. Unsupported sort properties return 400.
         */
        get: operations["getAllBooks"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/books/budget": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Find books by maximum replacement cost
         * @description Returns all copies whose recorded replacement cost is at or below maxReplacementCost, inclusively. Results are paginated: page numbering starts at zero, default size 12, maximum 100.
         */
        get: operations["budgetBooks"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/books/genre": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get genre distribution
         * @description Returns a map from each genre name to the number of book records in that genre.
         */
        get: operations["getGenre"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/books/query": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Query books with pagination
         * @description Optional title, author, genre, copyStatus, and advisory available filters combine with AND. Text filters use case-insensitive substrings; replacement-cost bounds are inclusive. With no filters, returns all books. Pages start at zero; default page 0, size 12, id ascending; maximum size 100. Sort by id, title, author, genre, or replacementCost with sort=property,direction. Invalid ranges or sort fields return 400.
         */
        get: operations["queryBooks"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/books/replacement-cost": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Filter books by replacement-cost range
         * @description Returns copies with replacement costs between minReplacementCost and maxReplacementCost, inclusive. minReplacementCost must not exceed maxReplacementCost. Results are paginated: page numbering starts at zero, default size 12, maximum 100.
         */
        get: operations["getReplacementCostRangedBooks"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/books/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Search books
         * @description Search title, author, or genre with a case-insensitive substring. Replacement-cost searches require an exact numeric value. Results are paginated: page numbering starts at zero, default size 12, maximum 100; sort with sort=property,direction.
         */
        get: operations["searchBooks"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/books/sorted": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List books sorted by a field
         * @description Returns a page sorted ascending by title, author, id, replacementCost, or genre. The default field is title. The category parameter decides the order; page and size are honored (default size 12, maximum 100).
         */
        get: operations["getSortedBooks"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/books/stats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get library statistics
         * @description Returns the collection count and total recorded replacement cost, plus the copy with the highest replacement cost. The highest-cost copy is null when the collection is empty.
         */
        get: operations["getStats"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/books/stats/average-replacement-cost": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get average replacement cost
         * @description Returns the average recorded replacement cost in the ApiResponse data field, or 0 when the collection is empty.
         */
        get: operations["getAverageReplacementCost"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/books/stats/count": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get book count
         * @description Returns the collection count in the ApiResponse data field.
         */
        get: operations["getBooksCount"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/books/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a book by ID */
        get: operations["getBookById"];
        /**
         * Replace a book
         * @description Library assistants and the librarian may update books. Replaces every mutable book field. Title, author, genre, and a positive replacementCost are required; description is optional and limited to 2,000 characters. Text values are trimmed before storage.
         */
        put: operations["replaceBook"];
        post?: never;
        /**
         * Delete a book
         * @description Only library assistants and the librarian may delete books. A physical copy referenced by any loan history cannot be deleted.
         */
        delete: operations["deleteBook"];
        options?: never;
        head?: never;
        /**
         * Partially update a book
         * @description Library assistants and the librarian may update books. Updates only supplied fields. Omitted, null, or blank text fields remain unchanged; a supplied replacementCost must be greater than zero. Description is limited to 2,000 characters. Text values are trimmed before storage.
         */
        patch: operations["patchBook"];
        trace?: never;
    };
    "/app/info": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get running build information */
        get: operations["info"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/loans": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List library loans
         * @description Library assistants see student and faculty loans; librarians also see assistant loans. Filter by status, borrower university ID, or overdue=true (active loans past due now in Asia/Manila). Page numbers are zero-based; page size defaults to 12 and is capped at 100. Allowed sort fields are id, borrowedAt, dueAt, returnedAt, and status; directions are asc or desc. Without a requested sort, results use borrowedAt descending then id descending. For a supplied sort, id descending is appended only when id is not already included.
         */
        get: operations["staffLoans"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/loans/activity": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get weekly loan activity
         * @description Manila weeks start Monday; missing weeks have zero counts. Staff scope matches the loan directory.
         */
        get: operations["activity"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/loans/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * View my loan history
         * @description Returns active, returned, and lost loans for the authenticated caller. Page numbers are zero-based; page size defaults to 12 and is capped at 100. Allowed sort fields are id, borrowedAt, dueAt, returnedAt, and status; directions are asc or desc. Without a requested sort, results use borrowedAt descending then id descending. For a supplied sort, id descending is appended only when id is not already included. Overdue is calculated at request time.
         */
        get: operations["myHistory"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/loans/top-titles": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get most borrowed titles
         * @description Counts checkouts in a rolling Manila-time window; ties sort by title and author. Staff scope matches the loan directory.
         */
        get: operations["topTitles"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/loans/users/{universityId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * View a patron's loan history
         * @description Library assistants may view student/faculty histories. Librarians may also view library-assistant histories. Neither staff role may view another librarian's history. Page numbers are zero-based; page size defaults to 12 and is capped at 100. Allowed sort fields are id, borrowedAt, dueAt, returnedAt, and status; directions are asc or desc. Without a requested sort, results use borrowedAt descending then id descending. For a supplied sort, id descending is appended only when id is not already included.
         */
        get: operations["patronHistory"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/loans/{bookId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Borrow a book copy
         * @description Students and faculty may borrow an in-collection physical copy. Borrowing is blocked when the caller has an overdue loan or already has three active loans. Due time is 14 calendar days after checkout in Asia/Manila local time.
         */
        post: operations["borrow"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/loans/{loanId}/lost": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Mark a loan as lost
         * @description Library assistants may resolve student/faculty loans as lost. Librarians may also resolve library-assistant loans. The physical copy becomes unavailable, and the loan remains in history with the staff member and time recorded.
         */
        put: operations["markLost"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/loans/{loanId}/return": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Return a borrowed book
         * @description A student or faculty member can return only their own active loan. The returned event remains in history.
         */
        put: operations["returnLoan"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/users": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List accounts with pagination
         * @description Library assistants may list student and school faculty profiles. The librarian may also list library assistant profiles. Optional role and q filters stay inside that scope; q matches a case-insensitive literal substring of ID, name, or email. An out-of-scope role returns 403. Page numbering starts at zero. The default is page 0, size 12, sorted by universityId ascending; the maximum page size is 100. Sort with sort=property,direction using universityId, firstName, lastName, middleInitial, email, userRole, userCourse, or major. Unsupported sort properties return 400.
         */
        get: operations["getAllUsers"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/users/administrators": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Create a library assistant account
         * @description Only the library librarian may create a library assistant account. The submitted role is ignored and the account is always ADMINISTRATOR.
         */
        post: operations["createAdministrator"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/users/faculty": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Create a faculty account
         * @description Creates a faculty account. Only librarians may use this operation; the submitted role is ignored and the account is always FACULTY.
         */
        post: operations["createFaculty"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/users/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get my profile
         * @description All four roles may view their own current profile. The account is loaded from the Bearer token subject; deleted accounts return 404. Password data is never returned.
         */
        get: operations["getMyProfile"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/users/signup": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Sign up for a user account
         * @description Creates a student account; any supplied role is ignored. Students must provide a course, and IT students must provide an approved major. Passwords must be 8–72 characters and include uppercase and lowercase letters, a number, and a symbol. Names are trimmed and email is stored lowercase.
         */
        post: operations["createUser"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/users/{universityId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a user by university ID
         * @description Users may view their own profile. Library assistants and the librarian may also view student and school faculty profiles; only the librarian may view library assistant profiles. Passwords and password hashes are never included.
         */
        get: operations["getUserByUniversityId"];
        /**
         * Replace a user's profile
         * @description Users may replace their own profile; library assistants and the librarian may also replace student and school faculty profiles, while only the librarian may replace library assistant profiles. Replaces first name, last name, middle initial, and email. First name, last name, and email are required; university ID, role, course, major, and password are not changed. Names are trimmed and email is stored lowercase.
         */
        put: operations["replaceUserProfile"];
        post?: never;
        /**
         * Delete a user account
         * @description Students and school faculty may delete their own accounts. Library assistants may delete student and faculty accounts; the librarian may also delete library assistant accounts. Librarian accounts are protected.
         */
        delete: operations["deleteUser"];
        options?: never;
        head?: never;
        /**
         * Update supplied profile fields
         * @description Users may update their own profile; library assistants and the librarian may also update student or school faculty profiles, while only the librarian may update library assistant profiles. Updates only supplied fields; null or omitted fields are unchanged. Supplied names and email cannot be blank. A blank middle initial is treated as omitted. Values are trimmed and email is stored lowercase.
         */
        patch: operations["updateUser"];
        trace?: never;
    };
    "/app/users/{universityId}/password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Change a user's password
         * @description Account owners provide their current password and a new password. A successful change clears mustChangePassword. Staff-assisted recovery uses the separate reset route. The new password must meet the 8–72 character complexity policy; password values are never returned.
         */
        put: operations["updatePassword"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/app/users/{universityId}/password/reset": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Reset a managed account password
         * @description Library assistants may reset student and faculty accounts; librarians may also reset library assistants. Staff cannot reset their own or a librarian account. The temporary password is never returned, and the account receives mustChangePassword=true until its owner changes it.
         */
        put: operations["resetPassword"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /** @description Success envelope returned by every successful API operation. Errors use ErrorResponse instead. */
        ApiResponseBigDecimal: {
            /** @description Operation result. The schema is specialized to each endpoint's response type. */
            data?: number;
            /** @description Human-readable operation result. */
            message?: string;
            /** @description Whether the operation completed successfully. */
            success?: boolean;
            /**
             * Format: int64
             * @description Response creation time as Unix epoch milliseconds.
             */
            timestamp?: number;
        };
        /** @description Success envelope returned by every successful API operation. Errors use ErrorResponse instead. */
        ApiResponseBookResponseDTO: {
            /** @description Operation result. The schema is specialized to each endpoint's response type. */
            data?: components["schemas"]["BookResponseDTO"];
            /** @description Human-readable operation result. */
            message?: string;
            /** @description Whether the operation completed successfully. */
            success?: boolean;
            /**
             * Format: int64
             * @description Response creation time as Unix epoch milliseconds.
             */
            timestamp?: number;
        };
        /** @description Success envelope returned by every successful API operation. Errors use ErrorResponse instead. */
        ApiResponseBuildInfoDTO: {
            /** @description Operation result. The schema is specialized to each endpoint's response type. */
            data?: components["schemas"]["BuildInfoDTO"];
            /** @description Human-readable operation result. */
            message?: string;
            /** @description Whether the operation completed successfully. */
            success?: boolean;
            /**
             * Format: int64
             * @description Response creation time as Unix epoch milliseconds.
             */
            timestamp?: number;
        };
        /** @description Success envelope returned by every successful API operation. Errors use ErrorResponse instead. */
        ApiResponseLibraryStatisticsDTO: {
            /** @description Operation result. The schema is specialized to each endpoint's response type. */
            data?: components["schemas"]["LibraryStatisticsDTO"];
            /** @description Human-readable operation result. */
            message?: string;
            /** @description Whether the operation completed successfully. */
            success?: boolean;
            /**
             * Format: int64
             * @description Response creation time as Unix epoch milliseconds.
             */
            timestamp?: number;
        };
        /** @description Success envelope returned by every successful API operation. Errors use ErrorResponse instead. */
        ApiResponseLoanResponseDTO: {
            /** @description Operation result. The schema is specialized to each endpoint's response type. */
            data?: components["schemas"]["LoanResponseDTO"];
            /** @description Human-readable operation result. */
            message?: string;
            /** @description Whether the operation completed successfully. */
            success?: boolean;
            /**
             * Format: int64
             * @description Response creation time as Unix epoch milliseconds.
             */
            timestamp?: number;
        };
        /** @description Success envelope returned by every successful API operation. Errors use ErrorResponse instead. */
        ApiResponseLong: {
            /**
             * Format: int64
             * @description Operation result. The schema is specialized to each endpoint's response type.
             */
            data?: number;
            /** @description Human-readable operation result. */
            message?: string;
            /** @description Whether the operation completed successfully. */
            success?: boolean;
            /**
             * Format: int64
             * @description Response creation time as Unix epoch milliseconds.
             */
            timestamp?: number;
        };
        /** @description Success envelope returned by every successful API operation. Errors use ErrorResponse instead. */
        ApiResponseMapStringLong: {
            /** @description Operation result. The schema is specialized to each endpoint's response type. */
            data?: {
                [key: string]: number;
            };
            /** @description Human-readable operation result. */
            message?: string;
            /** @description Whether the operation completed successfully. */
            success?: boolean;
            /**
             * Format: int64
             * @description Response creation time as Unix epoch milliseconds.
             */
            timestamp?: number;
        };
        /** @description Success envelope returned by every successful API operation. Errors use ErrorResponse instead. */
        ApiResponsePageBookResponseDTO: {
            /** @description Operation result. The schema is specialized to each endpoint's response type. */
            data?: components["schemas"]["PageBookResponseDTO"];
            /** @description Human-readable operation result. */
            message?: string;
            /** @description Whether the operation completed successfully. */
            success?: boolean;
            /**
             * Format: int64
             * @description Response creation time as Unix epoch milliseconds.
             */
            timestamp?: number;
        };
        /** @description Success envelope returned by every successful API operation. Errors use ErrorResponse instead. */
        ApiResponsePageLoanResponseDTO: {
            /** @description Operation result. The schema is specialized to each endpoint's response type. */
            data?: components["schemas"]["PageLoanResponseDTO"];
            /** @description Human-readable operation result. */
            message?: string;
            /** @description Whether the operation completed successfully. */
            success?: boolean;
            /**
             * Format: int64
             * @description Response creation time as Unix epoch milliseconds.
             */
            timestamp?: number;
        };
        /** @description Success envelope returned by every successful API operation. Errors use ErrorResponse instead. */
        ApiResponsePageUserResponseDTO: {
            /** @description Operation result. The schema is specialized to each endpoint's response type. */
            data?: components["schemas"]["PageUserResponseDTO"];
            /** @description Human-readable operation result. */
            message?: string;
            /** @description Whether the operation completed successfully. */
            success?: boolean;
            /**
             * Format: int64
             * @description Response creation time as Unix epoch milliseconds.
             */
            timestamp?: number;
        };
        /** @description Success envelope returned by every successful API operation. Errors use ErrorResponse instead. */
        ApiResponseTokenResponseDTO: {
            /** @description Operation result. The schema is specialized to each endpoint's response type. */
            data?: components["schemas"]["TokenResponseDTO"];
            /** @description Human-readable operation result. */
            message?: string;
            /** @description Whether the operation completed successfully. */
            success?: boolean;
            /**
             * Format: int64
             * @description Response creation time as Unix epoch milliseconds.
             */
            timestamp?: number;
        };
        /** @description Success envelope returned by every successful API operation. Errors use ErrorResponse instead. */
        ApiResponseUserResponseDTO: {
            /** @description Operation result. The schema is specialized to each endpoint's response type. */
            data?: components["schemas"]["UserResponseDTO"];
            /** @description Human-readable operation result. */
            message?: string;
            /** @description Whether the operation completed successfully. */
            success?: boolean;
            /**
             * Format: int64
             * @description Response creation time as Unix epoch milliseconds.
             */
            timestamp?: number;
        };
        /** @description Success envelope returned by every successful API operation. Errors use ErrorResponse instead. */
        ApiResponseVoid: {
            /** @description Operation result. The schema is specialized to each endpoint's response type. */
            data?: unknown;
            /** @description Human-readable operation result. */
            message?: string;
            /** @description Whether the operation completed successfully. */
            success?: boolean;
            /**
             * Format: int64
             * @description Response creation time as Unix epoch milliseconds.
             */
            timestamp?: number;
        };
        /** @description Fields to update on a book. Omitted or null values are unchanged; blank text values are also ignored. */
        BookPatchRequestDTO: {
            /** @description Replacement author. Surrounding whitespace is trimmed. */
            author?: string;
            /** @description Optional catalog summary. Surrounding whitespace is trimmed; blank values are ignored and the maximum is 2,000 characters. */
            description?: string;
            /** @description Replacement genre. Surrounding whitespace is trimmed. */
            genre?: string;
            /** @description Replacement amount for this physical copy; when supplied it must be greater than zero. */
            replacementCost?: number;
            /** @description Replacement title. Surrounding whitespace is trimmed. */
            title?: string;
        };
        /** @description Complete book data used when creating or replacing a book. Title, author, genre, and replacement cost are required; description is optional. */
        BookRequestDTO: {
            /** @description Author name. Surrounding whitespace is trimmed before persistence. */
            author: string;
            /** @description Optional catalog summary. Surrounding whitespace is trimmed; maximum 2,000 characters. */
            description?: string;
            /** @description Book genre. Surrounding whitespace is trimmed before persistence. */
            genre: string;
            /** @description Positive decimal amount used to replace this physical copy. */
            replacementCost: number;
            /** @description Book title. Surrounding whitespace is trimmed before persistence. */
            title: string;
        };
        /** @description Public book representation. Persistence-only fields are omitted. */
        BookResponseDTO: {
            /** @description Normalized author name. */
            author?: string;
            /** @description Advisory availability at read time; checkout still enforces loan rules. */
            available?: boolean;
            /**
             * @description Physical-copy disposition. IN_COLLECTION does not mean available when an active loan exists.
             * @enum {string}
             */
            copyStatus?: "IN_COLLECTION" | "LOST";
            /** @description Optional catalog summary. */
            description?: string;
            /** @description Normalized genre. */
            genre?: string;
            /**
             * Format: int64
             * @description Generated book identifier.
             */
            id?: number;
            /** @description Positive decimal amount used to replace this physical copy. */
            replacementCost?: number;
            /** @description Normalized book title. */
            title?: string;
        };
        BuildInfoDTO: {
            buildTime?: string;
            /** @description Short source commit or unknown locally */
            commit?: string;
            environment?: string;
            version?: string;
        };
        /** @description Standard error response. Validation failures include fieldErrors keyed by request or entity field; non-field errors use _global. */
        ErrorResponse: {
            /** @description Stable upper snake case business code, or null when no specific code applies. */
            code?: string | null;
            /** @description Client-safe explanation. Does not include database internals or stack traces. */
            details?: string;
            /** @description Error category; varies with HTTP status. */
            error?: string;
            /** @description Field name to validation message mapping for validation errors; usually empty for other statuses. _global identifies object-level errors. */
            fieldErrors?: {
                [key: string]: string;
            };
            /** @description Correlation ID, also returned in the X-Request-Id header. Quote it when reporting a problem. */
            requestId?: string;
            /**
             * Format: int32
             * @description Matches the HTTP response status.
             */
            statusCode?: number;
            /**
             * Format: int64
             * @description Response creation time as Unix epoch milliseconds.
             */
            timestamp?: number;
        };
        LibraryStatisticsDTO: {
            /** @description Book with the highest replacement cost, or null when the collection is empty. */
            highestReplacementCostBook?: components["schemas"]["BookResponseDTO"];
            /**
             * Format: int64
             * @description Number of books in the collection.
             */
            totalBooks?: number;
            /** @description Sum of recorded replacement costs. */
            totalReplacementCost?: number;
        };
        /** @description One borrow event. Timestamps use Asia/Manila local time. */
        LoanResponseDTO: {
            /**
             * Format: int64
             * @description Physical book-copy identifier.
             */
            bookId?: number;
            /** @description Title of the borrowed physical copy. */
            bookTitle?: string;
            /**
             * Format: date-time
             * @description Time the copy was borrowed in Asia/Manila local time.
             */
            borrowedAt?: string;
            /** @description Borrower's university ID. Visible on own or role-authorized history. */
            borrowerUniversityId?: string;
            /**
             * Format: date-time
             * @description Due time in Asia/Manila local time.
             */
            dueAt?: string;
            /**
             * Format: int64
             * @description Loan identifier.
             */
            loanId?: number;
            /**
             * Format: date-time
             * @description Time staff recorded this copy as lost; null otherwise.
             */
            lostAt?: string | null;
            /** @description Staff member who recorded the copy as lost; null otherwise. */
            lostByUniversityId?: string | null;
            /** @description Calculated from active status and the current library time; not stored. */
            overdue?: boolean;
            /**
             * Format: date-time
             * @description Return time, or null until returned.
             */
            returnedAt?: string | null;
            /**
             * @description Lifecycle state; overdue is reported separately.
             * @enum {string}
             */
            status?: "ACTIVE" | "RETURNED" | "LOST";
        };
        /** @description University ID and password used to log in. The password is never returned. */
        LoginRequestDTO: {
            /** @description Password for this account. Maximum 72 characters and 72 UTF-8 bytes. */
            password: string;
            /** @description University ID in ####-#### format. */
            universityId: string;
        };
        PageBookResponseDTO: {
            content?: components["schemas"]["BookResponseDTO"][];
            empty?: boolean;
            first?: boolean;
            last?: boolean;
            /** Format: int32 */
            number?: number;
            /** Format: int32 */
            numberOfElements?: number;
            pageable?: components["schemas"]["PageableObject"];
            /** Format: int32 */
            size?: number;
            sort?: components["schemas"]["SortObject"];
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
        };
        PageLoanResponseDTO: {
            content?: components["schemas"]["LoanResponseDTO"][];
            empty?: boolean;
            first?: boolean;
            last?: boolean;
            /** Format: int32 */
            number?: number;
            /** Format: int32 */
            numberOfElements?: number;
            pageable?: components["schemas"]["PageableObject"];
            /** Format: int32 */
            size?: number;
            sort?: components["schemas"]["SortObject"];
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
        };
        PageUserResponseDTO: {
            content?: components["schemas"]["UserResponseDTO"][];
            empty?: boolean;
            first?: boolean;
            last?: boolean;
            /** Format: int32 */
            number?: number;
            /** Format: int32 */
            numberOfElements?: number;
            pageable?: components["schemas"]["PageableObject"];
            /** Format: int32 */
            size?: number;
            sort?: components["schemas"]["SortObject"];
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
        };
        PageableObject: {
            /** Format: int64 */
            offset?: number;
            /** Format: int32 */
            pageNumber?: number;
            /** Format: int32 */
            pageSize?: number;
            paged?: boolean;
            sort?: components["schemas"]["SortObject"];
            unpaged?: boolean;
        };
        /** @description Owner password-change request. The new password must be 8–72 characters and UTF-8 bytes and include uppercase and lowercase letters, a number, and a symbol. */
        PasswordChangeRequestDTO: {
            /** @description Current password, required for an owner change. */
            currentPassword: string;
            /** @description New password to store as a hash; maximum 72 UTF-8 bytes. */
            newPassword: string;
        };
        PasswordResetRequestDTO: {
            /** @description Temporary password meeting the normal complexity policy and 72-byte BCrypt limit. */
            temporaryPassword: string;
        };
        SortObject: {
            empty?: boolean;
            sorted?: boolean;
            unsorted?: boolean;
        };
        /** @description Short-lived access token. Keep the token secret and send it only in the Authorization header. */
        TokenResponseDTO: {
            /** @description Signed access token used in Bearer authorization. */
            accessToken?: string;
            /**
             * Format: int64
             * @description Token lifetime in seconds from issuance.
             */
            expiresIn?: number;
            /** @description True when the account must change its temporary password. */
            mustChangePassword?: boolean;
            tokenType?: string;
        };
        /** @description Account creation request. Students require a course and IT students require a major; faculty and library staff must leave course and major null. */
        UserCreateRequestDTO: {
            /** Format: email */
            email: string;
            firstName: string;
            lastName: string;
            /**
             * @description Required for IT students and omitted for all other users. Allowed values are SE, SMBPO, IST, and HN.
             * @enum {string}
             */
            major?: "SE" | "SMBPO" | "IST" | "HN";
            /** @description Optional middle initial. */
            middleInitial?: string;
            /** @description Password meeting the 8–72 character complexity policy and 72 UTF-8 byte BCrypt limit. Never returned by the API. */
            password: string;
            /** @description University ID in ####-#### format. */
            universityId: string;
            /**
             * @description Required for students. Allowed values are IT, EMC, and IS.
             * @enum {string}
             */
            userCourse?: "IT" | "EMC" | "IS";
            /**
             * @description Ignored by account-creation endpoints; each route assigns its fixed role. Only the librarian can create faculty or library assistant accounts.
             * @enum {string}
             */
            userRole?: "STUDENT" | "FACULTY" | "ADMINISTRATOR" | "LIBRARIAN";
        };
        /** @description Partial profile update. Omitted or null fields remain unchanged; supplied names and email must not be blank. A blank middle initial is treated as omitted. */
        UserPatchRequestDTO: {
            /**
             * Format: email
             * @description Optional replacement email; trimmed and stored lowercase.
             */
            email?: string;
            /** @description Optional replacement name; trimmed before storage. */
            firstName?: string;
            /** @description Optional replacement name; trimmed before storage. */
            lastName?: string;
            /** @description Optional one-letter middle initial. Blank input is ignored. */
            middleInitial?: string;
        };
        /** @description Complete profile replacement. First name, last name, and email are required. The university ID, role, course, major, and password are unchanged. */
        UserReplaceRequestDTO: {
            /** Format: email */
            email: string;
            firstName: string;
            lastName: string;
            /** @description Optional middle initial; blank input is stored as absent. */
            middleInitial?: string;
        };
        /** @description Public user profile. Passwords and password hashes are never included. */
        UserResponseDTO: {
            email?: string;
            firstName?: string;
            lastName?: string;
            /**
             * @description Present only for IT student accounts.
             * @enum {string}
             */
            major?: "SE" | "SMBPO" | "IST" | "HN";
            middleInitial?: string;
            /** @description True after a staff-assisted reset until this account changes its password. */
            mustChangePassword?: boolean;
            /** @description University identifier in ####-#### format. */
            universityId?: string;
            /**
             * @description Present for student accounts.
             * @enum {string}
             */
            userCourse?: "IT" | "EMC" | "IS";
            /** @enum {string} */
            userRole?: "STUDENT" | "FACULTY" | "ADMINISTRATOR" | "LIBRARIAN";
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    list: {
        parameters: {
            query?: {
                actor?: string;
                entityType?: string;
                actorRole?: string;
                targetRole?: "STUDENT" | "FACULTY" | "ADMINISTRATOR" | "LIBRARIAN";
                from?: string;
                to?: string;
                /** @description Zero-based page index (0..N) */
                page?: number;
                /** @description The size of the page to be returned */
                size?: number;
                /** @description Sorting criteria in the format: property,(asc|desc). Default sort order is ascending. Multiple sort criteria are supported. */
                sort?: string[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Invalid filter or sort */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Caller or role filter is outside staff scope */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    login: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LoginRequestDTO"];
            };
        };
        responses: {
            /** @description Credentials accepted; a short-lived Bearer token is returned */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseTokenResponseDTO"];
                };
            };
            /** @description Login request is missing a valid university ID or password */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description University ID or password is incorrect */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description This client has sent too many login requests */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    addBook: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BookRequestDTO"];
            };
        };
        responses: {
            /** @description Book created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseBookResponseDTO"];
                };
            };
            /** @description Request validation failed */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Only library assistants and the librarian may add books */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getAllBooks: {
        parameters: {
            query?: {
                /** @description Zero-based page index (0..N) */
                page?: number;
                /** @description The size of the page to be returned */
                size?: number;
                /** @description Sorting criteria in the format: property,(asc|desc). Default sort order is ascending. Multiple sort criteria are supported. */
                sort?: string[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Book page */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePageBookResponseDTO"];
                };
            };
            /** @description Invalid pagination or sort parameter */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    budgetBooks: {
        parameters: {
            query: {
                /** @description Inclusive maximum replacement cost. */
                maxReplacementCost: number;
                /** @description Zero-based page index (0..N) */
                page?: number;
                /** @description The size of the page to be returned */
                size?: number;
                /** @description Sorting criteria in the format: property,(asc|desc). Default sort order is ascending. Multiple sort criteria are supported. */
                sort?: string[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Affordable book page */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePageBookResponseDTO"];
                };
            };
            /** @description maxReplacementCost is not a valid decimal */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getGenre: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseMapStringLong"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    queryBooks: {
        parameters: {
            query?: {
                title?: string;
                author?: string;
                genre?: string;
                /** @description Inclusive minimum replacement cost; may be used without maxReplacementCost. */
                minReplacementCost?: number;
                /** @description Inclusive maximum replacement cost; may be used without minReplacementCost. */
                maxReplacementCost?: number;
                /** @description Physical copy status: IN_COLLECTION or LOST. */
                copyStatus?: "IN_COLLECTION" | "LOST";
                /** @description Current advisory checkout availability. */
                available?: boolean;
                /** @description Zero-based page index; default 0. */
                page?: number;
                /** @description Page size; default 12, maximum 100. */
                size?: number;
                /** @description property,direction; properties: id, title, author, genre, replacementCost. Direction: asc or desc. */
                sort?: string[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Matching book page */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePageBookResponseDTO"];
                };
            };
            /** @description Invalid decimal, replacement-cost range, pagination, or sort field */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getReplacementCostRangedBooks: {
        parameters: {
            query: {
                /** @description Inclusive minimum replacement cost. */
                minReplacementCost: number;
                /** @description Inclusive maximum replacement cost. */
                maxReplacementCost: number;
                /** @description Zero-based page index (0..N) */
                page?: number;
                /** @description The size of the page to be returned */
                size?: number;
                /** @description Sorting criteria in the format: property,(asc|desc). Default sort order is ascending. Multiple sort criteria are supported. */
                sort?: string[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Books in replacement-cost range */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePageBookResponseDTO"];
                };
            };
            /** @description Invalid decimal value or minimum replacement cost exceeds maximum */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    searchBooks: {
        parameters: {
            query: {
                /** @description Field to search: author, title, genre, or replacementCost. */
                type: "author" | "title" | "genre" | "replacementCost";
                /** @description Text fragment for title, author, or genre; exact decimal value for replacement cost. */
                value: string;
                /** @description Zero-based page index (0..N) */
                page?: number;
                /** @description The size of the page to be returned */
                size?: number;
                /** @description Sorting criteria in the format: property,(asc|desc). Default sort order is ascending. Multiple sort criteria are supported. */
                sort?: string[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Matching book page */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePageBookResponseDTO"];
                };
            };
            /** @description Unsupported search type or invalid replacement-cost value */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getSortedBooks: {
        parameters: {
            query?: {
                /** @description Allowed values: title, author, id, replacementCost, genre. */
                category?: "title" | "author" | "id" | "replacementCost" | "genre";
                /** @description Zero-based page index (0..N) */
                page?: number;
                /** @description The size of the page to be returned */
                size?: number;
                /** @description Sorting criteria in the format: property,(asc|desc). Default sort order is ascending. Multiple sort criteria are supported. */
                sort?: string[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Sorted book page */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePageBookResponseDTO"];
                };
            };
            /** @description Unsupported sort field */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getStats: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseLibraryStatisticsDTO"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getAverageReplacementCost: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseBigDecimal"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getBooksCount: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseLong"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getBookById: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Matching book copy */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseBookResponseDTO"];
                };
            };
            /** @description Book ID must be a number */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Book not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    replaceBook: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BookRequestDTO"];
            };
        };
        responses: {
            /** @description Replaced book copy */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseBookResponseDTO"];
                };
            };
            /** @description Invalid book ID or request validation failed */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Only library assistants and the librarian may update books */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Book not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    deleteBook: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Book deleted */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseVoid"];
                };
            };
            /** @description Book ID must be a number */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Only library assistants and the librarian may delete books */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Book not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Book copy has loan history and must be retained */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    patchBook: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BookPatchRequestDTO"];
            };
        };
        responses: {
            /** @description Updated book copy */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseBookResponseDTO"];
                };
            };
            /** @description Invalid book ID or supplied field */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Only library assistants and the librarian may update books */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Book not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    info: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseBuildInfoDTO"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    staffLoans: {
        parameters: {
            query?: {
                status?: "ACTIVE" | "RETURNED" | "LOST";
                overdue?: boolean;
                /** @description Borrower university ID in ####-#### format. */
                borrower?: string;
                /** @description Zero-based page index; default 0. */
                page?: number;
                /** @description Page size; default 12, maximum 100. */
                size?: number;
                /** @description property,direction; properties: id, borrowedAt, dueAt, returnedAt, status. Direction: asc or desc. */
                sort?: string[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Authorized loan page */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePageLoanResponseDTO"];
                };
            };
            /** @description Invalid filter or unsupported sort property */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Only authorized library staff may list these loans */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Borrower account not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    activity: {
        parameters: {
            query?: {
                /** @description Number of Monday-start weeks, 1 to 52. */
                weeks?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description weeks must be between 1 and 52 */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    myHistory: {
        parameters: {
            query?: {
                /** @description Zero-based page index (0..N) */
                page?: number;
                /** @description The size of the page to be returned */
                size?: number;
                /** @description Sorting criteria in the format: property,(asc|desc). Default sort order is ascending. Multiple sort criteria are supported. */
                sort?: string[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Own loan-history page */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePageLoanResponseDTO"];
                };
            };
            /** @description Unsupported sort property */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    topTitles: {
        parameters: {
            query?: {
                /** @description Rolling lookback in days, 1 to 365. */
                days?: number;
                /** @description Maximum title groups, 1 to 10. */
                limit?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description days must be 1-365 and limit must be 1-10 */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    patronHistory: {
        parameters: {
            query?: {
                /** @description Zero-based page index (0..N) */
                page?: number;
                /** @description The size of the page to be returned */
                size?: number;
                /** @description Sorting criteria in the format: property,(asc|desc). Default sort order is ascending. Multiple sort criteria are supported. */
                sort?: string[];
            };
            header?: never;
            path: {
                /** @description University ID of the patron whose history is requested. */
                universityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Authorized patron loan-history page */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePageLoanResponseDTO"];
                };
            };
            /** @description Unsupported sort property */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Caller cannot view this patron's history */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Patron account not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    borrow: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Identifier of one physical book copy. */
                bookId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Copy borrowed */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseLoanResponseDTO"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Only students and faculty may borrow */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Book copy not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Copy is already borrowed or lost, or borrower cannot borrow yet */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    markLost: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                loanId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Loan marked lost */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseLoanResponseDTO"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Caller cannot resolve this patron's loan */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Loan not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Loan is no longer active */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    returnLoan: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                loanId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Copy returned */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseLoanResponseDTO"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Only students and faculty may return loans */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Loan not found or not owned by caller */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Loan is already returned or marked lost */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getAllUsers: {
        parameters: {
            query?: {
                /** @description Optional target role within the caller's staff scope; LIBRARIAN is never listed. */
                role?: "STUDENT" | "FACULTY" | "ADMINISTRATOR" | "LIBRARIAN";
                /** @description Case-insensitive literal substring of university ID, first name, last name, or email. */
                q?: string;
                /** @description Zero-based page index (0..N) */
                page?: number;
                /** @description The size of the page to be returned */
                size?: number;
                /** @description Sorting criteria in the format: property,(asc|desc). Default sort order is ascending. Multiple sort criteria are supported. */
                sort?: string[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Authorized account page */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponsePageUserResponseDTO"];
                };
            };
            /** @description Invalid pagination or sort parameter */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Only library assistants and the librarian may list accounts */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    createAdministrator: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UserCreateRequestDTO"];
            };
        };
        responses: {
            /** @description Library assistant account created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseUserResponseDTO"];
                };
            };
            /** @description Request validation failed or university ID/email is already registered */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Only the librarian may create library assistant accounts */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    createFaculty: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UserCreateRequestDTO"];
            };
        };
        responses: {
            /** @description Faculty account created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseUserResponseDTO"];
                };
            };
            /** @description Request validation failed or university ID/email is already registered */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Only librarians may create faculty accounts */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getMyProfile: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Current public profile */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseUserResponseDTO"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Authenticated account no longer exists */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    createUser: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UserCreateRequestDTO"];
            };
        };
        responses: {
            /** @description User created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseUserResponseDTO"];
                };
            };
            /** @description Request validation failed or university ID/email is already registered */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description A database uniqueness constraint was concurrently violated */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description This client has sent too many signup requests */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getUserByUniversityId: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description University identifier in ####-#### format. */
                universityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Public account profile */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseUserResponseDTO"];
                };
            };
            /** @description University ID must use the ####-#### format */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Only the account owner or authorized staff may view this profile */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description User not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    replaceUserProfile: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description University identifier in ####-#### format. */
                universityId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UserReplaceRequestDTO"];
            };
        };
        responses: {
            /** @description Replaced public profile */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseUserResponseDTO"];
                };
            };
            /** @description Request validation failed, university ID is invalid, or email is already registered */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Only the account owner or authorized staff may replace this profile */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description User not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description A database uniqueness constraint was concurrently violated */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    deleteUser: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description University identifier in ####-#### format. */
                universityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Account deleted */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseVoid"];
                };
            };
            /** @description University ID must use the ####-#### format */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description The caller cannot delete the requested account */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description User not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Account has loan history and must be retained */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    updateUser: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description University identifier in ####-#### format. */
                universityId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UserPatchRequestDTO"];
            };
        };
        responses: {
            /** @description Updated public profile */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseUserResponseDTO"];
                };
            };
            /** @description Invalid profile field, university ID, or email is already registered */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Only the account owner or authorized staff may update this profile */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description User not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description A database uniqueness constraint was concurrently violated */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    updatePassword: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description University identifier in ####-#### format. */
                universityId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PasswordChangeRequestDTO"];
            };
        };
        responses: {
            /** @description Password changed */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseVoid"];
                };
            };
            /** @description Password format is invalid or the current password does not match */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Only the account owner or authorized staff may change this password */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description User not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    resetPassword: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description University identifier in ####-#### format. */
                universityId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PasswordResetRequestDTO"];
            };
        };
        responses: {
            /** @description Temporary password set */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseVoid"];
                };
            };
            /** @description Temporary password or university ID is invalid */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Bearer token is missing or invalid */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Caller cannot reset this account */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description User not found */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
}
