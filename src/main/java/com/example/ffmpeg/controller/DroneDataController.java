// 修改 DroneDataController.java 使用真实数据库数据
package com.example.ffmpeg.controller;

import com.example.ffmpeg.entity.ImageDetection;
import com.example.ffmpeg.entity.VideoDetection;
import com.example.ffmpeg.service.DatabaseService;
import com.example.ffmpeg.repository.ImageDetectionRepository;
import com.example.ffmpeg.repository.VideoDetectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/drone/data")
@RequiredArgsConstructor
public class DroneDataController {

    private final DatabaseService databaseService;
    private final ImageDetectionRepository imageDetectionRepository;
    private final VideoDetectionRepository videoDetectionRepository;

    /**
     * 获取真实的历史记录数据
     */
    @GetMapping("/history")
    public Mono<ResponseEntity<Map<String, Object>>> getHistory(
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("获取历史记录请求 - filter: {}, status: {}, date: {}, page: {}, size: {}",
                filter, status, date, page, size);

        return getHistoryFromDatabase(filter, status, date, page, size)
                .map(historyData -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("data", historyData);
                    result.put("filter", filter);
                    result.put("status", status);
                    result.put("date", date);
                    result.put("page", page);
                    result.put("size", size);
                    result.put("timestamp", System.currentTimeMillis());

                    log.info("成功返回 {} 条历史记录",
                            historyData instanceof java.util.List ? ((java.util.List<?>) historyData).size() : 0);

                    return ResponseEntity.ok(result);
                })
                .onErrorResume(ex -> {
                    log.error("获取历史记录失败", ex);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("success", false);
                    errorResult.put("error", ex.getMessage());
                    errorResult.put("data", java.util.Collections.emptyList());
                    errorResult.put("total", 0);
                    return Mono.just(ResponseEntity.internalServerError().body(errorResult));
                });
    }

    /**
     * 从数据库获取历史记录
     */
    private Mono<java.util.List<Map<String, Object>>> getHistoryFromDatabase(
            String filter, String status, String date, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        LocalDateTime startTime = null;
        LocalDateTime endTime = null;

        // 解析日期过滤条件
        if (date != null && !date.isEmpty()) {
            try {
                LocalDate filterDate = LocalDate.parse(date);
                startTime = filterDate.atStartOfDay();
                endTime = filterDate.atTime(23, 59, 59);
            } catch (DateTimeParseException e) {
                log.warn("日期格式错误: {}", date);
            }
        }

        // 根据过滤条件构建查询
        Flux<Map<String, Object>> imageFlux = getImageDetections(filter, status, startTime, endTime, pageable);
        Flux<Map<String, Object>> videoFlux = getVideoDetections(filter, status, startTime, endTime, pageable);

        // 合并图像和视频检测记录
        return Flux.merge(imageFlux, videoFlux)
                .sort((a, b) -> {
                    // 按创建时间倒序排序
                    Object timeA = a.get("createdAt");
                    Object timeB = b.get("createdAt");
                    if (timeA instanceof LocalDateTime && timeB instanceof LocalDateTime) {
                        return ((LocalDateTime) timeB).compareTo((LocalDateTime) timeA);
                    }
                    return 0;
                })
                .take(size) // 限制返回数量
                .collectList();
    }

    /**
     * 获取图像检测记录
     */
    private Flux<Map<String, Object>> getImageDetections(
            String filter, String status, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {

        // 如果只要视频记录，则跳过图像记录
        if ("video".equals(filter)) {
            return Flux.empty();
        }

        Flux<ImageDetection> flux;

        if (startTime != null && endTime != null) {
            // 按时间范围查询
            flux = imageDetectionRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startTime, endTime, pageable);
        } else if (!"all".equals(status)) {
            // 按状态查询
            ImageDetection.DetectionStatus detectionStatus = parseImageStatus(status);
            if (detectionStatus != null) {
                flux = imageDetectionRepository.findByStatusOrderByCreatedAtDesc(detectionStatus, pageable);
            } else {
                flux = imageDetectionRepository.findAll().sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            }
        } else {
            // 查询所有记录
            flux = imageDetectionRepository.findTop10ByOrderByCreatedAtDesc()
                    .cast(ImageDetection.class);
        }

        return flux.map(this::convertImageDetectionToMap);
    }

    /**
     * 获取视频检测记录
     */
    private Flux<Map<String, Object>> getVideoDetections(
            String filter, String status, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {

        // 如果只要图像记录，则跳过视频记录
        if ("image".equals(filter)) {
            return Flux.empty();
        }

        Flux<VideoDetection> flux;

        if (startTime != null && endTime != null) {
            // 按时间范围查询
            flux = videoDetectionRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startTime, endTime, pageable);
        } else if (!"all".equals(status)) {
            // 按状态查询
            VideoDetection.DetectionStatus detectionStatus = parseVideoStatus(status);
            if (detectionStatus != null) {
                flux = videoDetectionRepository.findByStatusOrderByCreatedAtDesc(detectionStatus, pageable);
            } else {
                flux = videoDetectionRepository.findAll().sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            }
        } else {
            // 查询所有记录
            flux = videoDetectionRepository.findTop10ByOrderByCreatedAtDesc()
                    .cast(VideoDetection.class);
        }

        return flux.map(this::convertVideoDetectionToMap);
    }

    /**
     * 转换图像检测记录为Map
     */
    private Map<String, Object> convertImageDetectionToMap(ImageDetection detection) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", detection.getId());
        map.put("type", "image");
        map.put("fileName", detection.getImageName());
        map.put("name", detection.getImageName()); // 兼容字段
        map.put("personCount", detection.getPersonCount());
        map.put("persons", detection.getPersonCount()); // 兼容字段
        map.put("status", detection.getStatus().name().toLowerCase());
        map.put("processingTime", detection.getProcessingTimeMs() != null ?
                detection.getProcessingTimeMs() / 1000.0 : 0.0);
        map.put("createdAt", detection.getCreatedAt());
        map.put("confidence", detection.getConfidenceThreshold());
        map.put("modelName", detection.getModelName());
        map.put("imagePath", detection.getImagePath());

        // 添加格式化的时间字符串用于前端显示
        if (detection.getCreatedAt() != null) {
            map.put("createdAtStr", detection.getCreatedAt().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        // 如果有错误信息，也包含进去
        if (detection.getErrorMessage() != null) {
            map.put("errorMessage", detection.getErrorMessage());
        }

        return map;
    }

    /**
     * 转换视频检测记录为Map
     */
    private Map<String, Object> convertVideoDetectionToMap(VideoDetection detection) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", detection.getId());
        map.put("type", "video");
        map.put("fileName", detection.getVideoName());
        map.put("name", detection.getVideoName()); // 兼容字段
        map.put("personCount", detection.getMaxPersonCount() != null ? detection.getMaxPersonCount() : 0);
        map.put("persons", detection.getMaxPersonCount() != null ? detection.getMaxPersonCount() : 0); // 兼容字段
        map.put("status", detection.getStatus().name().toLowerCase());
        map.put("processingTime", detection.getProcessingTimeMs() != null ?
                detection.getProcessingTimeMs() / 1000.0 : 0.0);
        map.put("createdAt", detection.getCreatedAt());
        map.put("confidence", detection.getConfidenceThreshold());
        map.put("modelName", detection.getModelName());
        map.put("videoPath", detection.getVideoPath());
        map.put("outputPath", detection.getOutputPath());
        map.put("totalFrames", detection.getTotalFrames());
        map.put("processedFrames", detection.getProcessedFrames());
        map.put("activeTrackers", detection.getActiveTrackers());
        map.put("apiCalls", detection.getTotalApiCalls());

        // 添加格式化的时间字符串用于前端显示
        if (detection.getCreatedAt() != null) {
            map.put("createdAtStr", detection.getCreatedAt().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        // 如果有错误信息，也包含进去
        if (detection.getErrorMessage() != null) {
            map.put("errorMessage", detection.getErrorMessage());
        }

        return map;
    }

    /**
     * 解析图像检测状态
     */
    private ImageDetection.DetectionStatus parseImageStatus(String status) {
        try {
            return ImageDetection.DetectionStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("无效的图像检测状态: {}", status);
            return null;
        }
    }

    /**
     * 解析视频检测状态
     */
    private VideoDetection.DetectionStatus parseVideoStatus(String status) {
        try {
            return VideoDetection.DetectionStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("无效的视频检测状态: {}", status);
            return null;
        }
    }

    /**
     * 获取仪表板统计数据
     */
    @GetMapping("/dashboard/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getDashboardStats() {
        return databaseService.getDetectionStats()
                .flatMap(stats -> {
                    Map<String, Object> mutableStats = new HashMap<>(stats);
                    return databaseService.getRecentDetections(5)
                            .collectList()
                            .map(recentActivities -> {
                                mutableStats.put("recentActivities", recentActivities);
                                return ResponseEntity.ok(mutableStats);
                            });
                })
                .onErrorResume(ex -> {
                    log.error("获取仪表板统计数据失败", ex);
                    Map<String, Object> fallbackStats = createFallbackStats();
                    return Mono.just(ResponseEntity.ok(fallbackStats));
                });
    }

    /**
     * 获取统计汇总信息
     */
    @GetMapping("/statistics/summary")
    public Mono<ResponseEntity<Map<String, Object>>> getStatisticsSummary() {
        return Mono.zip(
                imageDetectionRepository.count(),
                videoDetectionRepository.count(),
                // 获取今天的检测数量
                imageDetectionRepository.countByStatusAndCreatedAtBetween(
                        ImageDetection.DetectionStatus.SUCCESS,
                        LocalDateTime.now().toLocalDate().atStartOfDay(),
                        LocalDateTime.now()
                ),
                videoDetectionRepository.countByStatus(VideoDetection.DetectionStatus.SUCCESS)
        ).map(tuple -> {
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalImageDetections", tuple.getT1());
            summary.put("totalVideoDetections", tuple.getT2());
            summary.put("todayImageDetections", tuple.getT3());
            summary.put("totalSuccessfulVideos", tuple.getT4());
            summary.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(summary);
        }).onErrorResume(ex -> {
            log.error("获取统计汇总失败", ex);
            Map<String, Object> error = Map.of(
                    "success", false,
                    "error", ex.getMessage()
            );
            return Mono.just(ResponseEntity.internalServerError().body(error));
        });
    }

    /**
     * 删除历史记录
     */
    @DeleteMapping("/history/{type}/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteHistoryRecord(
            @PathVariable String type,
            @PathVariable Long id) {

        Mono<Void> deleteMono;

        if ("image".equals(type)) {
            deleteMono = imageDetectionRepository.deleteById(id);
        } else if ("video".equals(type)) {
            deleteMono = videoDetectionRepository.deleteById(id);
        } else {
            Map<String, Object> error = Map.of(
                    "success", false,
                    "error", "无效的记录类型: " + type
            );
            return Mono.just(ResponseEntity.badRequest().body(error));
        }

        return deleteMono
                .then(Mono.fromCallable(() -> {
                    Map<String, Object> result = Map.of(
                            "success", true,
                            "message", "记录删除成功"
                    );
                    return ResponseEntity.ok(result);
                }))
                .onErrorResume(ex -> {
                    log.error("删除记录失败", ex);
                    Map<String, Object> error = Map.of(
                            "success", false,
                            "error", ex.getMessage()
                    );
                    return Mono.just(ResponseEntity.internalServerError().body(error));
                });
    }

    /**
     * 创建备用统计数据
     */
    private Map<String, Object> createFallbackStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalImages", 0);
        stats.put("totalVideos", 0);
        stats.put("totalPersons", 0);
        stats.put("apiCalls", 0);
        stats.put("recentActivities", java.util.Collections.emptyList());
        return stats;
    }
}
