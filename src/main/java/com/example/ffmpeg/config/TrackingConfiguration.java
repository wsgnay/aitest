package com.example.ffmpeg.config;

import com.example.ffmpeg.test.OpenCVTrackerTest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 跟踪器配置和初始化
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TrackingConfiguration {

    private final OpenCVTrackerTest trackerTest;

    /**
     * 启动时检测可用的跟踪器
     */
    @Bean
    public CommandLineRunner trackingSetup() {
        return args -> {
            log.info("初始化无人机目标跟踪...");

            try {
                // 测试跟踪器可用性
                trackerTest.testTrackersAvailability();

                // 获取推荐的跟踪器
                String recommendedTracker = trackerTest.getRecommendedTracker();
                log.info("系统将使用跟踪器: {}", recommendedTracker);

                // 设置系统属性，供其他组件使用
                System.setProperty("drone.tracking.recommended", recommendedTracker);

                log.info("✅ 跟踪系统初始化完成");

            } catch (Exception e) {
                log.error("❌ 跟踪系统初始化失败: {}", e.getMessage(), e);
                log.warn("🔄 将使用fallback跟踪实现");
                System.setProperty("drone.tracking.recommended", "FALLBACK");
            }
        };
    }

    /**
     * 跟踪器性能配置
     */
    @Bean
    public TrackerPerformanceConfig trackerPerformanceConfig() {
        TrackerPerformanceConfig config = new TrackerPerformanceConfig();

        // 根据可用跟踪器调整性能参数
        String recommendedTracker = System.getProperty("drone.tracking.recommended", "MIL");

        switch (recommendedTracker) {
            case "CSRT":
                // CSRT最精确但最慢
                config.setMaxLostFrames(20);
                config.setConfidenceDecay(0.98);
                config.setUpdateInterval(1);
                break;

            case "KCF":
                // KCF平衡性能和精度
                config.setMaxLostFrames(25);
                config.setConfidenceDecay(0.95);
                config.setUpdateInterval(1);
                break;

            case "MIL":
            default:
                // MIL稳定性好
                config.setMaxLostFrames(30);
                config.setConfidenceDecay(0.95);
                config.setUpdateInterval(1);
                break;
        }

        return config;
    }

    /**
     * 跟踪器性能配置类
     */
    public static class TrackerPerformanceConfig {
        private int maxLostFrames = 30;
        private double confidenceDecay = 0.95;
        private int updateInterval = 1;
        private double iouThreshold = 0.3;
        private double dedupIouThreshold = 0.05;

        // Getters and Setters
        public int getMaxLostFrames() { return maxLostFrames; }
        public void setMaxLostFrames(int maxLostFrames) { this.maxLostFrames = maxLostFrames; }

        public double getConfidenceDecay() { return confidenceDecay; }
        public void setConfidenceDecay(double confidenceDecay) { this.confidenceDecay = confidenceDecay; }

        public int getUpdateInterval() { return updateInterval; }
        public void setUpdateInterval(int updateInterval) { this.updateInterval = updateInterval; }

        public double getIouThreshold() { return iouThreshold; }
        public void setIouThreshold(double iouThreshold) { this.iouThreshold = iouThreshold; }

        public double getDedupIouThreshold() { return dedupIouThreshold; }
        public void setDedupIouThreshold(double dedupIouThreshold) { this.dedupIouThreshold = dedupIouThreshold; }
    }
}
