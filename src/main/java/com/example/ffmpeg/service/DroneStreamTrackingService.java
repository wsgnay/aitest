// src/main/java/com/example/ffmpeg/service/DroneStreamTrackingService.java
package com.example.ffmpeg.service;

import com.example.ffmpeg.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_tracking.*;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class DroneStreamTrackingService {

    private final QwenApiService qwenApiService;
    private final Java2DFrameConverter frameConverter = new Java2DFrameConverter();
    private final OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat();

    // 流会话管理
    private final Map<String, StreamTrackingSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<StreamTrackingResult>> resultSinks = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<byte[]>> frameSinks = new ConcurrentHashMap<>();

    /**
     * 启动流跟踪
     */
    public Flux<StreamTrackingResult> startStreamTracking(StreamTrackingRequest request, String sessionId) {
        return Mono.fromCallable(() -> {
                    log.info("🎥 创建流跟踪会话: {}", sessionId);

                    // 创建结果流
                    Sinks.Many<StreamTrackingResult> resultSink = Sinks.many().multicast().onBackpressureBuffer();
                    Sinks.Many<byte[]> frameSink = Sinks.many().multicast().onBackpressureBuffer();

                    resultSinks.put(sessionId, resultSink);
                    frameSinks.put(sessionId, frameSink);

                    // 创建会话
                    StreamTrackingSession session = new StreamTrackingSession(sessionId, request, resultSink, frameSink);
                    activeSessions.put(sessionId, session);

                    // 异步启动处理
                    Mono.fromRunnable(() -> processStreamAsync(session))
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe();

                    return resultSink.asFlux();
                })
                .flatMapMany(flux -> flux);
    }

    /**
     * 停止流跟踪
     */
    public void stopStreamTracking(String sessionId) {
        StreamTrackingSession session = activeSessions.get(sessionId);
        if (session != null) {
            log.info("🛑 停止流跟踪会话: {}", sessionId);
            session.stop();

            // 清理资源
            cleanupSession(sessionId);
        }
    }

    /**
     * 获取流跟踪结果
     */
    public Flux<StreamTrackingResult> getStreamResults(String sessionId) {
        Sinks.Many<StreamTrackingResult> sink = resultSinks.get(sessionId);
        if (sink == null) {
            return Flux.error(new IllegalArgumentException("会话不存在: " + sessionId));
        }
        return sink.asFlux();
    }

    /**
     * 获取实时帧
     */
    public Flux<byte[]> getStreamFrames(String sessionId) {
        Sinks.Many<byte[]> sink = frameSinks.get(sessionId);
        if (sink == null) {
            return Flux.error(new IllegalArgumentException("会话不存在: " + sessionId));
        }
        return sink.asFlux();
    }

    /**
     * 异步处理流
     */
    private void processStreamAsync(StreamTrackingSession session) {
        FFmpegFrameGrabber grabber = null;

        try {
            String sessionId = session.getSessionId();
            StreamTrackingRequest request = session.getRequest();

            log.info("🎬 开始处理视频流: {} [{}]", request.getStreamUrl(), sessionId);

            // 初始化视频源
            grabber = initializeVideoSource(request.getStreamUrl());
            grabber.start();

            // 获取视频信息
            int fps = (int) grabber.getFrameRate();
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();

            log.info("📹 流信息: {}x{}, FPS: {} [{}]", width, height, fps, sessionId);

            // 初始化跟踪数据
            List<TrackerInfo> trackers = new ArrayList<>();
            AtomicInteger trackerIdCounter = new AtomicInteger(1);
            AtomicInteger frameCounter = new AtomicInteger(0);
            AtomicInteger apiCallCounter = new AtomicInteger(0);
            AtomicLong lastDetectionTime = new AtomicLong(0);

            // 配置参数
            double confThreshold = request.getConfThreshold() != null ? request.getConfThreshold() : 0.5;
            String trackerType = request.getTrackerType() != null ? request.getTrackerType() : "MIL";
            int detectionInterval = request.getDetectionInterval() != null ? request.getDetectionInterval() : 30;
            int maxDetections = request.getMaxDetections() != null ? request.getMaxDetections() : 10;

            Frame frame;
            long lastFrameTime = System.currentTimeMillis();

            // 主处理循环
            while (session.isRunning() && (frame = grabber.grab()) != null) {
                if (frame.image == null) continue;

                int currentFrame = frameCounter.incrementAndGet();
                long currentTime = System.currentTimeMillis();

                try {
                    BufferedImage bufferedImage = frameConverter.convert(frame);
                    Mat mat = matConverter.convert(frame);

                    // 执行检测（按间隔）
                    if (currentFrame % detectionInterval == 0 && apiCallCounter.get() < maxDetections) {
                        performStreamDetection(request, bufferedImage, trackers, trackerIdCounter,
                                apiCallCounter, currentFrame, confThreshold, trackerType, mat, lastDetectionTime);
                    }

                    // 更新跟踪器
                    updateStreamTrackers(trackers, mat, currentFrame);

                    // 绘制跟踪结果
                    drawTrackingResults(bufferedImage, trackers);

                    // 发送结果
                    StreamTrackingResult result = createStreamResult(sessionId, currentFrame, trackers,
                            apiCallCounter.get(), currentTime - lastFrameTime);

                    session.getResultSink().tryEmitNext(result);

                    // 发送帧图像（JPEG）
                    byte[] frameBytes = convertToJpegBytes(bufferedImage);
                    if (frameBytes != null) {
                        session.getFrameSink().tryEmitNext(frameBytes);
                    }

                    lastFrameTime = currentTime;

                    // 限制处理速度（避免过快）
                    if (fps > 0) {
                        long targetDelay = 1000 / fps;
                        long actualDelay = System.currentTimeMillis() - currentTime;
                        if (actualDelay < targetDelay) {
                            Thread.sleep(targetDelay - actualDelay);
                        }
                    }

                } catch (Exception e) {
                    log.warn("处理帧 {} 时出错: {}", currentFrame, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("流处理异常 [{}]: {}", session.getSessionId(), e.getMessage(), e);
            session.getResultSink().tryEmitError(e);
            session.getFrameSink().tryEmitError(e);
        } finally {
            if (grabber != null) {
                try {
                    grabber.close();
                } catch (Exception e) {
                    log.warn("关闭视频源失败: {}", e.getMessage());
                }
            }

            // 完成流
            session.getResultSink().tryEmitComplete();
            session.getFrameSink().tryEmitComplete();

            log.info("🏁 流处理完成: {}", session.getSessionId());
        }
    }

    /**
     * 初始化视频源
     */
    private FFmpegFrameGrabber initializeVideoSource(String videoSource) throws Exception {
        FFmpegFrameGrabber grabber;

        if (videoSource.matches("\\d+")) {
            // 摄像头设备
            int deviceId = Integer.parseInt(videoSource);
            grabber = new FFmpegFrameGrabber(deviceId);
            log.info("📷 使用摄像头设备: {}", deviceId);
        } else if (videoSource.startsWith("rtsp://") || videoSource.startsWith("rtmp://")
                || videoSource.startsWith("http://") || videoSource.startsWith("https://")
                || videoSource.startsWith("udp://")) {
            // 网络流
            grabber = new FFmpegFrameGrabber(videoSource);

            // 网络流特殊配置
            if (videoSource.startsWith("rtsp://")) {
                grabber.setOption("rtsp_transport", "tcp");
                grabber.setOption("buffer_size", "1024000");
            }

            grabber.setOption("reconnect", "1");
            grabber.setOption("reconnect_streamed", "1");
            grabber.setOption("reconnect_delay_max", "5");

            log.info("🌐 使用网络视频流: {}", videoSource);
        } else {
            // 本地文件
            grabber = new FFmpegFrameGrabber(videoSource);
            log.info("📁 使用本地视频文件: {}", videoSource);
        }

        return grabber;
    }

    /**
     * 执行流检测
     */
    private void performStreamDetection(StreamTrackingRequest request, BufferedImage bufferedImage,
                                        List<TrackerInfo> trackers, AtomicInteger trackerIdCounter,
                                        AtomicInteger apiCallCounter, int currentFrame,
                                        double confThreshold, String trackerType, Mat mat,
                                        AtomicLong lastDetectionTime) {
        try {
            log.debug("🔍 执行流检测 - 帧 {}", currentFrame);

            List<PersonDetection> detections = qwenApiService.detectPersonsInFrame(
                    bufferedImage, request.getApiKey(), confThreshold, 30
            ).block();

            apiCallCounter.incrementAndGet();
            lastDetectionTime.set(System.currentTimeMillis());

            if (detections != null && !detections.isEmpty()) {
                log.info("🎯 检测到{}个目标 - 帧 {}", detections.size(), currentFrame);

                for (PersonDetection detection : detections) {
                    double[] bbox = detection.getBbox();
                    if (bbox != null && bbox.length >= 4) {
                        Rect2d rect2d = new Rect2d(
                                bbox[0], bbox[1],
                                bbox[2] - bbox[0], bbox[3] - bbox[1]
                        );

                        // 检查重叠
                        if (!isOverlapWithExistingTrackers(rect2d, trackers, 0.3)) {
                            int trackerId = trackerIdCounter.getAndIncrement();
                            Color color = generateTrackingColor(trackerId);
                            TrackerInfo trackerInfo = new TrackerInfo(trackerId, rect2d,
                                    color, trackerType, currentFrame);

                            if (initializeTracker(trackerInfo, mat, rect2d, trackerType)) {
                                trackers.add(trackerInfo);
                                log.info("✅ 创建新跟踪器 #{} ({}) - 帧 {}",
                                        trackerId, trackerType, currentFrame);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.warn("流检测失败 - 帧 {}: {}", currentFrame, e.getMessage());
        }
    }

    /**
     * 更新流跟踪器
     */
    private void updateStreamTrackers(List<TrackerInfo> trackers, Mat mat, int currentFrame) {
        Iterator<TrackerInfo> iterator = trackers.iterator();
        while (iterator.hasNext()) {
            TrackerInfo trackerInfo = iterator.next();
            if (!trackerInfo.active) {
                iterator.remove();
                continue;
            }

            Rect2d bbox = new Rect2d();
            boolean success = trackerInfo.tracker.update(mat, bbox);

            if (success && isValidBbox(bbox)) {
                trackerInfo.lastBbox = bbox;
                trackerInfo.confidence = Math.max(0.1, trackerInfo.confidence * 0.99);
                trackerInfo.lostFrames = 0;
                trackerInfo.lastUpdateFrame = currentFrame;
            } else {
                trackerInfo.lostFrames++;
                trackerInfo.confidence *= 0.9;

                if (trackerInfo.lostFrames > 15) {
                    trackerInfo.active = false;
                    log.debug("🗑️ 移除流跟踪器 #{} - 丢失{}帧", trackerInfo.id, trackerInfo.lostFrames);
                }
            }
        }
    }

    /**
     * 创建流结果
     */
    private StreamTrackingResult createStreamResult(String sessionId, int frameNumber,
                                                    List<TrackerInfo> trackers, int apiCalls,
                                                    long processingTime) {
        StreamTrackingResult result = new StreamTrackingResult();
        result.setSessionId(sessionId);
        result.setFrameNumber(frameNumber);
        result.setTimestamp(LocalDateTime.now());
        result.setCurrentPersonCount(getActiveTrackerCount(trackers));
        result.setTotalDetections(apiCalls);
        result.setProcessingTimeMs(processingTime);

        // 设置检测详情
        List<StreamTrackingResult.DetectionInfo> detections = new ArrayList<>();
        for (TrackerInfo tracker : trackers) {
            if (tracker.active) {
                StreamTrackingResult.DetectionInfo detection = new StreamTrackingResult.DetectionInfo();
                detection.setTrackerId(tracker.id);
                detection.setBbox(new double[]{
                        tracker.lastBbox.x(), tracker.lastBbox.y(),
                        tracker.lastBbox.x() + tracker.lastBbox.width(),
                        tracker.lastBbox.y() + tracker.lastBbox.height()
                });
                detection.setConfidence(tracker.confidence);
                detection.setTrackerType(tracker.trackerType);
                detections.add(detection);
            }
        }
        result.setDetections(detections);

        return result;
    }

    // 辅助方法

    private int getActiveTrackerCount(List<TrackerInfo> trackers) {
        return (int) trackers.stream().filter(t -> t.active).count();
    }

    private boolean isValidBbox(Rect2d bbox) {
        return bbox.width() > 10 && bbox.height() > 10
                && bbox.x() >= 0 && bbox.y() >= 0;
    }

    private boolean isOverlapWithExistingTrackers(Rect2d newBbox, List<TrackerInfo> trackers, double threshold) {
        for (TrackerInfo tracker : trackers) {
            if (!tracker.active) continue;

            double iou = calculateIoU(newBbox, tracker.lastBbox);
            if (iou > threshold) {
                return true;
            }
        }
        return false;
    }

    private double calculateIoU(Rect2d bbox1, Rect2d bbox2) {
        double x1 = Math.max(bbox1.x(), bbox2.x());
        double y1 = Math.max(bbox1.y(), bbox2.y());
        double x2 = Math.min(bbox1.x() + bbox1.width(), bbox2.x() + bbox2.width());
        double y2 = Math.min(bbox1.y() + bbox1.height(), bbox2.y() + bbox2.height());

        if (x2 <= x1 || y2 <= y1) return 0.0;

        double intersection = (x2 - x1) * (y2 - y1);
        double union = bbox1.width() * bbox1.height() + bbox2.width() * bbox2.height() - intersection;

        return union > 0 ? intersection / union : 0.0;
    }

    private boolean initializeTracker(TrackerInfo trackerInfo, Mat mat, Rect2d rect2d, String trackerType) {
        try {
            switch (trackerType.toUpperCase()) {
                case "MIL":
                    trackerInfo.tracker = TrackerMIL.create();
                    break;
                case "CSRT":
                    trackerInfo.tracker = TrackerCSRT.create();
                    break;
                case "KCF":
                    trackerInfo.tracker = TrackerKCF.create();
                    break;
                default:
                    trackerInfo.tracker = TrackerMIL.create();
            }

            return trackerInfo.tracker.init(mat, rect2d);
        } catch (Exception e) {
            log.error("初始化跟踪器失败: {}", e.getMessage());
            return false;
        }
    }

    private Color generateTrackingColor(int trackerId) {
        Color[] colors = {
                Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW,
                Color.MAGENTA, Color.CYAN, Color.ORANGE, Color.PINK
        };
        return colors[trackerId % colors.length];
    }

    private void drawTrackingResults(BufferedImage image, List<TrackerInfo> trackers) {
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (TrackerInfo tracker : trackers) {
            if (!tracker.active) continue;

            Rect2d bbox = tracker.lastBbox;
            g2d.setColor(tracker.color);
            g2d.setStroke(new BasicStroke(2.0f));

            // 绘制边界框
            g2d.drawRect((int) bbox.x(), (int) bbox.y(),
                    (int) bbox.width(), (int) bbox.height());

            // 绘制标签
            String label = String.format("ID:%d (%.2f)", tracker.id, tracker.confidence);
            FontMetrics fm = g2d.getFontMetrics();
            int labelWidth = fm.stringWidth(label);
            int labelHeight = fm.getHeight();

            // 标签背景
            g2d.fillRect((int) bbox.x(), (int) bbox.y() - labelHeight,
                    labelWidth + 4, labelHeight);

            // 标签文字
            g2d.setColor(Color.WHITE);
            g2d.drawString(label, (int) bbox.x() + 2, (int) bbox.y() - 2);
        }

        g2d.dispose();
    }

    private byte[] convertToJpegBytes(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "JPEG", baos);

            // 添加MJPEG边界
            String boundary = "--frame\r\n";
            String contentType = "Content-Type: image/jpeg\r\n";
            String contentLength = "Content-Length: " + baos.size() + "\r\n\r\n";

            ByteArrayOutputStream mjpegStream = new ByteArrayOutputStream();
            mjpegStream.write(boundary.getBytes());
            mjpegStream.write(contentType.getBytes());
            mjpegStream.write(contentLength.getBytes());
            mjpegStream.write(baos.toByteArray());
            mjpegStream.write("\r\n".getBytes());

            return mjpegStream.toByteArray();
        } catch (Exception e) {
            log.warn("转换帧为JPEG失败: {}", e.getMessage());
            return null;
        }
    }

    private void cleanupSession(String sessionId) {
        activeSessions.remove(sessionId);

        Sinks.Many<StreamTrackingResult> resultSink = resultSinks.remove(sessionId);
        if (resultSink != null) {
            resultSink.tryEmitComplete();
        }

        Sinks.Many<byte[]> frameSink = frameSinks.remove(sessionId);
        if (frameSink != null) {
            frameSink.tryEmitComplete();
        }

        log.info("🧹 清理会话资源: {}", sessionId);
    }

    // 内部类

    /**
     * 跟踪器信息类
     */
    private static class TrackerInfo {
        public Tracker tracker;
        public int id;
        public double confidence;
        public int lostFrames;
        public Rect2d lastBbox;
        public Color color;
        public boolean active;
        public long lastUpdateFrame;
        public String trackerType;
        public int createdFrame;

        public TrackerInfo(int id, Rect2d bbox, Color color, String trackerType, int createdFrame) {
            this.id = id;
            this.confidence = 1.0;
            this.lostFrames = 0;
            this.lastBbox = new Rect2d(bbox.x(), bbox.y(), bbox.width(), bbox.height());
            this.color = color;
            this.active = true;
            this.lastUpdateFrame = System.currentTimeMillis();
            this.trackerType = trackerType;
            this.createdFrame = createdFrame;
        }
    }

    /**
     * 流跟踪会话
     */
    private static class StreamTrackingSession {
        private final String sessionId;
        private final StreamTrackingRequest request;
        private final Sinks.Many<StreamTrackingResult> resultSink;
        private final Sinks.Many<byte[]> frameSink;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final long startTime = System.currentTimeMillis();

        public StreamTrackingSession(String sessionId, StreamTrackingRequest request,
                                     Sinks.Many<StreamTrackingResult> resultSink,
                                     Sinks.Many<byte[]> frameSink) {
            this.sessionId = sessionId;
            this.request = request;
            this.resultSink = resultSink;
            this.frameSink = frameSink;
        }

        public boolean isRunning() { return running.get(); }
        public void stop() { running.set(false); }

        // Getters
        public String getSessionId() { return sessionId; }
        public StreamTrackingRequest getRequest() { return request; }
        public Sinks.Many<StreamTrackingResult> getResultSink() { return resultSink; }
        public Sinks.Many<byte[]> getFrameSink() { return frameSink; }
        public long getStartTime() { return startTime; }
    }
}