// 修正你的 src/main/java/com/example/ffmpeg/config/WebConfig.java
package com.example.ffmpeg.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.nio.file.Paths;
import java.util.Arrays;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Configuration
public class WebConfig {

    // 保留你原有的CORS配置
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        corsConfig.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",
                "http://localhost:8080",
                "http://127.0.0.1:8080",
                "http://localhost:5173",
                "http://localhost:4200"
        ));

        corsConfig.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"
        ));

        corsConfig.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));

        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }

    // WebFlux 的静态资源配置 - 使用路由函数
    @Bean
    public RouterFunction<ServerResponse> staticResourceRouter() {
        return route(GET("/outputs/**"), request -> {
            String path = request.path().substring("/outputs/".length());
            Resource resource = new FileSystemResource(Paths.get("outputs", path));

            if (resource.exists() && resource.isReadable()) {
                MediaType mediaType = getMediaType(path);
                return ServerResponse.ok()
                        .contentType(mediaType)
                        .header("Cache-Control", "public, max-age=3600") // 缓存1小时
                        .bodyValue(resource);
            }
            return ServerResponse.notFound().build();
        })
                .andRoute(GET("/video/**"), request -> {
                    String path = request.path().substring("/video/".length());
                    Resource resource = new FileSystemResource(Paths.get("video", path));

                    if (resource.exists() && resource.isReadable()) {
                        MediaType mediaType = getMediaType(path);
                        return ServerResponse.ok()
                                .contentType(mediaType)
                                .header("Cache-Control", "public, max-age=3600") // 缓存1小时
                                .bodyValue(resource);
                    }
                    return ServerResponse.notFound().build();
                })
                .andRoute(GET("/uploads/**"), request -> {
                    String path = request.path().substring("/uploads/".length());
                    Resource resource = new FileSystemResource(Paths.get("uploads", path));

                    if (resource.exists() && resource.isReadable()) {
                        MediaType mediaType = getMediaType(path);
                        return ServerResponse.ok()
                                .contentType(mediaType)
                                .header("Cache-Control", "public, max-age=3600") // 缓存1小时
                                .bodyValue(resource);
                    }
                    return ServerResponse.notFound().build();
                });
    }

    // 根据文件扩展名确定MIME类型
    private MediaType getMediaType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "jpg":
            case "jpeg":
                return MediaType.IMAGE_JPEG;
            case "png":
                return MediaType.IMAGE_PNG;
            case "gif":
                return MediaType.IMAGE_GIF;
            case "webp":
                return MediaType.parseMediaType("image/webp");
            case "mp4":
                return MediaType.parseMediaType("video/mp4");
            case "webm":
                return MediaType.parseMediaType("video/webm");
            case "avi":
                return MediaType.parseMediaType("video/x-msvideo");
            case "mov":
                return MediaType.parseMediaType("video/quicktime");
            case "mkv":
                return MediaType.parseMediaType("video/x-matroska");
            default:
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
