package com.example.ffmpeg.repository;

import com.example.ffmpeg.entity.ImageDetection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface ImageDetectionRepository extends ReactiveCrudRepository<ImageDetection, Long> {

    // 根据状态查询
    Flux<ImageDetection> findByStatusOrderByCreatedAtDesc(ImageDetection.DetectionStatus status, Pageable pageable);

    // 根据时间范围查询
    Flux<ImageDetection> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    // 统计成功检测数量
    Mono<Long> countByStatusAndCreatedAtBetween(
            ImageDetection.DetectionStatus status, LocalDateTime startTime, LocalDateTime endTime);

    // 统计总人数
    @Query("SELECT SUM(person_count) FROM image_detections WHERE status = :status AND created_at BETWEEN :startTime AND :endTime")
    Mono<Long> sumPersonCountByStatusAndCreatedAtBetween(
            ImageDetection.DetectionStatus status, LocalDateTime startTime, LocalDateTime endTime);

    // 根据图像名称查询
    Mono<ImageDetection> findByImageName(String imageName);

    // 查询最近的检测记录
    Flux<ImageDetection> findTop10ByOrderByCreatedAtDesc();
}
