package app.book.controller;

import app.book.dto.BookRequestDTO;
import app.book.dto.BookResponseDTO;
import app.book.dto.LibraryStatisticsDTO;
import app.book.mapper.BookMapper;
import app.book.service.BookService;
import app.global.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
            description = "Runs a database count query to confirm the database is reachable.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "API and database status",
            content = @Content(examples = @ExampleObject(value = """
                    {"success":true,"message":"Health check","data":{"api":true,"database":true},"timestamp":1720000000000}
                    """)))
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> healthCheck() {
        Map<String, Boolean> status = Map.of("api", true, "database", service.getBookCount() >= 0);
        return ResponseEntity.ok(new ApiResponse<>(true, "Health check", status));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get library statistics",
            description = "Returns the book count, total collection value, and most expensive book.")
    public ResponseEntity<LibraryStatisticsDTO> getStats() {
        return ResponseEntity.ok(service.getLibraryStatistics());
    }

    @GetMapping("/search")
    @Operation(summary = "Search books",
            description = "Use type=title, author, genre, or price. Text matches are case-insensitive; price matches exactly. Results are unpaginated.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported search type or invalid price")
    public ResponseEntity<List<BookResponseDTO>> searchBooks(@RequestParam String type, @RequestParam String value) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.searchBooks(type, value)));
    }

    @GetMapping("/budget")
    @Operation(summary = "Find books within a budget",
            description = "Returns unpaginated results priced at or below the positive maxPrice.")
    public ResponseEntity<List<BookResponseDTO>> budgetBooks(@RequestParam BigDecimal maxPrice) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.getBooksWithinBudget(maxPrice)));
    }

    @PostMapping("/add")
    @Operation(summary = "Add a book",
            description = "Creates a book with required text fields and a positive price. Text is trimmed; duplicate titles are allowed.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Book created")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<ApiResponse<BookResponseDTO>> addBook(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Book to create", required = true,
                    content = @Content(schema = @Schema(implementation = BookRequestDTO.class),
                            examples = @ExampleObject(value = """
                                    {"title":"1984","author":"George Orwell","genre":"Dystopian","price":19.99}
                                    """)))
            @Valid @RequestBody BookRequestDTO input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Book Added Successfully",
                        mapper.toResponseDTO(service.addBook(input))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a book by ID", description = "Returns a book without persistence-only fields such as createdAt.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Book not found")
    public ResponseEntity<BookResponseDTO> getBookById(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponseDTO(service.findBookById(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a book", description = "Deletes one book by its generated identifier.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Book not found")
    public ResponseEntity<ApiResponse<Void>> deleteBook(@PathVariable Long id) {
        service.deleteBookById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Book deleted successfully"));
    }

    @GetMapping("/all")
    @Operation(summary = "List books with pagination",
            description = "Page is zero-based; size defaults to 12 (maximum 100). Sort by id, title, author, genre, or price, with an optional direction such as price,desc.")
    public ResponseEntity<Page<BookResponseDTO>> getAllBooks(
            @ParameterObject @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(service.getBooks(pageable).map(mapper::toResponseDTO));
    }

    @GetMapping("/sorted")
    @Operation(summary = "List books sorted by a field", description = "Returns an unpaginated ascending list. Allowed fields: title, author, genre, price, id.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported sort field")
    public ResponseEntity<List<BookResponseDTO>> getSortedBooks(
            @RequestParam(required = false, defaultValue = "title") String category) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.getBooksSortedBy(category)));
    }

    @GetMapping("/genre")
    @Operation(summary = "Get genre distribution", description = "Returns each genre and its book count.")
    public ResponseEntity<Map<String, Long>> getGenre() {
        return ResponseEntity.ok(service.getGenreDistribution());
    }

    @GetMapping("/price")
    @Operation(summary = "Filter books by price range",
            description = "Returns books in the inclusive range. Both prices must be positive; minPrice cannot exceed maxPrice. Example: minPrice=10&maxPrice=25.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid price bounds")
    public ResponseEntity<List<BookResponseDTO>> getPriceRangedBooks(
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.getBooksInPriceRange(minPrice, maxPrice)));
    }

    @GetMapping("/stats/average-price")
    @Operation(summary = "Get average book price", description = "Returns zero when the collection is empty.")
    public ResponseEntity<ApiResponse<BigDecimal>> getAveragePrice() {
        return ResponseEntity.ok(new ApiResponse<>(true,
                "Average Price of Collection: ", service.getAveragePrice()));
    }

    @GetMapping("/stats/count")
    @Operation(summary = "Get book count", description = "Returns the number of books in the collection.")
    public ResponseEntity<ApiResponse<Long>> getBooksCount() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book Collection Count", service.getBookCount()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a book",
            description = "Updates supplied fields only. Omitted or blank text fields stay unchanged; a supplied price must be positive.")
    public ResponseEntity<ApiResponse<BookResponseDTO>> patchBook(
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Any subset of title, author, genre, and price", required = true,
                    content = @Content(schema = @Schema(implementation = BookRequestDTO.class),
                            examples = @ExampleObject(value = "{\"price\":15.99}")))
            @RequestBody BookRequestDTO updates) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book updated successfully",
                mapper.toResponseDTO(service.patchBook(id, updates))));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a book",
            description = "Replaces all mutable fields. Every field is required; text is trimmed before persistence.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<ApiResponse<BookResponseDTO>> replaceBook(
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Complete replacement payload", required = true,
                    content = @Content(schema = @Schema(implementation = BookRequestDTO.class),
                            examples = @ExampleObject(value = """
                                    {"title":"Animal Farm","author":"George Orwell","genre":"Political Satire","price":12.99}
                                    """)))
            @Valid @RequestBody BookRequestDTO updates) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book updated successfully",
                mapper.toResponseDTO(service.replaceBook(id, updates))));
    }
}
