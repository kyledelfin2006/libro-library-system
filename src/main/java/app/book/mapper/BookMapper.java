package app.book.mapper;

import app.book.dto.BookRequestDTO;
import app.book.dto.BookResponseDTO;
import app.book.entity.Book;
import org.springframework.stereotype.Component;


@Component
public class BookMapper {


    /**
     * Maps a copy with availability computed from its persisted loan state.
     *
     * <p>Availability depends on active loans, which the entity does not hold, so the caller must supply it.</p>
     *
     * @param book the entity to convert (maybe null)
     * @param available whether the copy can currently be borrowed
     * @return the response DTO, or null if input is null
     */
    public BookResponseDTO toResponseDTO(Book book, boolean available) {
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
                book.getCopyStatus(),
                available
        );
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
