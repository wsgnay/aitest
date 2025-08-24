package com.example.ffmpeg.dto;

import lombok.Data;

@Data
public class StreamTrackingRequest {

    /**
     * 视频流URL或摄像头设备ID
     * 例如: "rtsp://example.com/stream", "0" (摄像头), "http://example.com/stream.m3u8"
     */
    private String streamUrl;

    /**
     * Qwen API密钥
     */
    private String apiKey;

    /**
     * 检测置信度阈值 (0.0-1.0)
     */
    private Double confThreshold = 0.5;

    /**
     * 跟踪器类型: MIL, CSRT, KCF
     */
    private String trackerType = "MIL";

    /**
     * 检测间隔（帧数）
     */
    private Integer detectionInterval = 30;

    /**
     * 最大检测次数（API调用限制）
     */
    private Integer maxDetections = 100;

    /**
     * 是否启用告警
     */
    private Boolean enableAlerts = false;

    /**
     * 是否保存检测图像
     */
    private Boolean saveImages = false;

    /**
     * 输出目录（可选）
     */
    private String outputDir;

    /**
     * 备注信息
     */
    private String description;
}