package com.example.ffmpeg.controller;

import com.example.ffmpeg.dto.ClipRequest;
import com.example.ffmpeg.dto.WatermarkRequest;
import com.example.ffmpeg.service.ClipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/media/clip")
public class ClipController {

    @Autowired
    private ClipService clipService;

    @PostMapping("/cut")
    public ResponseEntity<?> clipVideo(@RequestBody ClipRequest request) {
        try {
            String outputPath = clipService.clipVideo(
                request.getInputPath(),
                request.getOutputPath(),
                request.getStartTime(),
                request.getDuration(),
                request.isPreserveQuality(),
                request.getVideoCodec(),
                request.getAudioCodec()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("outputPath", outputPath);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/split")
    public ResponseEntity<?> splitVideo(@RequestBody Map<String, Object> request) {
        try {
            String inputPath = (String) request.get("inputPath");
            String outputPattern = (String) request.get("outputPattern");
            double segmentDuration = Double.parseDouble(request.get("segmentDuration").toString());

            List<String> outputPaths = clipService.splitVideo(inputPath, outputPattern, segmentDuration);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("outputPaths", outputPaths);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/merge")
    public ResponseEntity<?> mergeVideos(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> inputPaths = (List<String>) request.get("inputPaths");
            String outputPath = (String) request.get("outputPath");
            String transition = (String) request.get("transition");

            if (inputPaths == null || inputPaths.isEmpty()) {
                throw new IllegalArgumentException("输入视频列表不能为空");
            }

            String result = clipService.mergeVideos(inputPaths, outputPath, transition);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("outputPath", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/lossless")
    public ResponseEntity<?> losslessClip(@RequestBody Map<String, Object> request) {
        try {
            String inputPath = (String) request.get("inputPath");
            String outputPath = (String) request.get("outputPath");
            double startTime = Double.parseDouble(request.get("startTime").toString());
            double duration = Double.parseDouble(request.get("duration").toString());

            String result = clipService.losslessClip(inputPath, outputPath, startTime, duration);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("outputPath", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/keyframes")
    public Mono<Map<String, Object>> getKeyframes(@RequestBody Map<String, Object> request) {
        String inputPath = (String) request.get("inputPath");
        String outputDir = (String) request.getOrDefault("outputDir", null);
        boolean extractImages = (boolean) request.getOrDefault("extractImages", false);
        String imageFormat = (String) request.getOrDefault("imageFormat", "jpg");
        int imageQuality = ((Number) request.getOrDefault("imageQuality", 95)).intValue();

        return clipService.getKeyframes(inputPath, outputDir, extractImages, imageFormat, imageQuality)
                .map(result -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("keyframes", result);
                    return response;
                })
                .onErrorResume(e -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("error", e.getMessage());
                    return Mono.just(response);
                });
    }

    @PostMapping("/watermark")
    public Mono<Map<String, Object>> addWatermark(@RequestBody WatermarkRequest request) {
        return clipService.addWatermark(request);
    }
} 