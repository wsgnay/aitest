// src/main/java/com/example/ffmpeg/controller/FileUploadController.java
package com.example.ffmpeg.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/drone/upload")
@RequiredArgsConstructor
public class FileUploadController {

    @Value("${drone.inspection.storage.upload-dir:uploads}")
    private String uploadDir;

    @Value("${drone.inspection.storage.temp-dir:temp}")
    private String tempDir;

    // 支持的图像格式
    private static final String[] SUPPORTED_IMAGE_FORMATS = {
            "jpg", "jpeg", "png", "bmp", "tiff", "webp"
    };

    // 支持的视频格式
    private static final String[] SUPPORTED_VIDEO_FORMATS = {
            "mp4", "avi", "mov", "mkv", "flv", "wmv", "3gp"
    };

    // 最大文件大小限制
    private static final long MAX_IMAGE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final long MAX_VIDEO_SIZE = 500 * 1024 * 1024L; // 500MB

    /**
     * 上传图像文件
     */
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> uploadImage(@RequestPart("file") Mono<FilePart> filePartMono) {
        return filePartMono
                .cast(FilePart.class)
                .flatMap(filePart -> {
                    log.info("开始上传图像文件: {}", filePart.filename());
                    return uploadFile(filePart, "image", SUPPORTED_IMAGE_FORMATS, MAX_IMAGE_SIZE);
                })
                .map(ResponseEntity::ok)
                .onErrorResume(ex -> {
                    log.error("图像文件上传失败: {}", ex.getMessage());
                    Map<String, Object> errorResponse = createErrorResponse(ex.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(errorResponse));
                });
    }

    /**
     * 上传视频文件
     */
    @PostMapping(value = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> uploadVideo(@RequestPart("file") Mono<FilePart> filePartMono) {
        return filePartMono
                .cast(FilePart.class)
                .flatMap(filePart -> {
                    log.info("开始上传视频文件: {}", filePart.filename());
                    return uploadFile(filePart, "video", SUPPORTED_VIDEO_FORMATS, MAX_VIDEO_SIZE);
                })
                .map(ResponseEntity::ok)
                .onErrorResume(ex -> {
                    log.error("视频文件上传失败: {}", ex.getMessage());
                    Map<String, Object> errorResponse = createErrorResponse(ex.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(errorResponse));
                });
    }

    /**
     * 批量上传图像文件
     */
    @PostMapping(value = "/images/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> uploadImageBatch(@RequestPart("files") Mono<FilePart> filePartsMono) {
        // 这里可以实现批量上传逻辑
        // 目前先返回单文件上传的结果
        return uploadImage(filePartsMono);
    }

    /**
     * 获取上传进度（WebSocket支持）
     */
    @GetMapping("/progress/{uploadId}")
    public Mono<ResponseEntity<Map<String, Object>>> getUploadProgress(@PathVariable String uploadId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> progress = new HashMap<>();

            // 这里可以实现实际的进度跟踪逻辑
            // 目前返回模拟数据
            progress.put("uploadId", uploadId);
            progress.put("progress", 100); // 百分比
            progress.put("status", "COMPLETED"); // UPLOADING, COMPLETED, FAILED
            progress.put("message", "上传完成");

            return ResponseEntity.ok(progress);
        });
    }

    /**
     * 删除上传的文件
     */
    @DeleteMapping("/{fileId}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteFile(@PathVariable String fileId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();

            try {
                // 这里可以实现实际的文件删除逻辑
                log.info("删除文件: {}", fileId);

                result.put("success", true);
                result.put("message", "文件删除成功");
                result.put("fileId", fileId);

                return ResponseEntity.ok(result);

            } catch (Exception e) {
                log.error("删除文件失败: {}", e.getMessage());
                result.put("success", false);
                result.put("error", e.getMessage());
                return ResponseEntity.badRequest().body(result);
            }
        });
    }

    /**
     * 核心文件上传方法
     */
    private Mono<Map<String, Object>> uploadFile(FilePart filePart, String fileType,
                                                 String[] supportedFormats, long maxSize) {
        return Mono.fromCallable(() -> {
                    // 验证文件
                    validateFile(filePart, supportedFormats, maxSize);

                    // 创建上传目录
                    createUploadDirectories();

                    // 生成唯一文件名
                    String originalFilename = filePart.filename();
                    String fileExtension = getFileExtension(originalFilename);
                    String uniqueFilename = generateUniqueFilename(fileExtension);

                    // 确定保存路径
                    Path savePath = Paths.get(uploadDir, fileType, uniqueFilename);

                    return new UploadContext(filePart, savePath, originalFilename, uniqueFilename, fileType);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(this::saveFile)
                .map(this::createSuccessResponse);
    }

    /**
     * 验证文件
     */
    private void validateFile(FilePart filePart, String[] supportedFormats, long maxSize) {
        String filename = filePart.filename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        // 检查文件扩展名
        String extension = getFileExtension(filename).toLowerCase();
        boolean isSupported = false;
        for (String format : supportedFormats) {
            if (format.equalsIgnoreCase(extension)) {
                isSupported = true;
                break;
            }
        }

        if (!isSupported) {
            throw new IllegalArgumentException("不支持的文件格式: " + extension);
        }

        // 注意：在WebFlux中，我们无法在这里直接检查文件大小
        // 实际的大小检查需要在保存过程中进行
        log.debug("文件验证通过: {}", filename);
    }

    /**
     * 创建上传目录
     */
    private void createUploadDirectories() {
        try {
            Path uploadPath = Paths.get(uploadDir);
            Path imagePath = uploadPath.resolve("image");
            Path videoPath = uploadPath.resolve("video");
            Path tempPath = Paths.get(tempDir);

            Files.createDirectories(imagePath);
            Files.createDirectories(videoPath);
            Files.createDirectories(tempPath);

            log.debug("上传目录创建完成");
        } catch (IOException e) {
            throw new RuntimeException("创建上传目录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    /**
     * 生成唯一文件名
     */
    private String generateUniqueFilename(String extension) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s_%s.%s", timestamp, uuid, extension);
    }

    /**
     * 保存文件
     */
    private Mono<UploadContext> saveFile(UploadContext context) {
        return DataBufferUtils.write(context.filePart.content(), context.savePath)
                .then(Mono.fromCallable(() -> {
                    // 验证文件是否保存成功
                    File savedFile = context.savePath.toFile();
                    if (!savedFile.exists()) {
                        throw new RuntimeException("文件保存失败");
                    }

                    // 检查文件大小
                    long fileSize = savedFile.length();
                    long maxSize = context.fileType.equals("image") ? MAX_IMAGE_SIZE : MAX_VIDEO_SIZE;
                    if (fileSize > maxSize) {
                        // 删除过大的文件
                        savedFile.delete();
                        throw new IllegalArgumentException(
                                String.format("文件过大: %.2fMB，最大允许: %.2fMB",
                                        fileSize / (1024.0 * 1024.0), maxSize / (1024.0 * 1024.0)));
                    }

                    context.fileSize = fileSize;
                    log.info("文件保存成功: {} ({}字节)", context.savePath, fileSize);
                    return context;
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 创建成功响应
     */
    private Map<String, Object> createSuccessResponse(UploadContext context) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "文件上传成功");
        response.put("originalFilename", context.originalFilename);
        response.put("filename", context.uniqueFilename);
        response.put("filePath", context.savePath.toString());
        response.put("relativePath", context.savePath.toString().replace(uploadDir + File.separator, ""));
        response.put("fileSize", context.fileSize);
        response.put("fileType", context.fileType);
        response.put("uploadTime", LocalDateTime.now().toString());
        response.put("fileId", UUID.randomUUID().toString()); // 用于后续操作的文件ID

        return response;
    }

    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", errorMessage);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    /**
     * 上传上下文类
     */
    private static class UploadContext {
        final FilePart filePart;
        final Path savePath;
        final String originalFilename;
        final String uniqueFilename;
        final String fileType;
        long fileSize;

        UploadContext(FilePart filePart, Path savePath, String originalFilename,
                      String uniqueFilename, String fileType) {
            this.filePart = filePart;
            this.savePath = savePath;
            this.originalFilename = originalFilename;
            this.uniqueFilename = uniqueFilename;
            this.fileType = fileType;
        }
    }
}
