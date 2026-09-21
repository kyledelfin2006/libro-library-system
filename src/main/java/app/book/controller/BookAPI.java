package app.book.controller;

import app.book.dto.BookResponseDTO;
import app.book.dto.LibraryStatisticsDTO;
import app.book.entity.Book;
import app.book.dto.BookRequestDTO;
import app.book.mapper.BookMapper;
import app.global.responses.ApiResponse;
import app.book.service.BookService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;



@RestController
@RequestMapping("/app/books")
@Tag(name = "Books", description = "Book catalog, search, filtering, statistics, and health operations")
public class BookAPI {

    private final BookService service;
    private final BookMapper mapper;

    @Autowired
    public BookAPI(BookService service, BookMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    // API ENDPOINTS

    @GetMapping("/health")
    @Operation(summary = "Check API and database health", description = "Runs a database count query and reports whether the API and database are reachable.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Health status",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {"success":true,"message":"Health check","data":{"api":true,"database":true},"timestamp":1720000000000}
                    """)))
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> healthCheck() {
        boolean dbUp = service.getBookCount() >= 0; // throws exception if connection fails
        Map<String, Boolean> status = Map.of("api", true, "database", dbUp);
        return ResponseEntity.ok(new ApiResponse<>(true, "Health check", status));
    }


    @GetMapping("/stats")
    @Operation(summary = "Get library statistics", description = "Returns the total book count, total collection value, and most expensive book.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Library statistics",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LibraryStatisticsDTO.class), examples = @ExampleObject(value = """
                    {"totalBooks":6,"totalValue":123.45,"mostExpensiveBook":{"id":4,"title":"Dune","author":"Frank Herbert","genre":"Science Fiction","price":49.99}}
                    """)))
    public ResponseEntity<LibraryStatisticsDTO> getStats() {

        // 1. Create libraryStatsDTO pre-built dto from service
        LibraryStatisticsDTO dto = service.getLibraryStatistics();

        // 2. Return object
        return ResponseEntity.ok(dto);
    }

    // Search books
    @GetMapping("/search")
    @Operation(summary = "Search books", description = "Searches title, author, or genre using a case-insensitive contains match. Use type=price for an exact numeric price match. Results are currently returned as an unpaginated list.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Matching books", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            [{"id":1,"title":"1984","author":"George Orwell","genre":"Dystopian","price":19.99}]
            """)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported search type or invalid price value")
    public ResponseEntity<List<BookResponseDTO>> searchBooks(
            @Parameter(description = "Search field.", required = true, example = "author", schema = @Schema(allowableValues = {"title", "author", "genre", "price"}))
            @RequestParam String type,
            @Parameter(description = "Text search term or exact decimal price when type=price.", required = true, example = "orwell")
            @RequestParam String value) {

        List<Book> foundBooks = service.searchBooks(type, value);
        return ResponseEntity.ok(mapper.toResponseDTOList(foundBooks));
    }

    @GetMapping("/budget")
    @Operation(summary = "Find books within a budget", description = "Returns books priced at or below maxPrice. Results are currently returned as an unpaginated list.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Books within budget", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            [{"id":1,"title":"1984","author":"George Orwell","genre":"Dystopian","price":19.99}]
            """)))
    public ResponseEntity<List<BookResponseDTO>> budgetBooks(
            @Parameter(description = "Inclusive maximum price; must be positive.", required = true, example = "20.00")
            @RequestParam BigDecimal maxPrice) {
            List<Book> affordableBooks = service.getBooksWithinBudget(maxPrice);
            return ResponseEntity.ok(mapper.toResponseDTOList(affordableBooks));
    }

    // Add a new book (validated)
    @PostMapping("/add")
    @Operation(summary = "Add a book", description = "Creates a book after validating required text, length limits, and positive price. Surrounding whitespace in text fields is trimmed. Duplicate title/author pairs are allowed.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Book created", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"success":true,"message":"Book Added Successfully","data":{"id":1,"title":"1984","author":"George Orwell","genre":"Dystopian","price":19.99},"timestamp":1720000000000}
            """)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"error":"Validation failed","details":"Price must be greater than 0","timestamp":1720000000000,"statusCode":400}
            """)))
    public ResponseEntity<ApiResponse<BookResponseDTO>> addBook(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Book to create.", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = BookRequestDTO.class), examples = @ExampleObject(value = """
                    {"title":"1984","author":"George Orwell","genre":"Dystopian","price":19.99}
                    """)))
            @Valid @RequestBody BookRequestDTO input) {
        Book newBook = service.addBook(input);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Book Added Successfully", mapper.toResponseDTO(newBook)));
    }

    // Get book by ID
    @GetMapping("/{id}")
    @Operation(summary = "Get a book by ID", description = "Returns one book response without persistence-only fields such as createdAt.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Book found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BookResponseDTO.class), examples = @ExampleObject(value = """
            {"id":1,"title":"1984","author":"George Orwell","genre":"Dystopian","price":19.99}
            """)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Book not found")
    public ResponseEntity<BookResponseDTO> getBookById(
            @Parameter(description = "Generated book identifier.", required = true, example = "1")
            @PathVariable Long id) {
        Book book = service.findBookById(id);
        return ResponseEntity.ok(mapper.toResponseDTO(book));
    }

    // Delete a book
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a book", description = "Deletes one book by its generated identifier.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Book deleted", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"success":true,"message":"Book deleted successfully","data":null,"timestamp":1720000000000}
            """)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Book not found")
    public ResponseEntity<ApiResponse<Void>> deleteBook(
            @Parameter(description = "Generated book identifier.", required = true, example = "1")
            @PathVariable Long id) {
        service.deleteBookById(id); // Throws Exception in case
        return ResponseEntity.ok(new ApiResponse<>(true, "Book deleted successfully"));
    }

    @GetMapping("/all") // User sets page, if omit then default will be used
    @Operation(summary = "List books with pagination", description = "Returns a Spring Page of books. page is zero-based, size defaults to 12 and is capped at 100, and sort accepts a property plus optional direction such as sort=price,desc. Supported sort properties are id, title, author, genre, and price.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paginated books", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"content":[{"id":1,"title":"1984","author":"George Orwell","genre":"Dystopian","price":19.99}],"pageable":{"pageNumber":0,"pageSize":12,"sort":{"sorted":true,"orders":[{"property":"id","direction":"ASC"}]}},"totalElements":1,"totalPages":1,"last":true,"size":12,"number":0,"numberOfElements":1,"first":true,"empty":false}
            """)))
    public ResponseEntity<Page<BookResponseDTO>> getAllBooks(
           @ParameterObject @PageableDefault(size = 12, sort = "id") Pageable pageable) {

        // 1. Get page as book entity
        Page<Book> pageEntity = service.getBooks(pageable);

        // 2. Map from book entity to response dto
        Page<BookResponseDTO> pageResponse = pageEntity.map(mapper::toResponseDTO);

        return ResponseEntity.ok(pageResponse);
    }

    @GetMapping("/sorted")
    @Operation(summary = "List books sorted by one allowed field", description = "Returns an unpaginated ascending list. Allowed category values are title, author, genre, price, and id. Invalid values return 400.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Sorted books", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            [{"id":2,"title":"Animal Farm","author":"George Orwell","genre":"Political Satire","price":12.99}]
            """)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported sort field")
    public ResponseEntity<List<BookResponseDTO>> getSortedBooks(
            @Parameter(description = "Allowed ascending sort field.", example = "title", schema = @Schema(allowableValues = {"title", "author", "genre", "price", "id"}))
            @RequestParam(required = false, defaultValue = "title") String category) {

        // 1. Get book list in book entity type
        List<Book> bookList = service.getBooksSortedBy(category);

        // 2. Map book entity list to DTO list
        List<BookResponseDTO> dtoList = mapper.toResponseDTOList(bookList);

        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/genre")
    @Operation(summary = "Get genre distribution", description = "Returns a map whose keys are genres and values are book counts.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Genre counts", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"Dystopian":3,"Fantasy":2,"Fiction":1}
            """)))
    public ResponseEntity<Map<String, Long>> getGenre() {
        return ResponseEntity.ok(service.getGenreDistribution());
    }

    @GetMapping("/price")
    @Operation(summary = "Filter books by price range", description = "Returns books whose prices fall inclusively between minPrice and maxPrice. Both values are required, positive, and minPrice must be less than or equal to maxPrice.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Books in price range", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            [{"id":1,"title":"1984","author":"George Orwell","genre":"Dystopian","price":19.99}]
            """)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing, non-numeric, non-positive, or reversed price bounds")
    public ResponseEntity<List<BookResponseDTO>> getPriceRangedBooks(
            @Parameter(description = "Inclusive minimum price; must be positive and no greater than maxPrice.", required = true, example = "10.00")
            @RequestParam BigDecimal minPrice,
            @Parameter(description = "Inclusive maximum price; must be positive and no less than minPrice.", required = true, example = "25.00")
            @RequestParam BigDecimal maxPrice
    ){
        List<Book> bookList = service.getBooksInPriceRange(minPrice,maxPrice);
        List<BookResponseDTO> dtoList = mapper.toResponseDTOList(bookList);
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/stats/average-price")
    @Operation(summary = "Get average book price", description = "Returns the average price, or zero when the collection is empty.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Average price", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"success":true,"message":"Average Price of Collection: ","data":20.50,"timestamp":1720000000000}
            """)))
    public ResponseEntity<ApiResponse<BigDecimal>> getAveragePrice() {
        BigDecimal avg = service.getAveragePrice();
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Average Price of Collection: ",avg)
        );
    }

    @GetMapping("/stats/count")
    @Operation(summary = "Get book count", description = "Returns the number of books in the collection.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Book count", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"success":true,"message":"Book Collection Count","data":6,"timestamp":1720000000000}
            """)))
    public  ResponseEntity<ApiResponse<Long>> getBooksCount() {
      return ResponseEntity.ok(
              new ApiResponse<>(true,"Book Collection Count", service.getBookCount())
              );
    }


    // Patch (partial update)
    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a book", description = "Updates only supplied fields. Omitted or blank text fields are left unchanged; a supplied price must be positive. This endpoint intentionally does not apply full DTO validation because PATCH fields may be omitted.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Book updated", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"success":true,"message":"Book updated successfully","data":{"id":1,"title":"1984","author":"George Orwell","genre":"Dystopian","price":15.99},"timestamp":1720000000000}
            """)))
    public ResponseEntity<ApiResponse<BookResponseDTO>> patchBook(
            @Parameter(description = "Generated book identifier.", required = true, example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Any subset of title, author, genre, and price.", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = BookRequestDTO.class), examples = @ExampleObject(value = """
                    {"price":15.99}
                    """)))
            @RequestBody BookRequestDTO updates) { // No @Valid to allow null values

        Book updated = service.patchBook(id, updates);
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Book updated successfully", mapper.toResponseDTO(updated))
        );
    }

    // Put (complete update)
    @PutMapping("/{id}")
    @Operation(summary = "Replace a book", description = "Replaces all mutable book fields. Every field is required and validated; surrounding whitespace in text fields is trimmed before persistence.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Book replaced", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"success":true,"message":"Book updated successfully","data":{"id":1,"title":"Animal Farm","author":"George Orwell","genre":"Political Satire","price":12.99},"timestamp":1720000000000}
            """)))
    public ResponseEntity<ApiResponse<BookResponseDTO>> replaceBook(
            @Parameter(description = "Generated book identifier.", required = true, example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Complete replacement payload.", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = BookRequestDTO.class), examples = @ExampleObject(value = """
                    {"title":"Animal Farm","author":"George Orwell","genre":"Political Satire","price":12.99}
                    """)))
            @Valid @RequestBody BookRequestDTO updates){

        // Update book
         Book updated = service.replaceBook(id,updates);
        return ResponseEntity.ok(
                new ApiResponse<>(true,
                        "Book updated successfully",
                        mapper.toResponseDTO(updated)));
    }
}

