package com.example.ffmpeg.dto;

public class ClipRequest {
    private String inputPath;        // 输入视频路径
    private String outputPath;       // 输出视频路径
    private double startTime;        // 开始时间（秒）
    private double duration;         // 持续时间（秒）
    private boolean preserveQuality; // 是否保持原视频质量
    private String videoCodec;       // 视频编码（可选）
    private String audioCodec;       // 音频编码（可选）

    // Getters and Setters
    public String getInputPath() {
        return inputPath;
    }

    public void setInputPath(String inputPath) {
        this.inputPath = inputPath;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public double getStartTime() {
        return startTime;
    }

    public void setStartTime(double startTime) {
        this.startTime = startTime;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public boolean isPreserveQuality() {
        return preserveQuality;
    }

    public void setPreserveQuality(boolean preserveQuality) {
        this.preserveQuality = preserveQuality;
    }

    public String getVideoCodec() {
        return videoCodec;
    }

    public void setVideoCodec(String videoCodec) {
        this.videoCodec = videoCodec;
    }

    public String getAudioCodec() {
        return audioCodec;
    }

    public void setAudioCodec(String audioCodec) {
        this.audioCodec = audioCodec;
    }
} 