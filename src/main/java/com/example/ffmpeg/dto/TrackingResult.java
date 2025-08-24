package com.example.ffmpeg.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 视频跟踪结果DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrackingResult {

    /** 是否成功 */
    private boolean success;

    /** 错误信息 */
    private String error;

    /** 消息信息 */
    private String message;

    /** 视频路径 */
    private String videoPath;

    /** 输出路径 */
    private String outputPath;

    /** 输出视频路径 */
    private String outputVideoPath;

    /** 处理开始时间 */
    private LocalDateTime startTime;

    /** 处理结束时间 */
    private LocalDateTime endTime;

    /** 总处理时间（毫秒） */
    private long processingTimeMs;

    /** 检测到的人物列表（按帧组织） */
    private Map<Integer, List<PersonDetection>> detectionsByFrame;

    /** 跟踪统计信息 */
    private TrackingStats stats;

    /** 跟踪器信息列表 */
    private List<TrackerInfo> trackers;

    /** 视频元数据 */
    private VideoMetadata videoMetadata;

    /** 消息列表 */
    private List<String> messages;

    /** 最大同时人数 */
    private int maxPersonCount;

    /** API调用次数 */
    private int apiCallCount;

    /** 总帧数 */
    private int totalFrames;

    /** 去重操作次数 */
    private int dedupOperations;

    /** 处理结果数据 */
    private Map<String, Object> result;

    /**
     * 跟踪统计信息
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TrackingStats {

        /** 开始时间 */
        private LocalDateTime startTime;

        /** 结束时间 */
        private LocalDateTime endTime;

        /** 总跟踪帧数 */
        private int totalFrames;

        /** API调用次数 */
        private int apiCalls;

        /** 最大同时跟踪人数 */
        private int maxPersonCount;

        /** 总处理时间（毫秒） */
        private long processingTimeMs;

        /** 活跃跟踪器数量 */
        private int activeTrackers;

        /** 平均检测置信度 */
        private double avgConfidence;

        /** 跟踪成功率 */
        private double trackingSuccessRate;

        /** 去重次数 */
        private int dedupCount;

        /** 丢失跟踪次数 */
        private int lostTrackingCount;

        /** 视频帧率 */
        private double fps;

        /** 视频时长（秒） */
        private double duration;
    }

    /**
     * 跟踪器信息
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TrackerInfo {
        private int id;
        private boolean active;
        private double confidence;
        private int lostFrames;
        private int lastUpdateFrame;
        private PersonDetection.BoundingBox lastBbox;
        private LocalDateTime createTime;
        private LocalDateTime lastUpdateTime;
    }

    /**
     * 视频元数据
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class VideoMetadata {
        private int width;
        private int height;
        private double fps;
        private long totalFrames;
        private double duration;
        private String format;
        private String codec;
        private long fileSize;
    }
}
