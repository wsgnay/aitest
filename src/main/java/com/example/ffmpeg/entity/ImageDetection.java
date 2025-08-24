package com.example.ffmpeg.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Table("image_detections")
public class ImageDetection {
    @Id
    private Long id;

    @Column("image_path")
    private String imagePath;

    @Column("image_name")
    private String imageName;

    @Column("image_size")
    private Long imageSize;

    @Column("image_width")
    private Integer imageWidth;

    @Column("image_height")
    private Integer imageHeight;

    @Column("output_path")
    private String outputPath;

    @Column("confidence_threshold")
    private BigDecimal confidenceThreshold;

    @Column("max_image_size")
    private Integer maxImageSize;

    @Column("model_name")
    private String modelName;

    @Column("person_count")
    private Integer personCount;

    @Column("detection_result")
    private String detectionResult; // JSON格式存储

    @Column("processing_time_ms")
    private Long processingTimeMs;

    @Column("api_call_time_ms")
    private Long apiCallTimeMs;

    @Column("status")
    private DetectionStatus status;

    @Column("error_message")
    private String errorMessage;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    public enum DetectionStatus {
        PROCESSING, SUCCESS, FAILED
    }
}
