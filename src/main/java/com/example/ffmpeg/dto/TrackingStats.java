package com.example.ffmpeg.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 跟踪统计信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrackingStats {

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
