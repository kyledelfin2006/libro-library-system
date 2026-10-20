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
     * Converts a Book entity to a response DTO, including the physical-copy status.
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
                book.getDescription(),
                book.getReplacementCost(),
                book.getCopyStatus()
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
                trimOptional(dto.getDescription()),
                dto.getReplacementCost()
        );
    }

    /**
     * Replaces catalog fields from a complete request without changing copy status or creation time.
     * Copy status is managed by the loan workflow, not by book-edit requests.
     */
    public void updateBookFromDto(BookRequestDTO dto, Book existingBook) {
        if (dto == null) return;

        existingBook.setTitle(trim(dto.getTitle()));
        existingBook.setAuthor(trim(dto.getAuthor()));
        existingBook.setGenre(trim(dto.getGenre()));
        // A full replacement may clear an optional description; blank content is normalized to null.
        existingBook.setDescription(trimOptional(dto.getDescription()));
        existingBook.setReplacementCost(dto.getReplacementCost());
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    /** Trims an optional catalog description and stores blank content as absent. */
    private String trimOptional(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }




}
