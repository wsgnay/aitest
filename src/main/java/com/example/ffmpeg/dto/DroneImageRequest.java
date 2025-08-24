package com.example.ffmpeg.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;

@Data
public class DroneImageRequest {

    @NotBlank(message = "图像路径不能为空")
    private String imagePath;

    private String outputPath;

    private boolean showResult = true;

    @DecimalMin(value = "0.1", message = "置信度阈值不能小于0.1")
    @DecimalMax(value = "1.0", message = "置信度阈值不能大于1.0")
    private double confThreshold = 0.3;

    private int maxImageSize = 1024;

    private String apiKey;

    private String modelName = "qwen2.5-vl-72b-instruct";

    private Integer apiTimeout = 120;

    private Boolean saveOutput = true;
}
