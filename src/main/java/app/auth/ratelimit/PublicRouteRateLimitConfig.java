package app.auth.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers throttling before request bodies reach the public account controllers. */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class PublicRouteRateLimitConfig implements WebMvcConfigurer {

    private final PublicRouteRateLimitInterceptor interceptor;

    /**
     * Creates MVC rate-limit registration with deployment-controlled quotas.
     *
     * @param properties route quotas
     * @param objectMapper serializer for standard API errors
     */
    public PublicRouteRateLimitConfig(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.interceptor = new PublicRouteRateLimitInterceptor(properties, objectMapper);
    }

    /** Registers the guard only for the two public POST route paths. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/app/auth/login", "/app/users/signup");
    }
}
