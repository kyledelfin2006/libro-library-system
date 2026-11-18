package app.book.controller;

import app.book.dto.BookRequestDTO;
import app.book.dto.BookPatchRequestDTO;
import app.book.dto.BookResponseDTO;
import app.book.dto.LibraryStatisticsDTO;
import app.book.entity.BookCopyStatus;
import app.book.service.BookService;
import app.global.openapi.ErrorApiResponse;
import app.global.response.ApiResponse;
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
import java.util.Map;

@RestController
@RequestMapping("/app/books")
@Tag(name = "Books", description = "Book catalog, search, filtering, and statistics")
public class BookController {

    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    /** Returns catalog totals based on the recorded replacement amounts for physical copies. */
    @GetMapping("/stats")
    @Operation(summary = "Get library statistics",
            description = "Returns the collection count and total recorded replacement cost, plus the copy with the highest replacement cost. The highest-cost copy is null when the collection is empty.")
    public ResponseEntity<ApiResponse<LibraryStatisticsDTO>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(service.getLibraryStatistics()));
    }

    /** Creates one physical copy after request DTO validation; the service returns the public response shape. */
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
                        service.addBook(input)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a book by ID")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Matching book copy", useReturnTypeSchema = true)
    @ErrorApiResponse(responseCode = "400", description = "Book ID must be a number")
    @ErrorApiResponse(responseCode = "404", description = "Book not found")
    public ResponseEntity<ApiResponse<BookResponseDTO>> getBookById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.findBookById(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a book", description = "Only library assistants and the librarian may delete books. A physical copy referenced by any loan history cannot be deleted.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Book deleted", useReturnTypeSchema = true)
    @ErrorApiResponse(responseCode = "400", description = "Book ID must be a number")
    @ErrorApiResponse(responseCode = "404", description = "Book not found")
    @ErrorApiResponse(responseCode = "403", description = "Only library assistants and the librarian may delete books")
    @ErrorApiResponse(responseCode = "409", description = "Book copy has loan history and must be retained")
    public ResponseEntity<ApiResponse<Void>> deleteBook(@PathVariable Long id) {
        service.deleteBookById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Book deleted successfully"));
    }

    /** Validates the replacement-cost bounds and delegates pagination and filtering to the service. */
    @GetMapping("/query")
    @Operation(summary = "Query books with pagination",
            description = "Optional title, author, genre, copyStatus, and advisory available filters combine with AND. Text filters use case-insensitive substrings; replacement-cost bounds are inclusive. With no filters, returns all books. Pages start at zero; default page 0, size 12, id ascending; maximum size 100. Sort by id, title, author, genre, or replacementCost with sort=property,direction. Invalid ranges or sort fields return 400.")
    @Parameters({
            @Parameter(name = "page", description = "Zero-based page index; default 0.", schema = @Schema(type = "integer", defaultValue = "0")),
            @Parameter(name = "size", description = "Page size; default 12, maximum 100.", schema = @Schema(type = "integer", defaultValue = "12", maximum = "100")),
            @Parameter(name = "sort", description = "property,direction; properties: id, title, author, genre, replacementCost. Direction: asc or desc.", example = "replacementCost,desc")
    })
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Matching book page", useReturnTypeSchema = true)
    @ErrorApiResponse(responseCode = "400", description = "Invalid decimal, replacement-cost range, pagination, or sort field")
    public ResponseEntity<ApiResponse<Page<BookResponseDTO>>> queryBooks(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String genre,
            @Parameter(description = "Inclusive minimum replacement cost; may be used without maxReplacementCost.", example = "10.00")
            @RequestParam(required = false) BigDecimal minReplacementCost,
            @Parameter(description = "Inclusive maximum replacement cost; may be used without minReplacementCost.", example = "25.00")
            @RequestParam(required = false) BigDecimal maxReplacementCost,
            @Parameter(description = "Physical copy status: IN_COLLECTION or LOST.")
            @RequestParam(required = false) BookCopyStatus copyStatus,
            @Parameter(description = "Current advisory checkout availability.")
            @RequestParam(required = false) Boolean available,
            @ParameterObject @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        Page<BookResponseDTO> books = copyStatus == null && available == null
                ? service.queryBooks(title, author, genre, minReplacementCost, maxReplacementCost, pageable)
                : service.queryBooks(title, author, genre, minReplacementCost, maxReplacementCost,
                        copyStatus, available, pageable);
        return ResponseEntity.ok(ApiResponse.ok(books));
    }

    @GetMapping("/genre")
    @Operation(summary = "Get genre distribution",
            description = "Returns a map from each genre name to the number of book records in that genre.")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getGenre() {
        return ResponseEntity.ok(ApiResponse.ok(service.getGenreDistribution()));
    }

    /** Applies only supplied catalog fields; null and blank description values preserve the current description. */
    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a book",
            description = "Library assistants and the librarian may update books. Updates only supplied fields. Omitted, null, or blank text fields remain unchanged; a supplied replacementCost must be greater than zero. Description is limited to 2,000 characters. Text values are trimmed before storage.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated book copy", useReturnTypeSchema = true)
    @ErrorApiResponse(responseCode = "400", description = "Invalid book ID or supplied field")
    @ErrorApiResponse(responseCode = "404", description = "Book not found")
    @ErrorApiResponse(responseCode = "403", description = "Only library assistants and the librarian may update books")
    public ResponseEntity<ApiResponse<BookResponseDTO>> patchBook(
            @PathVariable Long id,
            @RequestBody BookPatchRequestDTO updates) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book updated successfully",
                service.patchBook(id, updates)));
    }

    /** Replaces all catalog fields while retaining the loan-managed physical-copy status. */
    @PutMapping("/{id}")
    @Operation(summary = "Replace a book",
            description = "Library assistants and the librarian may update books. Replaces every mutable book field. Title, author, genre, and a positive replacementCost are required; description is optional and limited to 2,000 characters. Text values are trimmed before storage.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Replaced book copy", useReturnTypeSchema = true)
    @ErrorApiResponse(responseCode = "400", description = "Invalid book ID or request validation failed")
    @ErrorApiResponse(responseCode = "404", description = "Book not found")
    @ErrorApiResponse(responseCode = "403", description = "Only library assistants and the librarian may update books")
    public ResponseEntity<ApiResponse<BookResponseDTO>> replaceBook(
            @PathVariable Long id,
            @Valid @RequestBody BookRequestDTO updates) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book updated successfully",
                service.replaceBook(id, updates)));
    }
}
