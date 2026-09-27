package app.book.controller;

import app.book.dto.BookRequestDTO;
import app.book.dto.BookPatchRequestDTO;
import app.book.dto.BookResponseDTO;
import app.book.dto.LibraryStatisticsDTO;
import app.book.mapper.BookMapper;
import app.book.service.BookService;
import app.global.responses.ApiResponse;
import app.global.responses.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Database health query failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> healthCheck() {
        Map<String, Boolean> status = Map.of("api", true, "database", service.getBookCount() >= 0);
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
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported search type or invalid price value",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "maxPrice is not a valid decimal",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<BookResponseDTO>> budgetBooks(
            @Parameter(description = "Inclusive maximum price.", example = "25.00")
            @RequestParam BigDecimal maxPrice) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.getBooksWithinBudget(maxPrice)));
    }

    @PostMapping("/add")
    @Operation(summary = "Add a book",
            description = "Creates a physical book-copy record. Duplicate titles are allowed; each copy receives its own generated ID.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Book created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<BookResponseDTO>> addBook(
            @Valid @RequestBody BookRequestDTO input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Book Added Successfully",
                        mapper.toResponseDTO(service.addBook(input))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a book by ID")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Book ID must be a number",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Book not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BookResponseDTO> getBookById(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponseDTO(service.findBookById(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a book")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Book ID must be a number",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Book not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<Void>> deleteBook(@PathVariable Long id) {
        service.deleteBookById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Book deleted successfully"));
    }

    @GetMapping("/all")
    @Operation(summary = "List books with pagination",
            description = "Page numbering starts at zero. The default is page 0, size 12, sorted by id ascending; the maximum page size is 100. Sort with sort=property,direction using id, title, author, genre, or price. Unsupported sort properties return 400.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid pagination or sort parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<BookResponseDTO>> getAllBooks(
            @ParameterObject @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(service.getBooks(pageable).map(mapper::toResponseDTO));
    }

    @GetMapping("/sorted")
    @Operation(summary = "List books sorted by a field",
            description = "Returns an unpaginated list sorted ascending by title, author, id, price, or genre. The default field is title.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported sort field",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid decimal value or minPrice exceeds maxPrice",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid book ID or supplied field",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Book not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<BookResponseDTO>> patchBook(
            @PathVariable Long id,
            @RequestBody BookPatchRequestDTO updates) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book updated successfully",
                mapper.toResponseDTO(service.patchBook(id, updates))));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a book",
            description = "Replaces every mutable book field. Title, author, genre, and a positive price are required; text values are trimmed before storage.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid book ID or request validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Book not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<BookResponseDTO>> replaceBook(
            @PathVariable Long id,
            @Valid @RequestBody BookRequestDTO updates) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book updated successfully",
                mapper.toResponseDTO(service.replaceBook(id, updates))));
    }
}
