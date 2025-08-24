package com.example.ffmpeg.service;

import com.example.ffmpeg.dto.PersonDetection;
import com.example.ffmpeg.dto.TrackingResult;
import com.example.ffmpeg.entity.DetectionDetail;
import com.example.ffmpeg.entity.ImageDetection;
import com.example.ffmpeg.entity.VideoDetection;
import com.example.ffmpeg.repository.DetectionDetailRepository;
import com.example.ffmpeg.repository.ImageDetectionRepository;
import com.example.ffmpeg.repository.VideoDetectionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseService {

    private final ImageDetectionRepository imageDetectionRepository;
    private final VideoDetectionRepository videoDetectionRepository;
    private final DetectionDetailRepository detectionDetailRepository;
    private final ObjectMapper objectMapper;

    /**
     * 保存图像检测结果
     */
    public Mono<ImageDetection> saveImageDetection(String imagePath, String imageName,
                                                   Map<String, Object> detectionResult,
                                                   List<PersonDetection> detections,
                                                   long processingTimeMs, double confidence) {
        return Mono.fromCallable(() -> {
                    ImageDetection imageDetection = new ImageDetection();
                    imageDetection.setImagePath(imagePath);
                    imageDetection.setImageName(imageName);
                    imageDetection.setConfidenceThreshold(BigDecimal.valueOf(confidence));
                    imageDetection.setModelName("qwen2.5-vl-72b-instruct");
                    imageDetection.setPersonCount(detections != null ? detections.size() : 0);
                    imageDetection.setProcessingTimeMs(processingTimeMs);
                    imageDetection.setStatus(ImageDetection.DetectionStatus.SUCCESS);
                    imageDetection.setCreatedAt(LocalDateTime.now());
                    imageDetection.setUpdatedAt(LocalDateTime.now());

                    // 将检测结果转换为JSON
                    try {
                        imageDetection.setDetectionResult(objectMapper.writeValueAsString(detectionResult));
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to serialize detection result", e);
                        imageDetection.setDetectionResult("{}");
                    }

                    return imageDetection;
                })
                .flatMap(imageDetectionRepository::save)
                .flatMap(savedDetection -> {
                    // 保存详细检测信息
                    if (detections != null && !detections.isEmpty()) {
                        return saveImageDetectionDetails(savedDetection.getId(), detections)
                                .then(Mono.just(savedDetection));
                    }
                    return Mono.just(savedDetection);
                })
                .doOnSuccess(saved -> log.info("Saved image detection with ID: {}", saved.getId()))
                .onErrorResume(ex -> {
                    log.error("Failed to save image detection", ex);
                    return Mono.empty();
                });
    }

    /**
     * 保存视频检测结果
     */
    public Mono<VideoDetection> saveVideoDetection(String videoPath, String videoName,
                                                   TrackingResult trackingResult,
                                                   Map<String, Object> config) {
        return Mono.fromCallable(() -> {
                    VideoDetection videoDetection = new VideoDetection();
                    videoDetection.setVideoPath(videoPath);
                    videoDetection.setVideoName(videoName);
                    videoDetection.setOutputPath(trackingResult.getOutputPath());

                    // 从配置中获取参数
                    videoDetection.setConfidenceThreshold(BigDecimal.valueOf((Double) config.getOrDefault("confThreshold", 0.5)));
                    videoDetection.setTrackerType((String) config.getOrDefault("trackerType", "MIL"));
                    videoDetection.setModelName((String) config.getOrDefault("model", "qwen2.5-vl-72b-instruct"));
                    videoDetection.setAutoDedupEnabled((Boolean) config.getOrDefault("enableAutoDedup", true));

                    videoDetection.setTotalFrames(trackingResult.getTotalFrames()); // 修复：使用 getTotalFrames()
                    videoDetection.setProcessedFrames(trackingResult.getTotalFrames()); // 修复：使用 getTotalFrames()
                    videoDetection.setActiveTrackers(trackingResult.getMaxPersonCount()); // 修复：使用 getMaxPersonCount()
                    videoDetection.setTotalApiCalls(trackingResult.getApiCallCount()); // 修复：使用 getApiCallCount()
                    videoDetection.setDedupOperations(trackingResult.getDedupOperations());
                    videoDetection.setMaxPersonCount(trackingResult.getMaxPersonCount()); // 修复：使用 getMaxPersonCount()

                    videoDetection.setStatus(VideoDetection.DetectionStatus.SUCCESS);
                    videoDetection.setProgress(BigDecimal.valueOf(100.0));
                    videoDetection.setCreatedAt(LocalDateTime.now());
                    videoDetection.setUpdatedAt(LocalDateTime.now());

                    // 将跟踪结果转换为JSON
                    try {
                        videoDetection.setTrackingResult(objectMapper.writeValueAsString(trackingResult));
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to serialize tracking result", e);
                        videoDetection.setTrackingResult("{}");
                    }

                    return videoDetection;
                })
                .flatMap(videoDetectionRepository::save)
                .doOnSuccess(saved -> log.info("Saved video detection with ID: {}", saved.getId()))
                .onErrorResume(ex -> {
                    log.error("Failed to save video detection", ex);
                    return Mono.empty();
                });
    }

    /**
     * 保存图像检测详情
     */
    private Mono<Void> saveImageDetectionDetails(Long detectionId, List<PersonDetection> detections) {
        return Flux.fromIterable(detections)
                .index()
                .flatMap(indexedDetection -> {
                    int index = indexedDetection.getT1().intValue();
                    PersonDetection detection = indexedDetection.getT2();

                    DetectionDetail detail = new DetectionDetail();
                    detail.setDetectionType(DetectionDetail.DetectionType.IMAGE);
                    detail.setDetectionId(detectionId);
                    detail.setPersonId(index + 1);

                    double[] bbox = detection.getBbox();
                    detail.setBboxX1(BigDecimal.valueOf(bbox[0]));
                    detail.setBboxY1(BigDecimal.valueOf(bbox[1]));
                    detail.setBboxX2(BigDecimal.valueOf(bbox[2]));
                    detail.setBboxY2(BigDecimal.valueOf(bbox[3]));
                    detail.setConfidence(BigDecimal.valueOf(detection.getConfidence()));
                    detail.setDescription(detection.getDescription());
                    detail.setCreatedAt(LocalDateTime.now());

                    return detectionDetailRepository.save(detail);
                })
                .then();
    }

    /**
     * 更新处理进度
     */
    public Mono<VideoDetection> updateVideoProgress(Long detectionId, double progress, String status) {
        return videoDetectionRepository.findById(detectionId)
                .flatMap(detection -> {
                    detection.setProgress(BigDecimal.valueOf(progress));
                    if (status != null) {
                        detection.setStatus(VideoDetection.DetectionStatus.valueOf(status.toUpperCase()));
                    }
                    detection.setUpdatedAt(LocalDateTime.now());
                    return videoDetectionRepository.save(detection);
                });
    }

    /**
     * 标记检测失败
     */
    public Mono<Void> markDetectionFailed(Long detectionId, String errorMessage, String type) {
        if ("IMAGE".equals(type)) {
            return imageDetectionRepository.findById(detectionId)
                    .flatMap(detection -> {
                        detection.setStatus(ImageDetection.DetectionStatus.FAILED);
                        detection.setErrorMessage(errorMessage);
                        detection.setUpdatedAt(LocalDateTime.now());
                        return imageDetectionRepository.save(detection);
                    })
                    .then();
        } else {
            return videoDetectionRepository.findById(detectionId)
                    .flatMap(detection -> {
                        detection.setStatus(VideoDetection.DetectionStatus.FAILED);
                        detection.setErrorMessage(errorMessage);
                        detection.setUpdatedAt(LocalDateTime.now());
                        return videoDetectionRepository.save(detection);
                    })
                    .then();
        }
    }

    /**
     * 获取检测统计信息
     */
    public Mono<Map<String, Object>> getDetectionStats() {
        return Mono.zip(
                imageDetectionRepository.countByStatusAndCreatedAtBetween(
                        ImageDetection.DetectionStatus.SUCCESS,
                        LocalDateTime.now().minusDays(30),
                        LocalDateTime.now()
                ),
                videoDetectionRepository.countByStatus(VideoDetection.DetectionStatus.SUCCESS),
                imageDetectionRepository.sumPersonCountByStatusAndCreatedAtBetween(
                        ImageDetection.DetectionStatus.SUCCESS,
                        LocalDateTime.now().minusDays(30),
                        LocalDateTime.now()
                )
        ).map(tuple -> Map.of(
                "totalImages", tuple.getT1(),
                "totalVideos", tuple.getT2(),
                "totalPersons", tuple.getT3() != null ? tuple.getT3() : 0L,
                "apiCalls", tuple.getT1() + tuple.getT2() // 简化计算
        ));
    }

    /**
     * 获取最近的检测记录
     */
    public Flux<Map<String, Object>> getRecentDetections(int limit) {
        return Flux.merge(
                imageDetectionRepository.findTop10ByOrderByCreatedAtDesc()
                        .map(this::convertImageToMap),
                videoDetectionRepository.findTop10ByOrderByCreatedAtDesc()
                        .map(this::convertVideoToMap)
        ).take(limit);
    }

    private Map<String, Object> convertImageToMap(ImageDetection detection) {
        return Map.of(
                "id", detection.getId(),
                "type", "image",
                "name", detection.getImageName(),
                "persons", detection.getPersonCount(),
                "status", detection.getStatus().name().toLowerCase(),
                "createdAt", detection.getCreatedAt(),
                "processingTime", detection.getProcessingTimeMs() != null ?
                        detection.getProcessingTimeMs() / 1000.0 : 0.0
        );
    }

    private Map<String, Object> convertVideoToMap(VideoDetection detection) {
        return Map.of(
                "id", detection.getId(),
                "type", "video",
                "name", detection.getVideoName(),
                "persons", detection.getMaxPersonCount() != null ? detection.getMaxPersonCount() : 0,
                "status", detection.getStatus().name().toLowerCase(),
                "createdAt", detection.getCreatedAt(),
                "processingTime", detection.getProcessingTimeMs() != null ?
                        detection.getProcessingTimeMs() / 1000.0 : 0.0
        );
    }
}
