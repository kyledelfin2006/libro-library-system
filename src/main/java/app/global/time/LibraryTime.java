package app.global.time;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Single definition of the library's wall-clock zone.
 * <p>
 * The database stores offset-free {@code LocalDateTime} values, so every writer must use the same zone or
 * records created by the JVM default zone and loan events would disagree by hours. JPA entities cannot receive
 * an injected {@link java.time.Clock}, so their {@code @PrePersist} callbacks use {@link #now()}; services use
 * the {@code Clock} bean built from {@link #ZONE}.
 * </p>
 */
public final class LibraryTime {

    /** Library local time zone used for every stored timestamp. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Manila");

    private LibraryTime() {
    }

    /**
     * Reads the current library-local time.
     *
     * @return the current Asia/Manila date and time without an offset
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
