// src/main/java/com/example/ffmpeg/controller/DroneInspectionController.java
package com.example.ffmpeg.controller;

import com.example.ffmpeg.dto.DroneImageRequest;
import com.example.ffmpeg.dto.DroneVideoRequest;
import com.example.ffmpeg.dto.TrackingResult;
import com.example.ffmpeg.service.DroneImageDetectionService;
import com.example.ffmpeg.service.DroneVideoTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/drone")
@RequiredArgsConstructor
public class DroneInspectionController {

    private final DroneImageDetectionService imageDetectionService;
    private final DroneVideoTrackingService videoTrackingService;

    /**
     * 无人机图像人物检测
     */
    @PostMapping("/image/detect")
    public Mono<ResponseEntity<Map<String, Object>>> detectPersonsInImage(
            @RequestBody DroneImageRequest request,
            @RequestHeader("Authorization") String authorization) {

        return Mono.fromCallable(() -> {
                    // 提取API Key
                    String apiKey = extractApiKey(authorization);
                    if (apiKey == null || apiKey.isEmpty()) {
                        throw new IllegalArgumentException("缺少有效的API Key");
                    }
                    return apiKey;
                })
                .flatMap(apiKey -> imageDetectionService.detectAndVisualizePersons(request, apiKey))
                .map(ResponseEntity::ok)
                .onErrorResume(ex -> {
                    log.error("图像检测失败: {}", ex.getMessage(), ex);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", ex.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(errorResponse));
                });
    }

    /**
     * 无人机视频人物跟踪
     */
    @PostMapping("/video/track")
    public Mono<ResponseEntity<Map<String, Object>>> trackPersonsInVideo(
            @RequestBody DroneVideoRequest request) {

        return Mono.fromCallable(() -> {
                    // 验证请求参数
                    if (request.getVideoSource() == null || request.getVideoSource().trim().isEmpty()) {
                        throw new IllegalArgumentException("视频源路径不能为空");
                    }
                    if (request.getApiKey() == null || request.getApiKey().trim().isEmpty()) {
                        throw new IllegalArgumentException("API Key不能为空");
                    }

                    log.info("开始处理视频跟踪请求: {}", request.getVideoSource());
                    return request;
                })
                .flatMap(videoTrackingService::processVideoWithTracking)
                .map(result -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", result.isSuccess());
                    response.put("result", result.getResult());
                    response.put("outputVideoPath", result.getOutputVideoPath());

                    if (result.getMessage() != null) {
                        response.put("message", result.getMessage());
                    }

                    return ResponseEntity.ok(response);
                })
                .onErrorResume(ex -> {
                    log.error("视频跟踪失败: {}", ex.getMessage(), ex);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", ex.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(errorResponse));
                });
    }

    /**
     * 获取检测任务状态
     */
    @GetMapping("/task/{taskId}/status")
    public Mono<ResponseEntity<Map<String, Object>>> getTaskStatus(@PathVariable String taskId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> status = new HashMap<>();

            // 这里可以实现实际的任务状态查询逻辑
            // 目前返回模拟数据
            status.put("taskId", taskId);
            status.put("status", "PROCESSING"); // PENDING, PROCESSING, COMPLETED, FAILED
            status.put("progress", 65);
            status.put("message", "正在处理视频第65帧");
            status.put("estimatedTimeRemaining", 120); // 秒

            return ResponseEntity.ok(status);
        });
    }

    /**
     * 取消检测任务
     */
    @PostMapping("/task/{taskId}/cancel")
    public Mono<ResponseEntity<Map<String, Object>>> cancelTask(@PathVariable String taskId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();

            // 这里可以实现实际的任务取消逻辑
            log.info("取消任务: {}", taskId);

            result.put("success", true);
            result.put("taskId", taskId);
            result.put("message", "任务已取消");

            return ResponseEntity.ok(result);
        });
    }

    /**
     * 批量图像检测
     */
    @PostMapping("/image/batch-detect")
    public Mono<ResponseEntity<Map<String, Object>>> batchDetectImages(
            @RequestBody Map<String, Object> request,
            @RequestHeader("Authorization") String authorization) {

        return Mono.fromCallable(() -> {
                    String apiKey = extractApiKey(authorization);
                    if (apiKey == null || apiKey.isEmpty()) {
                        throw new IllegalArgumentException("缺少有效的API Key");
                    }

                    @SuppressWarnings("unchecked")
                    java.util.List<String> imagePaths = (java.util.List<String>) request.get("imagePaths");
                    if (imagePaths == null || imagePaths.isEmpty()) {
                        throw new IllegalArgumentException("图像路径列表不能为空");
                    }

                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("totalImages", imagePaths.size());
                    result.put("batchId", java.util.UUID.randomUUID().toString());
                    result.put("message", "批量检测任务已创建");

                    // 这里可以实现实际的批量处理逻辑
                    log.info("创建批量图像检测任务，图像数量: {}", imagePaths.size());

                    return ResponseEntity.ok(result);
                })
                .onErrorResume(ex -> {
                    log.error("批量图像检测失败: {}", ex.getMessage(), ex);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", ex.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(errorResponse));
                });
    }

    /**
     * 获取检测历史记录
     */
    @GetMapping("/history")
    public Mono<ResponseEntity<Map<String, Object>>> getDetectionHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {

        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();

            // 这里可以实现实际的历史记录查询
            java.util.List<Map<String, Object>> records = new java.util.ArrayList<>();

            // 模拟数据
            for (int i = 1; i <= size; i++) {
                Map<String, Object> record = new HashMap<>();
                record.put("id", i);
                record.put("type", i % 2 == 0 ? "image" : "video");
                record.put("fileName", "sample_" + i + (i % 2 == 0 ? ".jpg" : ".mp4"));
                record.put("personCount", (int) (Math.random() * 10));
                record.put("status", "success");
                record.put("createdAt", java.time.LocalDateTime.now().minusHours(i).toString());
                records.add(record);
            }

            result.put("success", true);
            result.put("data", records);
            result.put("page", page);
            result.put("size", size);
            result.put("total", 100); // 模拟总数

            return ResponseEntity.ok(result);
        });
    }

    /**
     * 获取检测统计信息
     */
    @GetMapping("/statistics")
    public Mono<ResponseEntity<Map<String, Object>>> getDetectionStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        return Mono.fromCallable(() -> {
            Map<String, Object> statistics = new HashMap<>();

            // 这里可以实现实际的统计查询
            statistics.put("totalImages", 156);
            statistics.put("totalVideos", 23);
            statistics.put("totalPersonsDetected", 892);
            statistics.put("totalApiCalls", 445);
            statistics.put("averageConfidence", 0.78);
            statistics.put("successRate", 0.95);

            // 按日期统计
            java.util.List<Map<String, Object>> dailyStats = new java.util.ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                Map<String, Object> dayStats = new HashMap<>();
                dayStats.put("date", java.time.LocalDate.now().minusDays(i).toString());
                dayStats.put("images", (int) (Math.random() * 20) + 5);
                dayStats.put("videos", (int) (Math.random() * 5) + 1);
                dayStats.put("persons", (int) (Math.random() * 100) + 20);
                dailyStats.add(dayStats);
            }
            statistics.put("dailyStats", dailyStats);

            statistics.put("success", true);
            return ResponseEntity.ok(statistics);
        });
    }

    /**
     * 从Authorization头中提取API Key
     */
    private String extractApiKey(String authorization) {
        if (authorization == null || authorization.trim().isEmpty()) {
            return null;
        }

        // 支持 "Bearer token" 格式
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }

        // 直接返回原始值（兼容性考虑）
        return authorization.trim();
    }
}
