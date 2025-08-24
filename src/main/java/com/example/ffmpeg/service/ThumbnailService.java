package com.example.ffmpeg.service;

public interface ThumbnailService {
    /**
     * 生成视频缩略图
     * @param videoPath 视频文件路径
     * @param outputPath 输出缩略图路径
     * @param timestamp 指定时间戳（秒）
     * @param width 缩略图宽度
     * @param height 缩略图高度
     * @return 生成的缩略图文件路径
     */
    String generateThumbnail(String videoPath, String outputPath, double timestamp, int width, int height) throws Exception;
} 