package app.auth.ratelimit;

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
     */
    public PublicRouteRateLimitConfig(RateLimitProperties properties) {
        this.interceptor = new PublicRouteRateLimitInterceptor(properties);
    }

    /** Registers the guard only for the two public POST route paths. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/app/auth/login", "/app/users/signup");
    }
}
