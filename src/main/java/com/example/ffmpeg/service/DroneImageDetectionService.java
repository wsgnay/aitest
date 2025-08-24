package com.example.ffmpeg.service;

import com.example.ffmpeg.dto.DroneImageRequest;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 无人机图像检测服务接口
 */
public interface DroneImageDetectionService {

    /**
     * 检测并可视化无人机图像中的人物
     *
     * @param request 检测请求
     * @param apiKey API密钥
     * @return 检测结果
     */
    Mono<Map<String, Object>> detectAndVisualizePersons(DroneImageRequest request, String apiKey);

}
