package com.example.ffmpeg.repository;

import com.example.ffmpeg.entity.VideoDetection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface VideoDetectionRepository extends ReactiveCrudRepository<VideoDetection, Long> {

    // 根据状态查询
    Flux<VideoDetection> findByStatusOrderByCreatedAtDesc(VideoDetection.DetectionStatus status, Pageable pageable);

    // 根据时间范围查询
    Flux<VideoDetection> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    // 统计处理中的任务
    Mono<Long> countByStatus(VideoDetection.DetectionStatus status);

    // 根据视频名称查询
    Mono<VideoDetection> findByVideoName(String videoName);

    // 查询最近的检测记录
    Flux<VideoDetection> findTop10ByOrderByCreatedAtDesc();

    // 统计平均处理时间
    @Query("SELECT AVG(processing_time_ms) FROM video_detections WHERE status = :status AND created_at BETWEEN :startTime AND :endTime")
    Mono<Long> avgProcessingTimeByStatusAndCreatedAtBetween(
            VideoDetection.DetectionStatus status, LocalDateTime startTime, LocalDateTime endTime);
}
