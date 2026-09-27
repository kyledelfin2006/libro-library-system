package app.book.controller;

import app.book.dto.BookRequestDTO;
import app.book.dto.BookResponseDTO;
import app.book.dto.LibraryStatisticsDTO;
import app.book.mapper.BookMapper;
import app.book.service.BookService;
import app.global.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "Check API and database health")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> healthCheck() {
        Map<String, Boolean> status = Map.of("api", true, "database", service.getBookCount() >= 0);
        return ResponseEntity.ok(new ApiResponse<>(true, "Health check", status));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get library statistics")
    public ResponseEntity<LibraryStatisticsDTO> getStats() {
        return ResponseEntity.ok(service.getLibraryStatistics());
    }

    @GetMapping("/search")
    @Operation(summary = "Search books")
    public ResponseEntity<List<BookResponseDTO>> searchBooks(@RequestParam String type, @RequestParam String value) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.searchBooks(type, value)));
    }

    @GetMapping("/budget")
    @Operation(summary = "Find books within a budget")
    public ResponseEntity<List<BookResponseDTO>> budgetBooks(@RequestParam BigDecimal maxPrice) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.getBooksWithinBudget(maxPrice)));
    }

    @PostMapping("/add")
    @Operation(summary = "Add a book")
    public ResponseEntity<ApiResponse<BookResponseDTO>> addBook(
            @Valid @RequestBody BookRequestDTO input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Book Added Successfully",
                        mapper.toResponseDTO(service.addBook(input))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a book by ID")
    public ResponseEntity<BookResponseDTO> getBookById(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponseDTO(service.findBookById(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a book")
    public ResponseEntity<ApiResponse<Void>> deleteBook(@PathVariable Long id) {
        service.deleteBookById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Book deleted successfully"));
    }

    @GetMapping("/all")
    @Operation(summary = "List books with pagination")
    public ResponseEntity<Page<BookResponseDTO>> getAllBooks(
            @ParameterObject @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(service.getBooks(pageable).map(mapper::toResponseDTO));
    }

    @GetMapping("/sorted")
    @Operation(summary = "List books sorted by a field")
    public ResponseEntity<List<BookResponseDTO>> getSortedBooks(
            @RequestParam(required = false, defaultValue = "title") String category) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.getBooksSortedBy(category)));
    }

    @GetMapping("/genre")
    @Operation(summary = "Get genre distribution")
    public ResponseEntity<Map<String, Long>> getGenre() {
        return ResponseEntity.ok(service.getGenreDistribution());
    }

    @GetMapping("/price")
    @Operation(summary = "Filter books by price range")
    public ResponseEntity<List<BookResponseDTO>> getPriceRangedBooks(
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice) {
        return ResponseEntity.ok(mapper.toResponseDTOList(service.getBooksInPriceRange(minPrice, maxPrice)));
    }

    @GetMapping("/stats/average-price")
    @Operation(summary = "Get average book price")
    public ResponseEntity<ApiResponse<BigDecimal>> getAveragePrice() {
        return ResponseEntity.ok(new ApiResponse<>(true,
                "Average Price of Collection: ", service.getAveragePrice()));
    }

    @GetMapping("/stats/count")
    @Operation(summary = "Get book count")
    public ResponseEntity<ApiResponse<Long>> getBooksCount() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book Collection Count", service.getBookCount()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a book")
    public ResponseEntity<ApiResponse<BookResponseDTO>> patchBook(
            @PathVariable Long id,
            @RequestBody BookRequestDTO updates) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book updated successfully",
                mapper.toResponseDTO(service.patchBook(id, updates))));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a book")
    public ResponseEntity<ApiResponse<BookResponseDTO>> replaceBook(
            @PathVariable Long id,
            @Valid @RequestBody BookRequestDTO updates) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book updated successfully",
                mapper.toResponseDTO(service.replaceBook(id, updates))));
    }
}
