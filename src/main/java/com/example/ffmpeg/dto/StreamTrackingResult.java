package com.example.ffmpeg.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class StreamTrackingResult {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 帧编号
     */
    private Integer frameNumber;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 当前帧检测到的人数
     */
    private Integer currentPersonCount = 0;

    /**
     * 累计检测次数（API调用次数）
     */
    private Integer totalDetections = 0;

    /**
     * 处理时间（毫秒）
     */
    private Long processingTimeMs = 0L;

    /**
     * 检测详情列表
     */
    private List<DetectionInfo> detections;

    /**
     * 是否触发告警
     */
    private Boolean alertTriggered = false;

    /**
     * 告警信息
     */
    private String alertMessage;

    /**
     * 检测详情
     */
    @Data
    public static class DetectionInfo {

        /**
         * 跟踪器ID
         */
        private Integer trackerId;

        /**
         * 边界框 [x1, y1, x2, y2]
         */
        private double[] bbox;

        /**
         * 置信度
         */
        private Double confidence;

        /**
         * 跟踪器类型
         */
        private String trackerType;

        /**
         * 检测到的帧编号
         */
        private Integer detectionFrame;

        /**
         * 持续跟踪帧数
         */
        private Integer trackingFrames;
    }
}