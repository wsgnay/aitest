
package com.example.ffmpeg.controller;

import com.example.ffmpeg.dto.StreamTrackingRequest;
import com.example.ffmpeg.dto.StreamTrackingResult;
import com.example.ffmpeg.service.DroneStreamTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/drone/stream")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class DroneStreamController {

    private final DroneStreamTrackingService streamTrackingService;

    // 存储活跃的流会话
    private final Map<String, StreamSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * 开始视频流跟踪
     */
    @PostMapping("/start")
    public Mono<ResponseEntity<Map<String, Object>>> startStreamTracking(
            @RequestBody StreamTrackingRequest request) {

        return Mono.fromCallable(() -> {
                    log.info("🎥 启动视频流跟踪: {}", request.getStreamUrl());

                    // 验证请求参数
                    validateStreamRequest(request);

                    // 生成会话ID
                    String sessionId = generateSessionId(request.getStreamUrl());

                    // 检查是否已存在相同的流
                    if (activeSessions.containsKey(sessionId)) {
                        throw new IllegalStateException("该视频流已在处理中: " + request.getStreamUrl());
                    }

                    // 创建流会话
                    StreamSession session = new StreamSession(sessionId, request);
                    activeSessions.put(sessionId, session);

                    // 异步启动流跟踪
                    streamTrackingService.startStreamTracking(request, sessionId)
                            .doOnNext(result -> updateSessionResult(sessionId, result))
                            .doOnError(error -> handleSessionError(sessionId, error))
                            .doOnComplete(() -> completeSession(sessionId))
                            .subscribe();

                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("sessionId", sessionId);
                    response.put("message", "视频流跟踪已启动");
                    response.put("streamUrl", request.getStreamUrl());

                    return ResponseEntity.ok(response);
                })
                .onErrorResume(ex -> {
                    log.error("启动视频流跟踪失败: {}", ex.getMessage(), ex);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", ex.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(errorResponse));
                });
    }

    /**
     * 停止视频流跟踪
     */
    @PostMapping("/stop/{sessionId}")
    public Mono<ResponseEntity<Map<String, Object>>> stopStreamTracking(
            @PathVariable String sessionId) {

        return Mono.fromCallable(() -> {
                    log.info("🛑 停止视频流跟踪: {}", sessionId);

                    StreamSession session = activeSessions.get(sessionId);
                    if (session == null) {
                        throw new IllegalArgumentException("会话不存在: " + sessionId);
                    }

                    // 停止流跟踪
                    streamTrackingService.stopStreamTracking(sessionId);

                    // 移除会话
                    activeSessions.remove(sessionId);

                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("sessionId", sessionId);
                    response.put("message", "视频流跟踪已停止");

                    return ResponseEntity.ok(response);
                })
                .onErrorResume(ex -> {
                    log.error("停止视频流跟踪失败: {}", ex.getMessage(), ex);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", ex.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(errorResponse));
                });
    }

    /**
     * 获取流跟踪结果 (Server-Sent Events)
     */
    @GetMapping(value = "/results/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamTrackingResult>> getStreamResults(
            @PathVariable String sessionId) {

        log.info("📡 客户端连接流结果: {}", sessionId);

        return streamTrackingService.getStreamResults(sessionId)
                .map(result -> ServerSentEvent.<StreamTrackingResult>builder()
                        .id(String.valueOf(result.getFrameNumber()))
                        .event("detection")
                        .data(result)
                        .build())
                .doOnSubscribe(s -> log.info("📡 开始推送流结果: {}", sessionId))
                .doOnComplete(() -> log.info("📡 流结果推送完成: {}", sessionId))
                .doOnError(error -> log.error("📡 流结果推送错误: {}", error.getMessage()));
    }

    /**
     * 获取流状态
     */
    @GetMapping("/status/{sessionId}")
    public Mono<ResponseEntity<Map<String, Object>>> getStreamStatus(
            @PathVariable String sessionId) {

        return Mono.fromCallable(() -> {
            StreamSession session = activeSessions.get(sessionId);

            Map<String, Object> status = new HashMap<>();
            if (session == null) {
                status.put("exists", false);
                status.put("status", "NOT_FOUND");
            } else {
                status.put("exists", true);
                status.put("sessionId", sessionId);
                status.put("streamUrl", session.getRequest().getStreamUrl());
                status.put("status", session.getStatus());
                status.put("startTime", session.getStartTime());
                status.put("lastUpdateTime", session.getLastUpdateTime());

                if (session.getLatestResult() != null) {
                    StreamTrackingResult result = session.getLatestResult();
                    status.put("currentFrame", result.getFrameNumber());
                    status.put("currentPersonCount", result.getCurrentPersonCount());
                    status.put("totalDetections", result.getTotalDetections());
                }

                if (session.getError() != null) {
                    status.put("error", session.getError());
                }
            }

            return ResponseEntity.ok(status);
        });
    }

    /**
     * 获取所有活跃流
     */
    @GetMapping("/active")
    public Mono<ResponseEntity<Map<String, Object>>> getActiveStreams() {
        return Mono.fromCallable(() -> {
            Map<String, Object> response = new HashMap<>();
            response.put("activeCount", activeSessions.size());
            response.put("sessions", activeSessions.values().stream()
                    .map(session -> {
                        Map<String, Object> sessionInfo = new HashMap<>();
                        sessionInfo.put("sessionId", session.getSessionId());
                        sessionInfo.put("streamUrl", session.getRequest().getStreamUrl());
                        sessionInfo.put("status", session.getStatus());
                        sessionInfo.put("startTime", session.getStartTime());
                        return sessionInfo;
                    })
                    .toList());

            return ResponseEntity.ok(response);
        });
    }

    /**
     * 获取实时帧图像 (MJPEG流)
     */
    @GetMapping(value = "/frames/{sessionId}", produces = "multipart/x-mixed-replace; boundary=frame")
    public Flux<byte[]> getStreamFrames(@PathVariable String sessionId) {
        log.info("📸 客户端请求实时帧: {}", sessionId);

        return streamTrackingService.getStreamFrames(sessionId)
                .delayElements(Duration.ofMillis(100)) // 限制帧率
                .doOnSubscribe(s -> log.info("📸 开始推送实时帧: {}", sessionId))
                .doOnComplete(() -> log.info("📸 实时帧推送完成: {}", sessionId))
                .doOnError(error -> log.error("📸 实时帧推送错误: {}", error.getMessage()));
    }

    // 私有方法

    private void validateStreamRequest(StreamTrackingRequest request) {
        if (request.getStreamUrl() == null || request.getStreamUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("视频流URL不能为空");
        }

        if (request.getApiKey() == null || request.getApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException("API Key不能为空");
        }
    }

    private String generateSessionId(String streamUrl) {
        return "stream_" + Math.abs(streamUrl.hashCode()) + "_" + System.currentTimeMillis();
    }

    private void updateSessionResult(String sessionId, StreamTrackingResult result) {
        StreamSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.setLatestResult(result);
            session.setLastUpdateTime(System.currentTimeMillis());
            session.setStatus("RUNNING");
        }
    }

    private void handleSessionError(String sessionId, Throwable error) {
        StreamSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.setError(error.getMessage());
            session.setStatus("ERROR");
            session.setLastUpdateTime(System.currentTimeMillis());
        }
        log.error("流会话错误 [{}]: {}", sessionId, error.getMessage());
    }

    private void completeSession(String sessionId) {
        StreamSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.setStatus("COMPLETED");
            session.setLastUpdateTime(System.currentTimeMillis());
        }
        log.info("流会话完成: {}", sessionId);
    }

    // 内部类：流会话
    private static class StreamSession {
        private final String sessionId;
        private final StreamTrackingRequest request;
        private final long startTime;
        private volatile String status = "STARTING";
        private volatile long lastUpdateTime;
        private volatile StreamTrackingResult latestResult;
        private volatile String error;

        public StreamSession(String sessionId, StreamTrackingRequest request) {
            this.sessionId = sessionId;
            this.request = request;
            this.startTime = System.currentTimeMillis();
            this.lastUpdateTime = startTime;
        }

        // Getters and Setters
        public String getSessionId() { return sessionId; }
        public StreamTrackingRequest getRequest() { return request; }
        public long getStartTime() { return startTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
        public StreamTrackingResult getLatestResult() { return latestResult; }
        public void setLatestResult(StreamTrackingResult latestResult) { this.latestResult = latestResult; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}