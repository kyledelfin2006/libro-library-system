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
