package app.loan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Supplies the library's agreed local time source for loan deadlines and events.
 */
@Configuration
public class LoanTimeConfig {

    /**
     * Creates the clock used to read Asia/Manila local time.
     *
     * @return a system clock configured for the library's timezone
     */
    @Bean
    public Clock libraryClock() {
        // LocalDateTime values are interpreted as Asia/Manila throughout this feature.
        return Clock.system(ZoneId.of("Asia/Manila"));
    }
}
