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
        Book book = new Book("The Hobbit", "J.R.R. Tolkien", "Fantasy", new BigDecimal("12.99"));
        book.setId(1L);
        book.prePersist();

        BookResponseDTO response = new BookMapper().toResponseDTO(book);

        assertEquals(1L, response.getId());
        assertEquals("The Hobbit", response.getTitle());
        assertEquals("J.R.R. Tolkien", response.getAuthor());
        assertEquals("Fantasy", response.getGenre());
        assertEquals(new BigDecimal("12.99"), response.getPrice());
        assertFalse(Arrays.stream(BookResponseDTO.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("createdAt")));
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
