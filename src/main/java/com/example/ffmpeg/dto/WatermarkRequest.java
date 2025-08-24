package com.example.ffmpeg.dto;

import lombok.Data;

@Data
public class WatermarkRequest {
    /**
     * 输入视频路径
     */
    private String inputPath;

    /**
     * 输出视频路径
     */
    private String outputPath;

    /**
     * 水印图片路径
     */
    private String watermarkPath;

    /**
     * 水印位置 (topleft, topright, bottomleft, bottomright, center)
     */
    private String position = "bottomright";

    /**
     * 水印透明度 (0-1)
     */
    private float opacity = 1.0f;

    /**
     * 水印边距（像素）
     */
    private int margin = 10;

    /**
     * 水印缩放比例 (0-1)
     */
    private float scale = 0.1f;

    /**
     * 是否保持原视频质量
     */
    private boolean preserveQuality = true;
} 