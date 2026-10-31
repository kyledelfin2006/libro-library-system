package app.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Exposes the existing university-ID lookup and BCrypt check for the login endpoint.
     *
     * @param userDetailsService the repository-backed account lookup
     * @param passwordEncoder the existing BCrypt password encoder
     * @return the manager used by {@code AuthService}
     */
    @Bean
    public AuthenticationManager authenticationManager(
            LibroUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * Applies stateless Bearer authentication and the current route-role rules.
     *
     * @param http Spring Security HTTP configuration
     * @param jwtAuthenticationConverter maps the validated Libro role claim
     * @return the configured filter chain
     * @throws Exception if the security chain cannot be built
     */
    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                // Credentials are accepted only from the explicit Authorization header.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/app/users/signup", "/app/auth/login").permitAll()
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
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
