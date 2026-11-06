package app.auth.config;

import app.auth.service.LibroUserDetailsService;
import app.auth.web.JsonSecurityErrorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsProperties.class)
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
     * Allows browser requests only from configured UI origins while keeping Bearer-only auth cookie-free.
     * Wildcard origins are rejected by {@link CorsProperties}; no origin is allowed when the setting is blank.
     *
     * @param properties exact origins configured for the browser UI
     * @return the CORS policy applied before the authentication filter rejects preflight requests
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();

        // Exact origins keep untrusted websites from making browser requests with the user's credentials.
        configuration.setAllowedOrigins(properties.origins());
        // Permit only API methods the UI uses, including unauthenticated OPTIONS preflights.
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Bearer tokens are sent explicitly; cookies and other ambient headers are not part of this API.
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        // Let the UI inspect the retry delay returned by the public-route limiter.
        configuration.setExposedHeaders(List.of("Retry-After"));
        // The stateless API does not use cookies or credentialed cross-origin requests.
        configuration.setAllowCredentials(false);
        // Cache successful browser preflights for one hour to avoid repeating OPTIONS calls.
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Applies stateless Bearer authentication and the current route-role rules.
     *
     * @param http Spring Security HTTP configuration
     * @param jwtAuthenticationConverter maps the validated Libro role claim
     * @param apiDocsEnabled whether the public OpenAPI document and Swagger UI routes are permitted
     * @return the configured filter chain
     * @throws Exception if the security chain cannot be built
     */
    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            @Value("${libro.api-docs.enabled:false}") boolean apiDocsEnabled) throws Exception {
        http
                // CORS must handle browser preflights before stateless Bearer authentication.
                .cors(Customizer.withDefaults())
                // Credentials are accepted only from the explicit Authorization header.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // A browser cannot attach a Bearer header to a page load, so the docs are either fully public
                    // (flag on) or not served at all (flag off, springdoc disabled and no permit rule).
                    if (apiDocsEnabled) {
                        auth.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    auth
                        // Liveness/readiness probes are public; all other Actuator endpoints live on the private management port.
                        .requestMatchers(HttpMethod.GET, "/livez", "/readyz").permitAll()
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

                        .anyRequest().authenticated();
                })
                // Filter-chain 401/403 happen before MVC, so they need their own standard JSON body.
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new JsonSecurityErrorHandler())
                        .accessDeniedHandler(new JsonSecurityErrorHandler()))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(new JsonSecurityErrorHandler())
                        .accessDeniedHandler(new JsonSecurityErrorHandler())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
