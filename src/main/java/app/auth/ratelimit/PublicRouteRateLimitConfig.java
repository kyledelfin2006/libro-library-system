package app.auth.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
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
     * @param meterRegistry application metrics registry; a throwaway registry is used in slices that have none
     */
    public PublicRouteRateLimitConfig(RateLimitProperties properties, ObjectProvider<MeterRegistry> meterRegistry) {
        this.interceptor = new PublicRouteRateLimitInterceptor(properties,
                meterRegistry.getIfAvailable(SimpleMeterRegistry::new));
    }

    /** Registers the guard only for the two public POST route paths. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/app/auth/login", "/app/users/signup");
    }
}
