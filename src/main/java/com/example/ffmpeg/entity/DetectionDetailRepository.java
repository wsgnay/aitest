package com.example.ffmpeg.repository;

import com.example.ffmpeg.entity.DetectionDetail;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface DetectionDetailRepository extends ReactiveCrudRepository<DetectionDetail, Long> {

    // 根据检测ID查询详情
    Flux<DetectionDetail> findByDetectionTypeAndDetectionIdOrderByFrameNumber(
            DetectionDetail.DetectionType detectionType, Long detectionId);

    // 根据跟踪器ID查询
    Flux<DetectionDetail> findByTrackerIdOrderByFrameNumber(Integer trackerId);

    // 统计检测到的人数
    Mono<Long> countByDetectionTypeAndDetectionId(
            DetectionDetail.DetectionType detectionType, Long detectionId);

    // 查询特定帧的检测结果
    Flux<DetectionDetail> findByDetectionTypeAndDetectionIdAndFrameNumber(
            DetectionDetail.DetectionType detectionType, Long detectionId, Integer frameNumber);
}
