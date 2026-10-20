package app.book.controller;

import app.book.dto.BookRequestDTO;
import app.book.dto.BookPatchRequestDTO;
import app.book.dto.BookResponseDTO;
import app.book.dto.LibraryStatisticsDTO;
import app.book.mapper.BookMapper;
import app.book.service.BookService;
import app.global.config.ErrorApiResponse;
import app.global.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/app/books")
@Tag(name = "Books", description = "Book catalog, search, filtering, statistics, and health operations")
public class BookAPI {

    private final BookService service;
    private final BookMapper mapper;

    public BookAPI(BookService service, BookMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/health")
    @Operation(summary = "Check API and database health",
            description = "Runs a database query to confirm the database is reachable. A successful response includes true api and database statuses; a database failure returns the standard error response.")
    @ErrorApiResponse(responseCode = "500", description = "Database health query failed")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> healthCheck() {
        Map<String, Boolean> status = Map.of("api", true, "database", service.isDatabaseReachable());
        return ResponseEntity.ok(new ApiResponse<>(true, "Health check", status));
    }

    /** Returns catalog totals based on the recorded replacement amounts for physical copies. */
    @GetMapping("/stats")
    @Operation(summary = "Get library statistics",
            description = "Returns the collection count and total recorded replacement cost, plus the copy with the highest replacement cost. The highest-cost copy is null when the collection is empty.")
    public ResponseEntity<LibraryStatisticsDTO> getStats() {
        return ResponseEntity.ok(service.getLibraryStatistics());
    }

    /** Searches text fields by substring and replacement cost by an exact decimal amount. */
    @GetMapping("/search")
    @Operation(summary = "Search books",
            description = "Search title, author, or genre with a case-insensitive substring. Replacement-cost searches require an exact numeric value.")
    @ErrorApiResponse(responseCode = "400", description = "Unsupported search type or invalid replacement-cost value")
    public ResponseEntity<List<BookResponseDTO>> searchBooks(
            @Parameter(description = "Field to search: author, title, genre, or replacementCost.", example = "title", schema = @Schema(allowableValues = {"author", "title", "genre", "replacementCost"}))
            @RequestParam String type,
            @Parameter(description = "Text fragment for title, author, or genre; exact decimal value for replacement cost.", example = "1984")
            @RequestParam String value) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.searchBooks(type, value)));
    }

    /** Filters copies using an inclusive maximum recorded replacement amount. */
    @GetMapping("/budget")
    @Operation(summary = "Find books by maximum replacement cost",
            description = "Returns all copies whose recorded replacement cost is at or below maxReplacementCost, inclusively. Results are unpaginated.")
    @ErrorApiResponse(responseCode = "400", description = "maxReplacementCost is not a valid decimal")
    public ResponseEntity<List<BookResponseDTO>> budgetBooks(
            @Parameter(description = "Inclusive maximum replacement cost.", example = "25.00")
            @RequestParam BigDecimal maxReplacementCost) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.getBooksAtOrBelowReplacementCost(maxReplacementCost)));
    }

    /** Creates one physical copy after request DTO validation and maps it to the public response shape. */
    @PostMapping("/add")
    @Operation(summary = "Add a book",
            description = "Library assistants and the librarian may add books. Creates a physical book-copy record; duplicate titles are allowed and each copy receives its own generated ID. Description is optional; replacement cost is required.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Book created")
    @ErrorApiResponse(responseCode = "400", description = "Request validation failed")
    @ErrorApiResponse(responseCode = "403", description = "Only library assistants and the librarian may add books")
    public ResponseEntity<ApiResponse<BookResponseDTO>> addBook(
            @Valid @RequestBody BookRequestDTO input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Book Added Successfully",
                        mapper.toResponseDTO(service.addBook(input))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a book by ID")
    @ErrorApiResponse(responseCode = "400", description = "Book ID must be a number")
    @ErrorApiResponse(responseCode = "404", description = "Book not found")
    public ResponseEntity<BookResponseDTO> getBookById(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponseDTO(service.findBookById(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a book", description = "Only library assistants and the librarian may delete books. A physical copy referenced by any loan history cannot be deleted.")
    @ErrorApiResponse(responseCode = "400", description = "Book ID must be a number")
    @ErrorApiResponse(responseCode = "404", description = "Book not found")
    @ErrorApiResponse(responseCode = "403", description = "Only library assistants and the librarian may delete books")
    @ErrorApiResponse(responseCode = "409", description = "Book copy has loan history and must be retained")
    public ResponseEntity<ApiResponse<Void>> deleteBook(@PathVariable Long id) {
        service.deleteBookById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Book deleted successfully"));
    }

    @GetMapping("/all")
    @Operation(summary = "List books with pagination",
            description = "Page numbering starts at zero. The default is page 0, size 12, sorted by id ascending; the maximum page size is 100. Sort with sort=property,direction using id, title, author, genre, or replacementCost. Unsupported sort properties return 400.")
    @ErrorApiResponse(responseCode = "400", description = "Invalid pagination or sort parameter")
    public ResponseEntity<Page<BookResponseDTO>> getAllBooks(
            @ParameterObject @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(service.getBooks(pageable).map(mapper::toResponseDTO));
    }

    /** Validates the replacement-cost bounds and delegates pagination and filtering to the service. */
    @GetMapping("/query")
    @Operation(summary = "Query books with pagination",
            description = "Optional title, author, and genre filters use case-insensitive substrings and combine with AND. Replacement-cost bounds are inclusive and may be supplied separately. With no filters, returns all books. Pages start at zero; default page 0, size 12, id ascending; maximum size 100. Sort by id, title, author, genre, or replacementCost with sort=property,direction. Invalid ranges or sort fields return 400.")
    @Parameters({
            @Parameter(name = "page", description = "Zero-based page index; default 0.", schema = @Schema(type = "integer", defaultValue = "0")),
            @Parameter(name = "size", description = "Page size; default 12, maximum 100.", schema = @Schema(type = "integer", defaultValue = "12", maximum = "100")),
            @Parameter(name = "sort", description = "property,direction; properties: id, title, author, genre, replacementCost. Direction: asc or desc.", example = "replacementCost,desc")
    })
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Matching book page", useReturnTypeSchema = true)
    @ErrorApiResponse(responseCode = "400", description = "Invalid decimal, replacement-cost range, pagination, or sort field")
    public ResponseEntity<Page<BookResponseDTO>> queryBooks(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String genre,
            @Parameter(description = "Inclusive minimum replacement cost; may be used without maxReplacementCost.", example = "10.00")
            @RequestParam(required = false) BigDecimal minReplacementCost,
            @Parameter(description = "Inclusive maximum replacement cost; may be used without minReplacementCost.", example = "25.00")
            @RequestParam(required = false) BigDecimal maxReplacementCost,
            @ParameterObject @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(service.queryBooks(title, author, genre, minReplacementCost, maxReplacementCost, pageable)
                .map(mapper::toResponseDTO));
    }

    @GetMapping("/sorted")
    @Operation(summary = "List books sorted by a field",
            description = "Returns an unpaginated list sorted ascending by title, author, id, replacementCost, or genre. The default field is title.")
    @ErrorApiResponse(responseCode = "400", description = "Unsupported sort field")
    public ResponseEntity<List<BookResponseDTO>> getSortedBooks(
            @Parameter(description = "Allowed values: title, author, id, replacementCost, genre.", schema = @Schema(allowableValues = {"title", "author", "id", "replacementCost", "genre"}))
            @RequestParam(required = false, defaultValue = "title") String category) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.getBooksSortedBy(category)));
    }

    @GetMapping("/genre")
    @Operation(summary = "Get genre distribution",
            description = "Returns a map from each genre name to the number of book records in that genre.")
    public ResponseEntity<Map<String, Long>> getGenre() {
        return ResponseEntity.ok(service.getGenreDistribution());
    }

    /** Returns copies whose recorded replacement amounts fall within the inclusive range. */
    @GetMapping("/replacement-cost")
    @Operation(summary = "Filter books by replacement-cost range",
            description = "Returns copies with replacement costs between minReplacementCost and maxReplacementCost, inclusive. minReplacementCost must not exceed maxReplacementCost.")
    @ErrorApiResponse(responseCode = "400", description = "Invalid decimal value or minimum replacement cost exceeds maximum")
    public ResponseEntity<List<BookResponseDTO>> getReplacementCostRangedBooks(
            @Parameter(description = "Inclusive minimum replacement cost.", example = "10.00")
            @RequestParam BigDecimal minReplacementCost,
            @Parameter(description = "Inclusive maximum replacement cost.", example = "25.00")
            @RequestParam BigDecimal maxReplacementCost) {
        return ResponseEntity.ok(mapper.toResponseDTOList(
                service.getBooksInReplacementCostRange(minReplacementCost, maxReplacementCost)));
    }

    /** Returns the average replacement amount, using zero for an empty collection. */
    @GetMapping("/stats/average-replacement-cost")
    @Operation(summary = "Get average replacement cost",
            description = "Returns the average recorded replacement cost in the ApiResponse data field, or 0 when the collection is empty.")
    public ResponseEntity<ApiResponse<BigDecimal>> getAverageReplacementCost() {
        return ResponseEntity.ok(new ApiResponse<>(true,
                "Average Replacement Cost of Collection", service.getAverageReplacementCost()));
    }

    @GetMapping("/stats/count")
    @Operation(summary = "Get book count",
            description = "Returns the collection count in the ApiResponse data field.")
    public ResponseEntity<ApiResponse<Long>> getBooksCount() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book Collection Count", service.getBookCount()));
    }

    /** Applies only supplied catalog fields; null and blank description values preserve the current description. */
    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a book",
            description = "Library assistants and the librarian may update books. Updates only supplied fields. Omitted, null, or blank text fields remain unchanged; a supplied replacementCost must be greater than zero. Description is limited to 2,000 characters. Text values are trimmed before storage.")
    @ErrorApiResponse(responseCode = "400", description = "Invalid book ID or supplied field")
    @ErrorApiResponse(responseCode = "404", description = "Book not found")
    @ErrorApiResponse(responseCode = "403", description = "Only library assistants and the librarian may update books")
    public ResponseEntity<ApiResponse<BookResponseDTO>> patchBook(
            @PathVariable Long id,
            @RequestBody BookPatchRequestDTO updates) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book updated successfully",
                mapper.toResponseDTO(service.patchBook(id, updates))));
    }

    /** Replaces all catalog fields while retaining the loan-managed physical-copy status. */
    @PutMapping("/{id}")
    @Operation(summary = "Replace a book",
            description = "Library assistants and the librarian may update books. Replaces every mutable book field. Title, author, genre, and a positive replacementCost are required; description is optional and limited to 2,000 characters. Text values are trimmed before storage.")
    @ErrorApiResponse(responseCode = "400", description = "Invalid book ID or request validation failed")
    @ErrorApiResponse(responseCode = "404", description = "Book not found")
    @ErrorApiResponse(responseCode = "403", description = "Only library assistants and the librarian may update books")
    public ResponseEntity<ApiResponse<BookResponseDTO>> replaceBook(
            @PathVariable Long id,
            @Valid @RequestBody BookRequestDTO updates) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book updated successfully",
                mapper.toResponseDTO(service.replaceBook(id, updates))));
    }
}
