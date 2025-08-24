// src/main/java/com/example/ffmpeg/controller/SystemStatusController.java
package com.example.ffmpeg.controller;

import com.example.ffmpeg.service.QwenApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/drone")
@RequiredArgsConstructor
public class SystemStatusController {

    private final QwenApiService qwenApiService;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${spring.application.name:drone-detection-system}")
    private String applicationName;

    @Value("${drone.inspection.storage.upload-dir:uploads}")
    private String uploadDir;

    /**
     * 系统状态检查
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, Object>>> getSystemStatus() {
        return Mono.fromCallable(() -> {
            Map<String, Object> status = new HashMap<>();

            try {
                // 基本信息
                status.put("applicationName", applicationName);
                status.put("serverPort", serverPort);
                status.put("timestamp", LocalDateTime.now().toString());
                status.put("status", "RUNNING");

                // 系统资源信息
                OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
                MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

                Map<String, Object> systemInfo = new HashMap<>();
                systemInfo.put("os", System.getProperty("os.name"));
                systemInfo.put("osVersion", System.getProperty("os.version"));
                systemInfo.put("javaVersion", System.getProperty("java.version"));
                systemInfo.put("processors", osBean.getAvailableProcessors());

                // 内存信息
                Map<String, Object> memoryInfo = new HashMap<>();
                memoryInfo.put("heapUsed", memoryBean.getHeapMemoryUsage().getUsed());
                memoryInfo.put("heapMax", memoryBean.getHeapMemoryUsage().getMax());
                memoryInfo.put("heapCommitted", memoryBean.getHeapMemoryUsage().getCommitted());
                memoryInfo.put("nonHeapUsed", memoryBean.getNonHeapMemoryUsage().getUsed());

                systemInfo.put("memory", memoryInfo);
                status.put("system", systemInfo);

                // 存储信息
                Map<String, Object> storageInfo = new HashMap<>();
                File uploadDirectory = new File(uploadDir);
                if (!uploadDirectory.exists()) {
                    uploadDirectory.mkdirs();
                }

                long totalSpace = uploadDirectory.getTotalSpace();
                long freeSpace = uploadDirectory.getFreeSpace();
                long usedSpace = totalSpace - freeSpace;

                storageInfo.put("uploadDir", uploadDirectory.getAbsolutePath());
                storageInfo.put("totalSpace", totalSpace);
                storageInfo.put("freeSpace", freeSpace);
                storageInfo.put("usedSpace", usedSpace);
                storageInfo.put("usagePercent", (double) usedSpace / totalSpace * 100);

                status.put("storage", storageInfo);

                // 服务状态
                Map<String, Object> services = new HashMap<>();
                services.put("imageDetection", "AVAILABLE");
                services.put("videoTracking", "AVAILABLE");
                services.put("database", "CONNECTED"); // 可以添加实际的数据库连接检查

                status.put("services", services);

                return ResponseEntity.ok(status);

            } catch (Exception e) {
                log.error("获取系统状态失败", e);
                status.put("status", "ERROR");
                status.put("error", e.getMessage());
                return ResponseEntity.internalServerError().body(status);
            }
        });
    }

    /**
     * API连接测试
     */
    @PostMapping("/test")
    public Mono<ResponseEntity<Map<String, Object>>> testApiConnection(@RequestBody Map<String, String> request) {
        String apiKey = request.get("apiKey");
        String model = request.get("model");

        if (apiKey == null || apiKey.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "API Key不能为空");
            return Mono.just(ResponseEntity.badRequest().body(error));
        }

        log.info("开始测试Qwen API连接，模型: {}", model != null ? model : "qwen2.5-vl-72b-instruct");

        return qwenApiService.testConnection(apiKey, model != null ? model : "qwen2.5-vl-72b-instruct")
                .map(success -> {
                    Map<String, Object> result = new HashMap<>();
                    if (success) {
                        result.put("success", true);
                        result.put("message", "API连接测试成功");
                        result.put("model", model);
                        result.put("testTime", LocalDateTime.now().toString());
                        log.info("Qwen API连接测试成功");
                        return ResponseEntity.ok(result);
                    } else {
                        result.put("success", false);
                        result.put("error", "API连接测试失败，请检查API Key和网络连接");
                        log.warn("Qwen API连接测试失败");
                        return ResponseEntity.badRequest().body(result);
                    }
                })
                .onErrorResume(ex -> {
                    log.error("API连接测试过程中发生错误", ex);
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("error", "API连接测试失败: " + ex.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(error));
                });
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> healthCheck() {
        return Mono.fromCallable(() -> {
            Map<String, Object> health = new HashMap<>();

            boolean isHealthy = true;
            StringBuilder details = new StringBuilder();

            try {
                // 检查内存使用情况
                MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
                long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
                long heapMax = memoryBean.getHeapMemoryUsage().getMax();
                double memoryUsagePercent = (double) heapUsed / heapMax * 100;

                if (memoryUsagePercent > 90) {
                    isHealthy = false;
                    details.append("内存使用率过高: ").append(String.format("%.1f%%", memoryUsagePercent)).append("; ");
                }

                // 检查磁盘空间
                File uploadDirectory = new File(uploadDir);
                if (uploadDirectory.exists()) {
                    long totalSpace = uploadDirectory.getTotalSpace();
                    long freeSpace = uploadDirectory.getFreeSpace();
                    double diskUsagePercent = (double) (totalSpace - freeSpace) / totalSpace * 100;

                    if (diskUsagePercent > 95) {
                        isHealthy = false;
                        details.append("磁盘使用率过高: ").append(String.format("%.1f%%", diskUsagePercent)).append("; ");
                    }
                }

                // 检查上传目录是否可写
                if (!uploadDirectory.exists() && !uploadDirectory.mkdirs()) {
                    isHealthy = false;
                    details.append("无法创建上传目录; ");
                } else if (!uploadDirectory.canWrite()) {
                    isHealthy = false;
                    details.append("上传目录不可写; ");
                }

                health.put("status", isHealthy ? "UP" : "DOWN");
                health.put("timestamp", LocalDateTime.now().toString());

                if (!isHealthy) {
                    health.put("details", details.toString());
                }

                // 添加详细的健康指标
                Map<String, Object> checks = new HashMap<>();
                checks.put("memory", memoryUsagePercent < 90 ? "UP" : "DOWN");
                checks.put("disk", true); // 简化的磁盘检查
                checks.put("uploadDir", uploadDirectory.canWrite() ? "UP" : "DOWN");
                health.put("checks", checks);

                return ResponseEntity.ok(health);

            } catch (Exception e) {
                log.error("健康检查失败", e);
                health.put("status", "DOWN");
                health.put("error", e.getMessage());
                return ResponseEntity.internalServerError().body(health);
            }
        });
    }

    /**
     * 获取版本信息
     */
    @GetMapping("/version")
    public Mono<ResponseEntity<Map<String, Object>>> getVersionInfo() {
        return Mono.fromCallable(() -> {
            Map<String, Object> version = new HashMap<>();

            try {
                // 从Maven属性或Manifest获取版本信息
                String implementationVersion = this.getClass().getPackage().getImplementationVersion();

                version.put("applicationName", applicationName);
                version.put("version", implementationVersion != null ? implementationVersion : "1.0.0-SNAPSHOT");
                version.put("buildTime", LocalDateTime.now().toString()); // 实际项目中应该从构建信息获取
                version.put("javaVersion", System.getProperty("java.version"));
                version.put("springBootVersion", "3.2.3"); // 可以从依赖中动态获取

                // 功能模块版本
                Map<String, String> modules = new HashMap<>();
                modules.put("javacv", "1.5.9");
                modules.put("opencv", "4.7.0");
                modules.put("ffmpeg", "6.0");
                modules.put("qwen-api", "2.5-vl-72b");
                version.put("modules", modules);

                return ResponseEntity.ok(version);

            } catch (Exception e) {
                log.error("获取版本信息失败", e);
                version.put("error", e.getMessage());
                return ResponseEntity.internalServerError().body(version);
            }
        });
    }

    /**
     * 清理临时文件
     */
    @PostMapping("/cleanup")
    public Mono<ResponseEntity<Map<String, Object>>> cleanupTempFiles() {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();

            try {
                int deletedFiles = 0;
                long freedSpace = 0L;

                // 清理上传目录中的临时文件
                File uploadDirectory = new File(uploadDir);
                if (uploadDirectory.exists()) {
                    File tempDir = new File(uploadDirectory, "temp");
                    if (tempDir.exists()) {
                        deletedFiles = cleanupDirectory(tempDir);
                        // 这里可以计算释放的空间大小
                    }
                }

                // 清理过期的输出文件（可选）
                File outputDir = new File("output");
                if (outputDir.exists()) {
                    // 删除7天前的文件
                    long cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
                    deletedFiles += cleanupOldFiles(outputDir, cutoffTime);
                }

                result.put("success", true);
                result.put("deletedFiles", deletedFiles);
                result.put("freedSpace", freedSpace);
                result.put("cleanupTime", LocalDateTime.now().toString());

                log.info("临时文件清理完成，删除文件数: {}", deletedFiles);
                return ResponseEntity.ok(result);

            } catch (Exception e) {
                log.error("清理临时文件失败", e);
                result.put("success", false);
                result.put("error", e.getMessage());
                return ResponseEntity.internalServerError().body(result);
            }
        });
    }

    /**
     * 清理目录中的所有文件
     */
    private int cleanupDirectory(File directory) {
        int count = 0;
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        if (file.delete()) {
                            count++;
                        }
                    } else if (file.isDirectory()) {
                        count += cleanupDirectory(file);
                        file.delete(); // 删除空目录
                    }
                }
            }
        }
        return count;
    }

    /**
     * 清理过期文件
     */
    private int cleanupOldFiles(File directory, long cutoffTime) {
        int count = 0;
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.lastModified() < cutoffTime) {
                        if (file.delete()) {
                            count++;
                        }
                    } else if (file.isDirectory()) {
                        count += cleanupOldFiles(file, cutoffTime);
                    }
                }
            }
        }
        return count;
    }
}
