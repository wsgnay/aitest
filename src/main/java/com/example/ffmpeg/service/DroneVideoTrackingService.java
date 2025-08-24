package com.example.ffmpeg.service;

import com.example.ffmpeg.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.global.opencv_tracking;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_tracking.*;
import org.bytedeco.opencv.opencv_video.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
@Service
@RequiredArgsConstructor
public class DroneVideoTrackingService {

    private final QwenApiService qwenApiService;
    private final DatabaseService databaseService;
    private final Java2DFrameConverter frameConverter = new Java2DFrameConverter();
    private final OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat();

    /**
     * 跟踪器信息类
     */
    private static class TrackerInfo {
        public Tracker tracker; // OpenCV跟踪器
        public int id;
        public double confidence;
        public int lostFrames;
        public Rect2d lastBbox;
        public Color color;
        public boolean active;
        public long lastUpdateFrame;
        public String trackerType;
        public int createdFrame;

        // 运动预测相关
        public Point2d lastCenter;
        public Point2d velocity;
        public java.util.Queue<Point2d> centerHistory;

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

            // 初始化运动预测
            this.lastCenter = new Point2d(bbox.x() + bbox.width()/2, bbox.y() + bbox.height()/2);
            this.velocity = new Point2d(0, 0);
            this.centerHistory = new LinkedList<>();
            this.centerHistory.offer(new Point2d(this.lastCenter.x(), this.lastCenter.y()));
        }
    }

    /**
     * 处理无人机视频并进行人物跟踪
     */
    public Mono<TrackingResult> processVideoWithTracking(DroneVideoRequest request) {
        return Mono.fromCallable(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String outputPath = request.getOutputPath();
            if (outputPath == null) {
                outputPath = String.format("video/output/drone_tracking_%s.mp4", timestamp);
            }

            log.info("🚁 开始处理无人机视频: {}", request.getVideoSource());
            log.info("📁 输出路径: {}", outputPath);

            return processVideo(request, outputPath);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 核心视频处理方法
     */
    private TrackingResult processVideo(DroneVideoRequest request, String outputPath) throws Exception {
        // 检查输入文件
        String videoSource = request.getVideoSource();
        if (!videoSource.matches("\\d+")) {
            Path inputPath = Paths.get(videoSource);
            if (!Files.exists(inputPath)) {
                throw new IllegalArgumentException("视频文件不存在: " + videoSource);
            }
        }

        // 创建输出目录
        Path outputDir = Paths.get(outputPath).getParent();
        if (outputDir != null && !Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        FFmpegFrameGrabber grabber = null;
        FFmpegFrameRecorder recorder = null;

        // 跟踪统计信息
        TrackingResult.TrackingStats stats = new TrackingResult.TrackingStats();
        stats.setStartTime(LocalDateTime.now());

        List<TrackerInfo> trackers = new ArrayList<>();
        AtomicInteger trackerIdCounter = new AtomicInteger(1);
        AtomicInteger frameCounter = new AtomicInteger(0);
        AtomicInteger apiCallCounter = new AtomicInteger(0);
        AtomicInteger dedupCounter = new AtomicInteger(0);

        // 配置参数
        double confThreshold = request.getConfThreshold() != null ? request.getConfThreshold() : 0.5;
        String trackerType = request.getTrackerType() != null ? request.getTrackerType() : "MIL";
        boolean enableAutoDedup = request.getEnableAutoDedup() != null ? request.getEnableAutoDedup() : true;

        // 检测帧配置
        List<Integer> detectionFrames = request.getDetectionFrames() != null ?
                request.getDetectionFrames() : Arrays.asList(1, 60, 150, 300);
        int minDetectionInterval = request.getMinDetectionInterval() != null ?
                request.getMinDetectionInterval() : 90;
        int maxDetectionCalls = request.getMaxDetectionCalls() != null ?
                request.getMaxDetectionCalls() : 4;

        try {
            // 初始化视频源
            grabber = initializeVideoSource(videoSource);
            grabber.start();

            int fps = (int) grabber.getFrameRate();
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            int totalFrames = 0;

            if (!videoSource.matches("\\d+")) {
                totalFrames = grabber.getLengthInFrames();
            }

            log.info("📹 视频信息: {}x{}, FPS: {}, 总帧数: {}", width, height, fps,
                    totalFrames > 0 ? totalFrames : "未知(实时流)");

            // 初始化录制器
            recorder = initializeRecorder(outputPath, width, height, fps);
            recorder.start();

            stats.setFps(fps);
            stats.setTotalFrames(totalFrames);

            Frame frame;
            int lastDetectionFrame = -minDetectionInterval;
            int maxPersonCount = 0;

            // 主循环：处理每一帧
            while ((frame = grabber.grab()) != null) {
                if (frame.image == null) continue;

                int currentFrame = frameCounter.incrementAndGet();
                BufferedImage bufferedImage = frameConverter.convert(frame);
                Mat mat = matConverter.convert(frame);

                // 判断是否需要执行AI检测
                boolean shouldDetect = shouldPerformDetection(currentFrame, detectionFrames,
                        lastDetectionFrame, minDetectionInterval, apiCallCounter.get(), maxDetectionCalls);

                if (shouldDetect) {
                    log.info("🔍 在第{}帧执行AI检测", currentFrame);
                    performAIDetection(request, bufferedImage, trackers, trackerIdCounter,
                            apiCallCounter, currentFrame, lastDetectionFrame,
                            confThreshold, trackerType, mat);
                    lastDetectionFrame = currentFrame;
                }

                // 更新现有跟踪器
                updateTrackers(trackers, mat, currentFrame);

                // 自动去重
                if (enableAutoDedup && currentFrame % 30 == 0) {
                    int removedCount = performAutoDedup(trackers, 0.05, 0.4);
                    if (removedCount > 0) {
                        dedupCounter.addAndGet(removedCount);
                        log.debug("🧹 第{}帧自动去重，移除{}个跟踪器", currentFrame, removedCount);
                    }
                }

                // 统计最大人数
                int activeCount = getActiveTrackerCount(trackers);
                maxPersonCount = Math.max(maxPersonCount, activeCount);

                // 绘制跟踪结果
                drawTrackingResults(bufferedImage, trackers);

                // 录制帧
                Frame outputFrame = frameConverter.convert(bufferedImage);
                recorder.record(outputFrame);

                // 记录进度
                logProgress(currentFrame, totalFrames, trackers);

                // 摄像头流停止条件
                if (videoSource.matches("\\d+") && currentFrame > 3000) {
                    log.info("⏹️ 达到最大处理帧数，停止摄像头流处理");
                    break;
                }
            }

            // 完成处理
            stats.setEndTime(LocalDateTime.now());
            stats.setTotalFrames(frameCounter.get());
            stats.setActiveTrackers(getActiveTrackerCount(trackers));
            stats.setApiCalls(apiCallCounter.get());
            stats.setDedupCount(dedupCounter.get());
            stats.setMaxPersonCount(maxPersonCount);

            log.info("✅ 视频处理完成: 处理{}帧, API调用{}次, 去重{}次, 最大人数{}",
                    frameCounter.get(), apiCallCounter.get(), dedupCounter.get(), maxPersonCount);

            // 保存到数据库
            saveVideoDetectionToDatabase(request, outputPath, stats);

            return buildTrackingResult(outputPath, stats);

        } finally {
            closeResources(grabber, recorder);
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
            String osName = System.getProperty("os.name").toLowerCase();
            String videoSourcePath;

            if (osName.contains("windows")) {
                videoSourcePath = "video=" + deviceId;
                grabber = new FFmpegFrameGrabber(videoSourcePath);
                grabber.setFormat("dshow");
            } else if (osName.contains("mac")) {
                videoSourcePath = "" + deviceId;
                grabber = new FFmpegFrameGrabber(videoSourcePath);
                grabber.setFormat("avfoundation");
            } else {
                videoSourcePath = "/dev/video" + deviceId;
                grabber = new FFmpegFrameGrabber(videoSourcePath);
                grabber.setFormat("v4l2");
            }

            // 设置摄像头参数
            grabber.setImageWidth(1280);
            grabber.setImageHeight(720);
            grabber.setFrameRate(30);

            log.info("📷 初始化摄像头设备: {}, 格式: {}", videoSourcePath, grabber.getFormat());
        } else {
            // 视频文件
            grabber = new FFmpegFrameGrabber(videoSource);
            log.info("📁 初始化视频文件: {}", videoSource);
        }

        return grabber;
    }

    /**
     * 初始化录制器
     */
    private FFmpegFrameRecorder initializeRecorder(String outputPath, int width, int height, int fps) {
        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height);
        recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
        recorder.setFrameRate(fps);
        recorder.setVideoBitrate(2000000); // 2Mbps
        return recorder;
    }

    /**
     * 执行AI检测
     */
    private void performAIDetection(DroneVideoRequest request, BufferedImage bufferedImage,
                                    List<TrackerInfo> trackers, AtomicInteger trackerIdCounter,
                                    AtomicInteger apiCallCounter, int currentFrame,
                                    int lastDetectionFrame, double confThreshold,
                                    String trackerType, Mat mat) {
        try {
            // 调用Qwen API进行检测
            List<PersonDetection> detections = qwenApiService.detectPersonsInFrame(
                    bufferedImage, request.getApiKey(), confThreshold, 30
            ).block();

            apiCallCounter.incrementAndGet();

            if (detections != null && !detections.isEmpty()) {
                log.info("🎯 检测到{}个目标", detections.size());

                // 为每个检测结果创建跟踪器
                for (PersonDetection detection : detections) {
                    double[] bbox = detection.getBbox();
                    if (bbox != null && bbox.length >= 4) {
                        Rect2d rect2d = new Rect2d(
                                bbox[0], bbox[1],
                                bbox[2] - bbox[0], bbox[3] - bbox[1]
                        );

                        // 检查是否与现有跟踪器重叠
                        if (!isOverlapWithExistingTrackers(rect2d, trackers, 0.3)) {
                            int trackerId = trackerIdCounter.getAndIncrement();
                            Color color = generateTrackingColor(trackerId);
                            TrackerInfo trackerInfo = new TrackerInfo(trackerId, rect2d, color,
                                    trackerType, currentFrame);

                            if (initializeTracker(trackerInfo, mat, rect2d, trackerType)) {
                                trackers.add(trackerInfo);
                                log.info("✨ 创建新跟踪器 #{} ({}), 位置: ({:.0f},{:.0f},{:.0f},{:.0f})",
                                        trackerId, trackerType, rect2d.x(), rect2d.y(),
                                        rect2d.width(), rect2d.height());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ AI检测失败: {}", e.getMessage());
        }
    }

    /**
     * 初始化跟踪器
     */
    private boolean initializeTracker(TrackerInfo trackerInfo, Mat frame, Rect2d bbox, String trackerType) {
        try {
            log.debug("🔧 创建OpenCV {}跟踪器", trackerType);

            Tracker tracker = createTracker(trackerType);

            if (tracker != null) {
                // 将Rect2d转换为Rect以匹配Tracker接口
                Rect rect = new Rect((int)bbox.x(), (int)bbox.y(), (int)bbox.width(), (int)bbox.height());
                tracker.init(frame, rect); // init方法返回void，不是boolean
                trackerInfo.tracker = tracker;
                log.debug("✅ OpenCV跟踪器 #{} ({}) 初始化成功", trackerInfo.id, trackerType);
                return true;
            } else {
                log.error("❌ 无法创建OpenCV跟踪器: {}", trackerType);
                return false;
            }

        } catch (Exception e) {
            log.error("❌ 创建OpenCV跟踪器失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 创建指定类型的跟踪器
     */
    private Tracker createTracker(String trackerType) {
        try {
            switch (trackerType.toUpperCase()) {
                case "MIL":
                    return TrackerMIL.create();
                case "KCF":
                    return TrackerKCF.create();
                case "CSRT":
                    return TrackerCSRT.create();
//                case "BOOSTING":
//                    return TrackerBoosting.create();
//                case "TLD":
//                    return TrackerTLD.create();
//                case "MEDIANFLOW":
//                    return TrackerMedianFlow.create();
//                case "MOSSE":
//                    return TrackerMOSSE.create();
                default:
                    log.warn("⚠️ 不支持的跟踪器类型: {}, 使用默认MIL", trackerType);
                    return TrackerMIL.create();
            }
        } catch (Exception e) {
            log.warn("⚠️ 创建跟踪器{}失败: {}", trackerType, e.getMessage());
            return null;
        }
    }
    /**
     * 更新所有跟踪器
     */
    private void updateTrackers(List<TrackerInfo> trackers, Mat frame, int currentFrame) {
        Iterator<TrackerInfo> iterator = trackers.iterator();

        while (iterator.hasNext()) {
            TrackerInfo trackerInfo = iterator.next();
            if (!trackerInfo.active || trackerInfo.tracker == null) continue;

            try {
                Rect bbox = new Rect(); // 使用Rect而不是Rect2d
                trackerInfo.tracker.update(frame, bbox); // update方法返回void，不是boolean

                if (isValidBbox(new Rect2d(bbox.x(), bbox.y(), bbox.width(), bbox.height()), frame.cols(), frame.rows())) {
                    // 跟踪成功
                    updateTrackerSuccess(trackerInfo, new Rect2d(bbox.x(), bbox.y(), bbox.width(), bbox.height()), currentFrame);
                } else {
                    // 跟踪失败
                    updateTrackerFailure(trackerInfo);
                }

            } catch (Exception e) {
                log.warn("⚠️ 跟踪器 #{} ({}) 更新异常: {}",
                        trackerInfo.id, trackerInfo.trackerType, e.getMessage());
                updateTrackerFailure(trackerInfo);
            }
        }
    }

    /**
     * 跟踪成功时更新跟踪器状态
     */
    private void updateTrackerSuccess(TrackerInfo trackerInfo, Rect2d bbox, int currentFrame) {
        // 更新位置信息
        Point2d newCenter = new Point2d(bbox.x() + bbox.width() / 2, bbox.y() + bbox.height() / 2);
        updateMotionInfo(trackerInfo, newCenter);

        trackerInfo.lastBbox = new Rect2d(bbox.x(), bbox.y(), bbox.width(), bbox.height());
        trackerInfo.lostFrames = 0;
        trackerInfo.lastUpdateFrame = currentFrame;
        trackerInfo.confidence = Math.max(0.1, trackerInfo.confidence * 0.995);

        log.debug("跟踪器 #{} ({}) 更新成功: ({:.0f},{:.0f},{:.0f},{:.0f})",
                trackerInfo.id, trackerInfo.trackerType,
                bbox.x(), bbox.y(), bbox.width(), bbox.height());
    }

    /**
     * 跟踪失败时更新跟踪器状态
     */
    private void updateTrackerFailure(TrackerInfo trackerInfo) {
        trackerInfo.lostFrames++;
        trackerInfo.confidence *= 0.9;

        log.debug("❌ 跟踪器 #{} ({}) 跟踪失败, 丢失帧数: {}",
                trackerInfo.id, trackerInfo.trackerType, trackerInfo.lostFrames);

        if (trackerInfo.lostFrames > 30) {
            trackerInfo.active = false;
            log.info("🗑️ 移除跟踪器 #{} ({}) (丢失{}帧)",
                    trackerInfo.id, trackerInfo.trackerType, trackerInfo.lostFrames);
        }
    }

    /**
     * 更新运动信息
     */
    private void updateMotionInfo(TrackerInfo trackerInfo, Point2d newCenter) {
        // 计算速度
        double vx = newCenter.x() - trackerInfo.lastCenter.x();
        double vy = newCenter.y() - trackerInfo.lastCenter.y();

        // 平滑速度估计
        double alpha = 0.7;
        trackerInfo.velocity.x(alpha * trackerInfo.velocity.x() + (1 - alpha) * vx);
        trackerInfo.velocity.y(alpha * trackerInfo.velocity.y() + (1 - alpha) * vy);

        // 更新中心点
        trackerInfo.lastCenter = newCenter;

        // 更新位置历史
        trackerInfo.centerHistory.offer(new Point2d(newCenter.x(), newCenter.y()));
        if (trackerInfo.centerHistory.size() > 5) {
            trackerInfo.centerHistory.poll();
        }
    }

    /**
     * 判断是否需要执行检测
     */
    private boolean shouldPerformDetection(int currentFrame, List<Integer> detectionFrames,
                                           int lastDetectionFrame, int minInterval,
                                           int apiCallCount, int maxCalls) {
        if (apiCallCount >= maxCalls) {
            return false;
        }

        if (detectionFrames.contains(currentFrame)) {
            return true;
        }

        return currentFrame - lastDetectionFrame >= minInterval;
    }

    /**
     * 检查是否与现有跟踪器重叠
     */
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

    /**
     * 计算IoU（交并比）
     */
    private double calculateIoU(Rect2d bbox1, Rect2d bbox2) {
        double x1 = Math.max(bbox1.x(), bbox2.x());
        double y1 = Math.max(bbox1.y(), bbox2.y());
        double x2 = Math.min(bbox1.x() + bbox1.width(), bbox2.x() + bbox2.width());
        double y2 = Math.min(bbox1.y() + bbox1.height(), bbox2.y() + bbox2.height());

        if (x2 <= x1 || y2 <= y1) return 0.0;

        double intersectionArea = (x2 - x1) * (y2 - y1);
        double bbox1Area = bbox1.width() * bbox1.height();
        double bbox2Area = bbox2.width() * bbox2.height();
        double unionArea = bbox1Area + bbox2Area - intersectionArea;

        return intersectionArea / unionArea;
    }

    /**
     * 验证边界框有效性
     */
    private boolean isValidBbox(Rect2d bbox, int imageWidth, int imageHeight) {
        return bbox.width() > 5 && bbox.height() > 5 &&
                bbox.x() >= 0 && bbox.y() >= 0 &&
                bbox.x() + bbox.width() <= imageWidth &&
                bbox.y() + bbox.height() <= imageHeight;
    }

    /**
     * 执行自动去重
     */
    private int performAutoDedup(List<TrackerInfo> trackers, double iouThreshold, double overlapThreshold) {
        int removedCount = 0;
        List<TrackerInfo> activeTrackers = trackers.stream()
                .filter(t -> t.active)
                .sorted((a, b) -> Double.compare(b.confidence, a.confidence))
                .toList();

        for (int i = 0; i < activeTrackers.size(); i++) {
            TrackerInfo tracker1 = activeTrackers.get(i);
            if (!tracker1.active) continue;

            for (int j = i + 1; j < activeTrackers.size(); j++) {
                TrackerInfo tracker2 = activeTrackers.get(j);
                if (!tracker2.active) continue;

                double iou = calculateIoU(tracker1.lastBbox, tracker2.lastBbox);
                if (iou > iouThreshold) {
                    tracker2.active = false;
                    removedCount++;
                    log.debug("🗑️ 去重移除跟踪器 #{} ({}) (IoU={:.3f})",
                            tracker2.id, tracker2.trackerType, iou);
                }
            }
        }

        return removedCount;
    }

    /**
     * 绘制跟踪结果
     */
    private void drawTrackingResults(BufferedImage image, List<TrackerInfo> trackers) {
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setStroke(new BasicStroke(2.0f));

        for (TrackerInfo tracker : trackers) {
            if (!tracker.active) continue;

            Rect2d bbox = tracker.lastBbox;

            // 根据丢失帧数调整颜色透明度
            float alpha = tracker.lostFrames > 0 ?
                    Math.max(0.3f, 1.0f - (float)tracker.lostFrames / 30.0f) : 1.0f;

            Color color = new Color(
                    tracker.color.getRed(),
                    tracker.color.getGreen(),
                    tracker.color.getBlue(),
                    (int)(255 * alpha)
            );
            g2d.setColor(color);

            // 绘制边界框
            g2d.drawRect((int) bbox.x(), (int) bbox.y(),
                    (int) bbox.width(), (int) bbox.height());

            // 绘制标签
            String label = String.format("#%d (%s) %.2f", tracker.id, tracker.trackerType, tracker.confidence);
            if (tracker.lostFrames > 0) {
                label += " [LOST:" + tracker.lostFrames + "]";
            }

            FontMetrics fm = g2d.getFontMetrics();
            int labelWidth = fm.stringWidth(label);
            int labelHeight = fm.getHeight();

            // 绘制标签背景
            g2d.fillRect((int) bbox.x(), (int) bbox.y() - labelHeight,
                    labelWidth + 4, labelHeight);

            // 绘制标签文字
            g2d.setColor(Color.WHITE);
            g2d.drawString(label, (int) bbox.x() + 2, (int) bbox.y() - 2);

            // 绘制运动轨迹
            drawTrajectory(g2d, tracker);
        }

        g2d.dispose();
    }

    /**
     * 绘制运动轨迹
     */
    private void drawTrajectory(Graphics2D g2d, TrackerInfo tracker) {
        if (tracker.centerHistory.size() > 1) {
            g2d.setColor(new Color(tracker.color.getRed(), tracker.color.getGreen(),
                    tracker.color.getBlue(), 100));
            g2d.setStroke(new BasicStroke(1.0f));

            Point2d[] points = tracker.centerHistory.toArray(new Point2d[0]);
            for (int i = 1; i < points.length; i++) {
                g2d.drawLine((int)points[i-1].x(), (int)points[i-1].y(),
                        (int)points[i].x(), (int)points[i].y());
            }
            g2d.setStroke(new BasicStroke(2.0f)); // 恢复线条粗细
        }
    }

    /**
     * 记录处理进度
     */
    private void logProgress(int currentFrame, int totalFrames, List<TrackerInfo> trackers) {
        if (currentFrame % 100 == 0) {
            if (totalFrames > 0) {
                double progress = (double) currentFrame / totalFrames * 100;
                log.info("📊 处理进度: {:.1f}% ({}/{}), 活跃跟踪器: {}",
                        progress, currentFrame, totalFrames, getActiveTrackerCount(trackers));
            } else {
                log.info("📊 已处理帧数: {}, 活跃跟踪器: {}",
                        currentFrame, getActiveTrackerCount(trackers));
            }
        }
    }

    /**
     * 获取活跃跟踪器数量
     */
    private int getActiveTrackerCount(List<TrackerInfo> trackers) {
        return (int) trackers.stream().filter(t -> t.active).count();
    }

    /**
     * 生成跟踪器颜色
     */
    private Color generateTrackingColor(int trackerId) {
        Color[] colors = {
                Color.GREEN, Color.BLUE, Color.RED, Color.CYAN,
                Color.MAGENTA, Color.YELLOW, Color.ORANGE, Color.PINK,
                new Color(128, 0, 128), new Color(255, 165, 0), // Purple, Orange
                new Color(0, 128, 128), new Color(128, 128, 0)  // Teal, Olive
        };
        return colors[trackerId % colors.length];
    }

    /**
     * 构建跟踪结果
     */
    private TrackingResult buildTrackingResult(String outputPath, TrackingResult.TrackingStats stats) {
        return TrackingResult.builder()
                .success(true)
                .outputVideoPath("/" + outputPath)
                .outputPath(outputPath)
                .totalFrames(stats.getTotalFrames())
                .maxPersonCount(stats.getMaxPersonCount())
                .apiCallCount(stats.getApiCalls())
                .dedupOperations(stats.getDedupCount())
                .processingTimeMs(stats.getProcessingTimeMs())
                .stats(stats)
                .build();
    }

    /**
     * 保存视频检测结果到数据库
     */
    private void saveVideoDetectionToDatabase(DroneVideoRequest request, String outputPath,
                                              TrackingResult.TrackingStats stats) {
        try {
            TrackingResult trackingResult = buildTrackingResult(outputPath, stats);
            trackingResult.setVideoPath(request.getVideoSource());
            trackingResult.setStartTime(stats.getStartTime());
            trackingResult.setEndTime(stats.getEndTime());

            Map<String, Object> config = new HashMap<>();
            config.put("confThreshold", request.getConfThreshold());
            config.put("trackerType", request.getTrackerType());
            config.put("enableAutoDedup", request.getEnableAutoDedup());
            config.put("model", request.getModelName());
            config.put("maxDetectionCalls", request.getMaxDetectionCalls());
            config.put("minDetectionInterval", request.getMinDetectionInterval());

            String videoName;
            if (request.getVideoSource().matches("\\d+")) {
                videoName = "camera_" + request.getVideoSource();
            } else {
                videoName = java.nio.file.Paths.get(request.getVideoSource()).getFileName().toString();
            }

            databaseService.saveVideoDetection(
                    request.getVideoSource(),
                    videoName,
                    trackingResult,
                    config
            ).subscribe(
                    result -> log.info("💾 视频检测结果已保存到数据库"),
                    error -> log.error("❌ 保存视频检测结果失败", error)
            );
        } catch (Exception e) {
            log.error("❌ 保存视频检测结果到数据库失败", e);
        }
    }

    /**
     * 关闭资源
     */
    private void closeResources(FFmpegFrameGrabber grabber, FFmpegFrameRecorder recorder) {
        if (grabber != null) {
            try {
                grabber.stop();
                grabber.close();
            } catch (Exception e) {
                log.warn("⚠️ 关闭grabber失败", e);
            }
        }
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.close();
            } catch (Exception e) {
                log.warn("⚠️ 关闭recorder失败", e);
            }
        }
    }

    /**
     * 获取跟踪器性能信息
     */
    public Mono<Map<String, Object>> getTrackerPerformanceInfo() {
        return Mono.fromCallable(() -> {
            Map<String, Object> info = new HashMap<>();

            try {
                // 测试各种跟踪器的可用性
                Map<String, Boolean> trackerAvailability = new HashMap<>();
                String[] trackerTypes = {"MIL", "KCF", "CSRT", "BOOSTING", "TLD", "MEDIANFLOW", "MOSSE"};

                for (String type : trackerTypes) {
                    try {
                        Tracker tracker = createTracker(type);
                        trackerAvailability.put(type, tracker != null);
                        if (tracker != null) {
                            // 清理测试跟踪器
                            tracker.close();
                        }
                    } catch (Exception e) {
                        trackerAvailability.put(type, false);
                    }
                }

                info.put("trackerAvailability", trackerAvailability);
                info.put("recommendedTracker", getRecommendedTracker(trackerAvailability));
                info.put("opencvVersion", opencv_core.CV_VERSION);
                info.put("javacvVersion", "unknown"); // 移除对Loader的引用

            } catch (Exception e) {
                log.error("获取跟踪器性能信息失败", e);
                info.put("error", e.getMessage());
            }

            return info;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取推荐的跟踪器类型
     */
    private String getRecommendedTracker(Map<String, Boolean> availability) {
        // 按优先级排序：稳定性 > 速度 > 精度
        String[] priorityOrder = {"MIL", "KCF", "CSRT", "BOOSTING", "MEDIANFLOW", "TLD", "MOSSE"};

        for (String tracker : priorityOrder) {
            if (availability.getOrDefault(tracker, false)) {
                return tracker;
            }
        }

        return "TEMPLATE"; // 如果都不可用，使用模板匹配
    }

    /**
     * 批量处理视频文件
     */
    public Mono<Map<String, Object>> batchProcessVideos(List<DroneVideoRequest> requests) {
        return Mono.fromCallable(() -> {
            Map<String, Object> batchResult = new HashMap<>();
            List<TrackingResult> results = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            log.info("🚀 开始批量处理{}个视频", requests.size());

            for (int i = 0; i < requests.size(); i++) {
                DroneVideoRequest request = requests.get(i);
                try {
                    log.info("📹 处理第{}/{}个视频: {}", i + 1, requests.size(), request.getVideoSource());

                    TrackingResult result = processVideoWithTracking(request).block();
                    results.add(result);

                    log.info("✅ 第{}/{}个视频处理完成", i + 1, requests.size());

                } catch (Exception e) {
                    String error = String.format("处理视频 %s 失败: %s", request.getVideoSource(), e.getMessage());
                    errors.add(error);
                    log.error("❌ {}", error, e);
                }
            }

            batchResult.put("totalVideos", requests.size());
            batchResult.put("successCount", results.size());
            batchResult.put("errorCount", errors.size());
            batchResult.put("results", results);
            batchResult.put("errors", errors);

            log.info("🎯 批量处理完成: 成功{}, 失败{}", results.size(), errors.size());

            return batchResult;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取系统资源使用情况
     */
    public Mono<Map<String, Object>> getSystemResourceUsage() {
        return Mono.fromCallable(() -> {
            Map<String, Object> usage = new HashMap<>();

            try {
                Runtime runtime = Runtime.getRuntime();
                long maxMemory = runtime.maxMemory();
                long totalMemory = runtime.totalMemory();
                long freeMemory = runtime.freeMemory();
                long usedMemory = totalMemory - freeMemory;

                usage.put("maxMemoryMB", maxMemory / 1024 / 1024);
                usage.put("totalMemoryMB", totalMemory / 1024 / 1024);
                usage.put("usedMemoryMB", usedMemory / 1024 / 1024);
                usage.put("freeMemoryMB", freeMemory / 1024 / 1024);
                usage.put("memoryUsagePercent", (double) usedMemory / maxMemory * 100);

                usage.put("availableProcessors", runtime.availableProcessors());
                usage.put("timestamp", LocalDateTime.now());

            } catch (Exception e) {
                log.error("获取系统资源使用情况失败", e);
                usage.put("error", e.getMessage());
            }

            return usage;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 清理临时文件和资源
     */
    public Mono<Map<String, Object>> cleanupResources() {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();
            int deletedFiles = 0;
            long freedSpace = 0L;

            try {
                // 清理临时输出文件（7天前的文件）
                Path outputDir = Paths.get("video/output");
                if (Files.exists(outputDir)) {
                    long cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);

                    Files.walk(outputDir)
                            .filter(Files::isRegularFile)
                            .filter(path -> {
                                try {
                                    return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                                } catch (Exception e) {
                                    return false;
                                }
                            })
                            .forEach(path -> {
                                try {
                                    long size = Files.size(path);
                                    Files.delete(path);
                                    log.debug("🗑️ 删除过期文件: {}", path);
                                } catch (Exception e) {
                                    log.warn("删除文件失败: {}", path, e);
                                }
                            });
                }

                // 强制垃圾回收
                System.gc();

                result.put("success", true);
                result.put("deletedFiles", deletedFiles);
                result.put("freedSpaceMB", freedSpace / 1024 / 1024);
                result.put("cleanupTime", LocalDateTime.now());

            } catch (Exception e) {
                log.error("清理资源失败", e);
                result.put("success", false);
                result.put("error", e.getMessage());
            }

            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 验证跟踪器配置
     */
    public boolean validateTrackerConfig(String trackerType) {
        try {
            Tracker tracker = createTracker(trackerType);
            if (tracker != null) {
                tracker.close();
                return true;
            }
            return false;
        } catch (Exception e) {
            log.debug("跟踪器 {} 验证失败: {}", trackerType, e.getMessage());
            return false;
        }
    }

    /**
     * 获取支持的跟踪器类型列表
     */
    public List<String> getSupportedTrackerTypes() {
        List<String> supported = new ArrayList<>();
        String[] allTypes = {"MIL", "KCF", "CSRT", "BOOSTING", "TLD", "MEDIANFLOW", "MOSSE"};

        for (String type : allTypes) {
            if (validateTrackerConfig(type)) {
                supported.add(type);
            }
        }

        return supported;
    }
}
