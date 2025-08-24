package com.example.ffmpeg.service;

import com.example.ffmpeg.dto.WatermarkRequest;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;

public interface ClipService {
    /**
     * 剪辑视频
     * @param inputPath 输入视频路径
     * @param outputPath 输出视频路径
     * @param startTime 开始时间（秒）
     * @param duration 持续时间（秒）
     * @param preserveQuality 是否保持原视频质量
     * @param videoCodec 视频编码（可选）
     * @param audioCodec 音频编码（可选）
     * @return 输出视频路径
     */
    String clipVideo(String inputPath, String outputPath, double startTime, double duration, 
                    boolean preserveQuality, String videoCodec, String audioCodec) throws Exception;

    /**
     * 分割视频为多个片段
     * @param inputPath 输入视频路径
     * @param outputPattern 输出文件名模式（例如：output_%d.mp4）
     * @param segmentDuration 每个片段的持续时间（秒）
     * @return 生成的视频片段路径列表
     */
    java.util.List<String> splitVideo(String inputPath, String outputPattern, double segmentDuration) throws Exception;

    /**
     * 合并多个视频
     * @param inputPaths 输入视频路径列表
     * @param outputPath 输出视频路径
     * @param transition 转场效果（可选）
     * @return 输出视频路径
     */
    String mergeVideos(java.util.List<String> inputPaths, String outputPath, String transition) throws Exception;

    /**
     * 无损剪辑（仅支持某些格式）
     * @param inputPath 输入视频路径
     * @param outputPath 输出视频路径
     * @param startTime 开始时间（秒）
     * @param duration 持续时间（秒）
     * @return 输出视频路径
     */
    String losslessClip(String inputPath, String outputPath, double startTime, double duration) throws Exception;

    Mono<List<Map<String, Object>>> getKeyframes(String inputPath, String outputDir, boolean extractImages, String imageFormat, int imageQuality);

    /**
     * 为视频添加水印
     * @param request 水印请求参数
     * @return 处理结果
     */
    Mono<Map<String, Object>> addWatermark(WatermarkRequest request);
} 