package com.example.ffmpeg.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Table("detection_details")
public class DetectionDetail {
    @Id
    private Long id;

    @Column("detection_type")
    private DetectionType detectionType;

    @Column("detection_id")
    private Long detectionId;

    @Column("frame_number")
    private Integer frameNumber;

    @Column("timestamp_ms")
    private Long timestampMs;

    @Column("person_id")
    private Integer personId;

    @Column("bbox_x1")
    private BigDecimal bboxX1;

    @Column("bbox_y1")
    private BigDecimal bboxY1;

    @Column("bbox_x2")
    private BigDecimal bboxX2;

    @Column("bbox_y2")
    private BigDecimal bboxY2;

    @Column("confidence")
    private BigDecimal confidence;

    @Column("description")
    private String description;

    @Column("tracker_id")
    private Integer trackerId;

    @Column("track_status")
    private TrackStatus trackStatus;

    @Column("created_at")
    private LocalDateTime createdAt;

    public enum DetectionType {
        IMAGE, VIDEO
    }

    public enum TrackStatus {
        ACTIVE, LOST, REMOVED
    }
}
