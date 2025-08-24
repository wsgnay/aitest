package com.example.ffmpeg.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TranscodeRequest {
    @NotBlank(message = "输入文件路径不能为空")
    private String inputPath;

    @NotBlank(message = "输出文件路径不能为空")
    private String outputPath;

    private String videoCodec = "h264";
    private String audioCodec = "aac";
    private String resolution;  // 例如：1920x1080
    private String bitrate;    // 例如：2M
}
