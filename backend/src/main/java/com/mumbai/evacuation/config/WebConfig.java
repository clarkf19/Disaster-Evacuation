package com.mumbai.evacuation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS and operator-auth wiring.
 *
 * In production the Vercel frontend proxies /api to the backend (same origin
 * from the browser's point of view), and in development Vite does the same, so
 * CORS is only needed for clients calling the backend directly. Allowed origins
 * are configurable via CORS_ALLOWED_ORIGINS (comma-separated).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminTokenInterceptor adminTokenInterceptor;
    private final String[] allowedOrigins;

    public WebConfig(AdminTokenInterceptor adminTokenInterceptor,
                     @Value("${app.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
        this.adminTokenInterceptor = adminTokenInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", AdminTokenInterceptor.HEADER);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminTokenInterceptor)
                .addPathPatterns("/api/disasters", "/api/disasters/**", "/api/shelters/**");
    }
}
