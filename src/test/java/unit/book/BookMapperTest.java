package unit.book;

import app.book.dto.BookResponseDTO;
import app.book.entity.Book;
import app.book.mapper.BookMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookMapperTest {

    @Test
    void mapsBookFieldsAndOmitsCreatedAtFromResponseDto() {
        Book book = new Book("The Hobbit", "J.R.R. Tolkien", "Fantasy", "A hobbit's journey to reclaim treasure.", new BigDecimal("12.99"));
        book.setId(1L);
        book.prePersist();

        BookResponseDTO response = new BookMapper().toResponseDTO(book);

        assertEquals(1L, response.getId());
        assertEquals("The Hobbit", response.getTitle());
        assertEquals("J.R.R. Tolkien", response.getAuthor());
        assertEquals("Fantasy", response.getGenre());
        assertEquals("A hobbit's journey to reclaim treasure.", response.getDescription());
        assertEquals(new BigDecimal("12.99"), response.getReplacementCost());
        assertFalse(Arrays.stream(BookResponseDTO.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("createdAt")));
    }

    /** Verifies request descriptions are trimmed and blank optional descriptions are stored as null. */
    @Test
    void mapsRequestDescriptionAndReplacementCostToEntity() {
        BookMapper mapper = new BookMapper();

        Book described = mapper.toEntity(new app.book.dto.BookRequestDTO(
                "The Hobbit", "J.R.R. Tolkien", "Fantasy", "  A hobbit's journey.  ", new BigDecimal("12.99")));
        Book blankDescription = mapper.toEntity(new app.book.dto.BookRequestDTO(
                "The Hobbit", "J.R.R. Tolkien", "Fantasy", "  ", new BigDecimal("12.99")));

        assertEquals("A hobbit's journey.", described.getDescription());
        assertEquals(new BigDecimal("12.99"), described.getReplacementCost());
        assertNull(blankDescription.getDescription());
    }

    @Test
    void returnsNullWhenBookIsNull() {
        assertNull(new BookMapper().toResponseDTO(null));
    }

    @Test
    void mapsBookListToResponseDtoList() {
        Book first = new Book("The Hobbit", "J.R.R. Tolkien", "Fantasy", new BigDecimal("12.99"));
        Book second = new Book("1984", "George Orwell", "Dystopian", new BigDecimal("19.99"));

        List<BookResponseDTO> responses = new BookMapper().toResponseDTOList(List.of(first, second));

        assertEquals(2, responses.size());
        assertEquals("The Hobbit", responses.get(0).getTitle());
        assertEquals("1984", responses.get(1).getTitle());
    }

    @Test
    void returnsEmptyListForNullOrEmptyBookList() {
        BookMapper mapper = new BookMapper();

        assertTrue(mapper.toResponseDTOList(null).isEmpty());
        assertTrue(mapper.toResponseDTOList(List.of()).isEmpty());
    }
}
