package com.example.ffmpeg.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Table("video_detections")
public class VideoDetection {
    @Id
    private Long id;

    @Column("video_path")
    private String videoPath;

    @Column("video_name")
    private String videoName;

    @Column("video_size")
    private Long videoSize;

    @Column("video_duration")
    private BigDecimal videoDuration;

    @Column("video_fps")
    private Integer videoFps;

    @Column("video_width")
    private Integer videoWidth;

    @Column("video_height")
    private Integer videoHeight;

    @Column("output_path")
    private String outputPath;

    @Column("confidence_threshold")
    private BigDecimal confidenceThreshold;

    @Column("tracker_type")
    private String trackerType;

    @Column("model_name")
    private String modelName;

    @Column("detection_frames")
    private String detectionFrames; // JSON格式

    @Column("auto_dedup_enabled")
    private Boolean autoDedupEnabled;

    @Column("total_frames")
    private Integer totalFrames;

    @Column("processed_frames")
    private Integer processedFrames;

    @Column("max_person_count")
    private Integer maxPersonCount;

    @Column("total_api_calls")
    private Integer totalApiCalls;

    @Column("dedup_operations")
    private Integer dedupOperations;

    @Column("active_trackers")
    private Integer activeTrackers;

    @Column("tracking_result")
    private String trackingResult; // JSON格式

    @Column("processing_time_ms")
    private Long processingTimeMs;

    @Column("status")
    private DetectionStatus status;

    @Column("progress")
    private BigDecimal progress;

    @Column("error_message")
    private String errorMessage;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    public enum DetectionStatus {
        PROCESSING, SUCCESS, FAILED, CANCELLED
    }
}
