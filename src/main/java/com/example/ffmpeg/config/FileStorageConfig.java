package com.example.ffmpeg.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Data
@Configuration
@ConfigurationProperties(prefix = "drone.inspection.storage")
public class FileStorageConfig {

    private String uploadDir = "uploads";
    private String outputDir = "outputs";
    private String tempDir = "temp";
    private int autoCleanupDays = 30;

    @PostConstruct
    public void init() {
        createDirectoryIfNotExists(uploadDir);
        createDirectoryIfNotExists(outputDir);
        createDirectoryIfNotExists(tempDir);
    }

    private void createDirectoryIfNotExists(String directory) {
        try {
            Path path = Paths.get(directory);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + directory, e);
        }
    }

    public Path getUploadPath() {
        return Paths.get(uploadDir);
    }

    public Path getOutputPath() {
        return Paths.get(outputDir);
    }

    public Path getTempPath() {
        return Paths.get(tempDir);
    }
}
