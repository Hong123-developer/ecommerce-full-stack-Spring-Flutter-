package com.ecomerceshopeonline.ecomercewepapp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.time.Duration;

/** Serves uploaded images from {@code app.upload.dir} at {@code /uploads/**}. */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(properties.upload().dir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                // Trailing slash matters: without it Spring treats the location as a file.
                .addResourceLocations(location.endsWith("/") ? location : location + "/")
                // Names are random UUIDs and never reused, so they can be cached hard.
                .setCacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }
}
