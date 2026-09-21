package app.book.mapper;

import app.book.dto.BookRequestDTO;
import app.book.dto.BookResponseDTO;
import app.book.entity.Book;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class BookMapper {


    /**
     * Converts a Book entity to a BookResponseDTO.
     *
     * @param book the entity to convert (maybe null)
     * @return the response DTO, or null if input is null
     */
    public BookResponseDTO toResponseDTO(Book book) {
        if (book == null) {
            return null;
        }

        return new BookResponseDTO(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getGenre(),
                book.getPrice()
        );
    }

    /**
     * Converts a list of Book entities to a list of BookResponseDTO's.
     *
     * @param books the list to convert (could contain null)
     * @return the list of response DTO's, or null if input is null
     */
    public List<BookResponseDTO> toResponseDTOList(List<Book> books){
        if (books == null || books.isEmpty()) {
            return Collections.emptyList();
        }

        return books.stream().map(this::toResponseDTO).collect(Collectors.toList());

    }

    /**
     * Converts a BookRequestDTO to a new Book entity.
     * Useful for create operations. (i.e. POST)
     */
    public Book toEntity(BookRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        return new Book(
                trim(dto.getTitle()),
                trim(dto.getAuthor()),
                trim(dto.getGenre()),
                dto.getPrice()
        );
    }

    /**
     * Updates an existing BookEntity from a BookRequestDTO.
     * Useful for changing entire entities. (i.e. PUT)
     */
    public void updateBookFromDto(BookRequestDTO dto, Book existingBook) {
        if (dto == null) return;

        existingBook.setTitle(trim(dto.getTitle()));
        existingBook.setAuthor(trim(dto.getAuthor()));
        existingBook.setGenre(trim(dto.getGenre()));
        existingBook.setPrice(dto.getPrice());
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }




}
