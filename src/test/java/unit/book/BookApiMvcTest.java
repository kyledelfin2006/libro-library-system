package unit.book;

import app.LibraryApplication;
import app.book.controller.BookAPI;
import app.book.dto.BookResponseDTO;
import app.book.dto.LibraryStatisticsDTO;
import app.book.entity.Book;
import app.book.exceptions.BookNotFoundException;
import app.book.mapper.BookMapper;
import app.book.service.BookService;
import app.global.exceptions.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fast MVC-slice coverage for the live book HTTP contract.
 *
 * <p>This test deliberately mocks the service and mapper. It verifies MVC
 * binding, validation, serialization, status codes, pageable/query parsing,
 * and the real global exception advice without starting JPA, Flyway, or
 * PostgreSQL.</p>
 */
@WebMvcTest(BookAPI.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, BookApiMvcTest.MockBeans.class})
@ContextConfiguration(classes = LibraryApplication.class)
class BookApiMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookService service;

    @Autowired
    private BookMapper mapper;

    @TestConfiguration(proxyBeanMethods = false)
    static class MockBeans {

        @Bean
        BookService bookService() {
            return mock(BookService.class);
        }

        @Bean
        BookMapper bookMapper() {
            return mock(BookMapper.class);
        }
    }

    @BeforeEach
    void resetMocks() {
        reset(service, mapper);
    }

    @Test
    void addBook_returnsCreatedEnvelopeAndBookShape() throws Exception {
        Book book = book(1L);
        BookResponseDTO response = response(1L);
        when(service.addBook(any())).thenReturn(book);
        when(mapper.toResponseDTO(book)).thenReturn(response);

        mockMvc.perform(post("/app/books/add")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"1984","author":"George Orwell","genre":"Dystopian","description":"A dystopian novel about surveillance.","replacementCost":19.99}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Book Added Successfully"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("1984"))
                .andExpect(jsonPath("$.data.author").value("George Orwell"))
                .andExpect(jsonPath("$.data.genre").value("Dystopian"))
                .andExpect(jsonPath("$.data.description").value("A dystopian novel about surveillance."))
                .andExpect(jsonPath("$.data.replacementCost").value(19.99))
                .andExpect(jsonPath("$.data.createdAt").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isNumber());
    }

    @Test
    void addBook_withInvalidPayload_returnsGlobalValidationResponse() throws Exception {
        mockMvc.perform(post("/app/books/add")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"","author":"","genre":"","replacementCost":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details", containsString("Title cannot be empty")))
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(service, never()).addBook(any());
    }

    /** Verifies descriptions above the catalog limit fail before the service is called. */
    @Test
    void addBook_withTooLongDescription_returnsGlobalValidationResponse() throws Exception {
        String tooLongDescription = "x".repeat(2001);
        String request = "{\"title\":\"1984\",\"author\":\"George Orwell\",\"genre\":\"Dystopian\",\"description\":\"%s\",\"replacementCost\":19.99}"
                .formatted(tooLongDescription);

        mockMvc.perform(post("/app/books/add").contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", containsString("Description cannot exceed 2000 characters")));

        verify(service, never()).addBook(any());
    }

    @Test
    void getBook_returnsDtoWithoutEntityOnlyFields() throws Exception {
        Book book = book(1L);
        when(service.findBookById(1L)).thenReturn(book);
        when(mapper.toResponseDTO(book)).thenReturn(response(1L));

        mockMvc.perform(get("/app/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("1984"))
                .andExpect(jsonPath("$.description").value("A dystopian novel about surveillance."))
                .andExpect(jsonPath("$.replacementCost").value(19.99))
                .andExpect(jsonPath("$.createdAt").doesNotExist());
    }

    @Test
    void getBook_whenMissing_returnsGlobalNotFoundResponse() throws Exception {
        when(service.findBookById(99L))
                .thenThrow(new BookNotFoundException("Couldn't find book of ID: 99"));

        mockMvc.perform(get("/app/books/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Book not found"))
                .andExpect(jsonPath("$.details").value("Couldn't find book of ID: 99"))
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    @Test
    void deleteBook_returnsSuccessEnvelope() throws Exception {
        mockMvc.perform(delete("/app/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Book deleted successfully"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(service).deleteBookById(1L);
    }

    @Test
    void listBooks_bindsPaginationAndSortQueryParameters() throws Exception {
        Book book = book(1L);
        when(service.getBooks(any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(book), PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "replacementCost")), 5)
        );
        when(mapper.toResponseDTO(book)).thenReturn(response(1L));

        mockMvc.perform(get("/app/books/all")
                        .queryParam("page", "1")
                        .queryParam("size", "2")
                        .queryParam("sort", "replacementCost,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("1984"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        org.mockito.ArgumentCaptor<Pageable> pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(service).getBooks(pageable.capture());
        Pageable actual = pageable.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(1, actual.getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(2, actual.getPageSize());
        org.junit.jupiter.api.Assertions.assertEquals(Sort.Direction.DESC, actual.getSort().getOrderFor("replacementCost").getDirection());
    }

    @Test
    void queryBooks_bindsFiltersAndReturnsPageMetadata() throws Exception {
        Book book = book(1L);
        when(service.queryBooks(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book), PageRequest.of(1, 2), 5));
        when(mapper.toResponseDTO(book)).thenReturn(response(1L));

        mockMvc.perform(get("/app/books/query")
                        .queryParam("title", "farm")
                        .queryParam("author", "orwell")
                        .queryParam("genre", "satire")
                        .queryParam("minReplacementCost", "10.00")
                        .queryParam("maxReplacementCost", "25.00")
                        .queryParam("page", "1")
                        .queryParam("size", "2")
                        .queryParam("sort", "replacementCost,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        var page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(service).queryBooks(org.mockito.Mockito.eq("farm"), org.mockito.Mockito.eq("orwell"),
                org.mockito.Mockito.eq("satire"), org.mockito.Mockito.eq(new BigDecimal("10.00")),
                org.mockito.Mockito.eq(new BigDecimal("25.00")), page.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1, page.getValue().getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(2, page.getValue().getPageSize());
        org.junit.jupiter.api.Assertions.assertEquals(Sort.Direction.DESC,
                page.getValue().getSort().getOrderFor("replacementCost").getDirection());
    }

    @Test
    void queryBooks_rejectsMalformedReplacementCostBeforeService() throws Exception {
        mockMvc.perform(get("/app/books/query").queryParam("minReplacementCost", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
        verify(service, never()).queryBooks(any(), any(), any(), any(), any(), any());
    }

    @Test
    void queryBooks_usesDefaultsAndReturnsValidationErrors() throws Exception {
        when(service.queryBooks(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/app/books/query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        var page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(service).queryBooks(any(), any(), any(), any(), any(), page.capture());
        org.junit.jupiter.api.Assertions.assertEquals(0, page.getValue().getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(12, page.getValue().getPageSize());
        org.junit.jupiter.api.Assertions.assertEquals(Sort.Direction.ASC,
                page.getValue().getSort().getOrderFor("id").getDirection());

        when(service.queryBooks(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenThrow(new IllegalArgumentException("minReplacementCost must be less than or equal to maxReplacementCost"));
        mockMvc.perform(get("/app/books/query")
                        .queryParam("minReplacementCost", "20")
                        .queryParam("maxReplacementCost", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    void search_bindsAndForwardsQueryParameters() throws Exception {
        Book book = book(1L);
        when(service.searchBooks("author", "orwell")).thenReturn(List.of(book));
        when(mapper.toResponseDTOList(List.of(book))).thenReturn(List.of(response(1L)));

        mockMvc.perform(get("/app/books/search")
                        .queryParam("type", "author")
                        .queryParam("value", "orwell"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].author").value("George Orwell"));

        verify(service).searchBooks("author", "orwell");
    }

    @Test
    void replacementCostFilter_bindsDecimalQueryParameters() throws Exception {
        when(service.getBooksInReplacementCostRange(new BigDecimal("10.00"), new BigDecimal("25.00")))
                .thenReturn(List.of());
        when(mapper.toResponseDTOList(List.of())).thenReturn(List.of());

        mockMvc.perform(get("/app/books/replacement-cost")
                        .queryParam("minReplacementCost", "10.00")
                        .queryParam("maxReplacementCost", "25.00"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(service).getBooksInReplacementCostRange(new BigDecimal("10.00"), new BigDecimal("25.00"));
    }

    @Test
    void sorted_whenServiceRejectsField_returnsGlobalValidationResponse() throws Exception {
        when(service.getBooksSortedBy("unknown"))
                .thenThrow(new IllegalArgumentException("Invalid sort field: unknown"));

        mockMvc.perform(get("/app/books/sorted").queryParam("category", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details").value("Invalid sort field: unknown"))
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    void replacementCostFilter_withNonNumericBoundary_returnsGlobalTypeMismatchResponse() throws Exception {
        mockMvc.perform(get("/app/books/replacement-cost")
                        .queryParam("minReplacementCost", "cheap")
                        .queryParam("maxReplacementCost", "25"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid parameter"))
                .andExpect(jsonPath("$.details", containsString("minReplacementCost")))
                .andExpect(jsonPath("$.statusCode").value(400));

        verify(service, never()).getBooksInReplacementCostRange(any(), any());
    }

    @Test
    void malformedJson_returnsGlobalProcessingError() throws Exception {
        mockMvc.perform(post("/app/books/add")
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"1984\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Processing Error"))
                .andExpect(jsonPath("$.details").value("Invalid JSON format in request body"))
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    void stats_returnsStatisticsResponseShape() throws Exception {
        when(service.getLibraryStatistics()).thenReturn(
                new LibraryStatisticsDTO(6, new BigDecimal("123.45"), response(4L))
        );

        mockMvc.perform(get("/app/books/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBooks").value(6))
                .andExpect(jsonPath("$.totalReplacementCost").value(123.45))
                .andExpect(jsonPath("$.highestReplacementCostBook.id").value(4));
    }

    private Book book(Long id) {
        Book book = new Book("1984", "George Orwell", "Dystopian",
                "A dystopian novel about surveillance.", new BigDecimal("19.99"));
        book.setId(id);
        return book;
    }

    private BookResponseDTO response(Long id) {
        BookResponseDTO response = new BookResponseDTO(
                id, "1984", "George Orwell", "Dystopian", new BigDecimal("19.99"));
        response.setDescription("A dystopian novel about surveillance.");
        return response;
    }
}
