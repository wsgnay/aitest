package com.example.ffmpeg.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;

/**
 * 无人机视频跟踪请求DTO
 */
@Data
public class DroneVideoRequest {

    /** 视频源路径 */
    @NotBlank(message = "视频源路径不能为空")
    private String videoSource;

    /** 输出路径 */
    private String outputPath;

    /** API密钥 */
    @NotBlank(message = "API Key不能为空")
    private String apiKey;

    /** 置信度阈值 */
    @DecimalMin(value = "0.1", message = "置信度阈值不能小于0.1")
    @DecimalMax(value = "1.0", message = "置信度阈值不能大于1.0")
    private Double confThreshold = 0.5;

    /** 跟踪器类型 */
    private String trackerType = "MIL";

    /** 最大图像尺寸 */
    private Integer maxImageSize = 1024;

    /** 是否启用自动去重 */
    private Boolean enableAutoDedup = true;

    /** 检测帧列表 */
    private java.util.List<Integer> detectionFrames;

    /** 最大API调用次数 */
    private Integer maxDetectionCalls = 4;

    /** 最小检测间隔帧数 */
    private Integer minDetectionInterval = 90;

    /** IoU去重阈值 */
    private Double iouThreshold = 0.05;

    /** 重叠比例阈值 */
    private Double overlapThreshold = 0.4;

    /** 最大丢失帧数 */
    private Integer maxLostFrames = 30;

    /** 模型名称 */
    private String modelName = "qwen2.5-vl-72b-instruct";

    /** API超时时间（秒） */
    private Integer apiTimeout = 120;

    /** 是否保存输出视频 */
    private Boolean saveVideo = true;

    /** 是否显示实时预览 */
    private Boolean showPreview = false;
}
