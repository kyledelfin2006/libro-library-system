package app.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    /**
     * Applies authentication and role rules to the HTTP API while retaining CSRF protection.
     *
     * @param http Spring Security HTTP configuration
     * @return the configured filter chain
     * @throws Exception if the security chain cannot be built
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/app/users/signup").permitAll()
                        .requestMatchers(HttpMethod.POST, "/app/users/faculty").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.POST, "/app/users/administrators").hasRole("LIBRARIAN")

                        .requestMatchers(HttpMethod.GET, "/app/users").hasAnyRole("ADMINISTRATOR", "LIBRARIAN")
                        .requestMatchers(HttpMethod.GET, "/app/users/**")
                        .hasAnyRole("STUDENT", "FACULTY", "ADMINISTRATOR", "LIBRARIAN")
                        .requestMatchers(HttpMethod.PATCH, "/app/users/**")
                        .hasAnyRole("STUDENT", "FACULTY", "ADMINISTRATOR", "LIBRARIAN")
                        .requestMatchers(HttpMethod.PUT, "/app/users/**")
                        .hasAnyRole("STUDENT", "FACULTY", "ADMINISTRATOR", "LIBRARIAN")
                        .requestMatchers(HttpMethod.DELETE, "/app/users/**")
                        .hasAnyRole("STUDENT", "FACULTY", "ADMINISTRATOR", "LIBRARIAN")

                        // Borrowers are students/faculty; administrators are library assistants.
                        .requestMatchers(HttpMethod.POST, "/app/loans/**")
                        .hasAnyRole("STUDENT", "FACULTY")
                        .requestMatchers(HttpMethod.PUT, "/app/loans/*/return")
                        .hasAnyRole("STUDENT", "FACULTY")
                        .requestMatchers(HttpMethod.PUT, "/app/loans/*/lost")
                        .hasAnyRole("ADMINISTRATOR", "LIBRARIAN")
                        .requestMatchers(HttpMethod.GET, "/app/loans/me")
                        .hasAnyRole("STUDENT", "FACULTY", "ADMINISTRATOR", "LIBRARIAN")
                        .requestMatchers(HttpMethod.GET, "/app/loans/users/**")
                        .hasAnyRole("ADMINISTRATOR", "LIBRARIAN")

                        .requestMatchers(HttpMethod.GET, "/app/books/**")
                        .hasAnyRole("STUDENT", "FACULTY", "ADMINISTRATOR", "LIBRARIAN")
                        .requestMatchers(HttpMethod.POST, "/app/books/**")
                        .hasAnyRole("ADMINISTRATOR", "LIBRARIAN")
                        .requestMatchers(HttpMethod.PUT, "/app/books/**")
                        .hasAnyRole("ADMINISTRATOR", "LIBRARIAN")
                        .requestMatchers(HttpMethod.PATCH, "/app/books/**")
                        .hasAnyRole("ADMINISTRATOR", "LIBRARIAN")
                        .requestMatchers(HttpMethod.DELETE, "/app/books/**")
                        .hasAnyRole("ADMINISTRATOR", "LIBRARIAN")

                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> {});
        return http.build();
    }
}
