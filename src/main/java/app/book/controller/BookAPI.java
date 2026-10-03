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

    @GetMapping("/stats")
    @Operation(summary = "Get library statistics",
            description = "Returns the collection count and value, plus the most expensive book. The most expensive book is null when the collection is empty.")
    public ResponseEntity<LibraryStatisticsDTO> getStats() {
        return ResponseEntity.ok(service.getLibraryStatistics());
    }

    @GetMapping("/search")
    @Operation(summary = "Search books",
            description = "Search title, author, or genre with a case-insensitive substring. Price searches require an exact numeric value.")
    @ErrorApiResponse(responseCode = "400", description = "Unsupported search type or invalid price value")
    public ResponseEntity<List<BookResponseDTO>> searchBooks(
            @Parameter(description = "Field to search: author, title, genre, or price.", example = "title", schema = @Schema(allowableValues = {"author", "title", "genre", "price"}))
            @RequestParam String type,
            @Parameter(description = "Text fragment for title, author, or genre; exact decimal value for price.", example = "1984")
            @RequestParam String value) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.searchBooks(type, value)));
    }

    @GetMapping("/budget")
    @Operation(summary = "Find books within a budget",
            description = "Returns all books priced at or below maxPrice, inclusively. Results are unpaginated.")
    @ErrorApiResponse(responseCode = "400", description = "maxPrice is not a valid decimal")
    public ResponseEntity<List<BookResponseDTO>> budgetBooks(
            @Parameter(description = "Inclusive maximum price.", example = "25.00")
            @RequestParam BigDecimal maxPrice) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.getBooksWithinBudget(maxPrice)));
    }

    @PostMapping("/add")
    @Operation(summary = "Add a book",
            description = "Creates a physical book-copy record. Duplicate titles are allowed; each copy receives its own generated ID.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Book created")
    @ErrorApiResponse(responseCode = "400", description = "Request validation failed")
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
    @Operation(summary = "Delete a book")
    @ErrorApiResponse(responseCode = "400", description = "Book ID must be a number")
    @ErrorApiResponse(responseCode = "404", description = "Book not found")
    public ResponseEntity<ApiResponse<Void>> deleteBook(@PathVariable Long id) {
        service.deleteBookById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Book deleted successfully"));
    }

    @GetMapping("/all")
    @Operation(summary = "List books with pagination",
            description = "Page numbering starts at zero. The default is page 0, size 12, sorted by id ascending; the maximum page size is 100. Sort with sort=property,direction using id, title, author, genre, or price. Unsupported sort properties return 400.")
    @ErrorApiResponse(responseCode = "400", description = "Invalid pagination or sort parameter")
    public ResponseEntity<Page<BookResponseDTO>> getAllBooks(
            @ParameterObject @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(service.getBooks(pageable).map(mapper::toResponseDTO));
    }

    @GetMapping("/query")
    @Operation(summary = "Query books with pagination",
            description = "Optional title, author, and genre filters use case-insensitive substrings and combine with AND. Price bounds are inclusive and may be supplied separately. With no filters, returns all books. Pages start at zero; default page 0, size 12, id ascending; maximum size 100. Sort by id, title, author, genre, or price with sort=property,direction. Invalid ranges or sort fields return 400.")
    @Parameters({
            @Parameter(name = "page", description = "Zero-based page index; default 0.", schema = @Schema(type = "integer", defaultValue = "0")),
            @Parameter(name = "size", description = "Page size; default 12, maximum 100.", schema = @Schema(type = "integer", defaultValue = "12", maximum = "100")),
            @Parameter(name = "sort", description = "property,direction; properties: id, title, author, genre, price. Direction: asc or desc.", example = "price,desc")
    })
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Matching book page", useReturnTypeSchema = true)
    @ErrorApiResponse(responseCode = "400", description = "Invalid decimal, price range, pagination, or sort field")
    public ResponseEntity<Page<BookResponseDTO>> queryBooks(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String genre,
            @Parameter(description = "Inclusive minimum price; may be used without maxPrice.", example = "10.00")
            @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Inclusive maximum price; may be used without minPrice.", example = "25.00")
            @RequestParam(required = false) BigDecimal maxPrice,
            @ParameterObject @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(service.queryBooks(title, author, genre, minPrice, maxPrice, pageable)
                .map(mapper::toResponseDTO));
    }

    @GetMapping("/sorted")
    @Operation(summary = "List books sorted by a field",
            description = "Returns an unpaginated list sorted ascending by title, author, id, price, or genre. The default field is title.")
    @ErrorApiResponse(responseCode = "400", description = "Unsupported sort field")
    public ResponseEntity<List<BookResponseDTO>> getSortedBooks(
            @Parameter(description = "Allowed values: title, author, id, price, genre.", schema = @Schema(allowableValues = {"title", "author", "id", "price", "genre"}))
            @RequestParam(required = false, defaultValue = "title") String category) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.getBooksSortedBy(category)));
    }

    @GetMapping("/genre")
    @Operation(summary = "Get genre distribution",
            description = "Returns a map from each genre name to the number of book records in that genre.")
    public ResponseEntity<Map<String, Long>> getGenre() {
        return ResponseEntity.ok(service.getGenreDistribution());
    }

    @GetMapping("/price")
    @Operation(summary = "Filter books by price range",
            description = "Returns books with prices between minPrice and maxPrice, inclusive. minPrice must not exceed maxPrice.")
    @ErrorApiResponse(responseCode = "400", description = "Invalid decimal value or minPrice exceeds maxPrice")
    public ResponseEntity<List<BookResponseDTO>> getPriceRangedBooks(
            @Parameter(description = "Inclusive minimum price.", example = "10.00")
            @RequestParam BigDecimal minPrice,
            @Parameter(description = "Inclusive maximum price.", example = "25.00")
            @RequestParam BigDecimal maxPrice) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.getBooksInPriceRange(minPrice, maxPrice)));
    }

    @GetMapping("/stats/average-price")
    @Operation(summary = "Get average book price",
            description = "Returns the average price in the ApiResponse data field, or 0 when the collection is empty.")
    public ResponseEntity<ApiResponse<BigDecimal>> getAveragePrice() {
        return ResponseEntity.ok(new ApiResponse<>(true,
                "Average Price of Collection: ", service.getAveragePrice()));
    }

    @GetMapping("/stats/count")
    @Operation(summary = "Get book count",
            description = "Returns the collection count in the ApiResponse data field.")
    public ResponseEntity<ApiResponse<Long>> getBooksCount() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book Collection Count", service.getBookCount()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a book",
            description = "Updates only supplied fields. Omitted or blank text fields remain unchanged; a supplied price must be greater than zero. Text values are trimmed before storage.")
    @ErrorApiResponse(responseCode = "400", description = "Invalid book ID or supplied field")
    @ErrorApiResponse(responseCode = "404", description = "Book not found")
    public ResponseEntity<ApiResponse<BookResponseDTO>> patchBook(
            @PathVariable Long id,
            @RequestBody BookPatchRequestDTO updates) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book updated successfully",
                mapper.toResponseDTO(service.patchBook(id, updates))));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a book",
            description = "Replaces every mutable book field. Title, author, genre, and a positive price are required; text values are trimmed before storage.")
    @ErrorApiResponse(responseCode = "400", description = "Invalid book ID or request validation failed")
    @ErrorApiResponse(responseCode = "404", description = "Book not found")
    public ResponseEntity<ApiResponse<BookResponseDTO>> replaceBook(
            @PathVariable Long id,
            @Valid @RequestBody BookRequestDTO updates) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book updated successfully",
                mapper.toResponseDTO(service.replaceBook(id, updates))));
    }
}
