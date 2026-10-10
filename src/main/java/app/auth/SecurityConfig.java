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
                        .requestMatchers(HttpMethod.POST, "/app/users/faculty").hasRole("ADMINISTRATOR")
                        .requestMatchers(HttpMethod.DELETE, "/app/users/**").hasRole("ADMINISTRATOR")
                        .requestMatchers(HttpMethod.GET, "/app/books/**")
                        .hasAnyRole("STUDENT", "FACULTY", "ADMINISTRATOR")
                        .requestMatchers(HttpMethod.POST, "/app/books/**")
                        .hasAnyRole("FACULTY", "ADMINISTRATOR")
                        .requestMatchers(HttpMethod.PUT, "/app/books/**")
                        .hasAnyRole("FACULTY", "ADMINISTRATOR")
                        .requestMatchers(HttpMethod.PATCH, "/app/books/**")
                        .hasAnyRole("FACULTY", "ADMINISTRATOR")
                        .requestMatchers(HttpMethod.DELETE, "/app/books/**")
                        .hasAnyRole("FACULTY", "ADMINISTRATOR")
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> {});
        return http.build();
    }
}
