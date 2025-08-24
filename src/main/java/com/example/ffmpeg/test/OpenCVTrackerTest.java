package com.example.ffmpeg.test;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.*;
// 使用更兼容的导入方式
import org.bytedeco.opencv.opencv_tracking.*;
import org.springframework.stereotype.Component;

/**
 * OpenCV跟踪器可用性测试 - 兼容性版本
 */
@Slf4j
@Component
public class OpenCVTrackerTest {

    /**
     * 测试OpenCV跟踪器是否可用
     */
    public void testTrackersAvailability() {
        log.info("=== OpenCV跟踪器可用性测试 ===");
        log.info("OpenCV 版本: {}", getOpenCVVersion());

        // 使用反射方式测试跟踪器，避免编译时依赖问题
        testTrackerByReflection("MIL");
        testTrackerByReflection("KCF");
        testTrackerByReflection("CSRT");

        log.info("=== 跟踪器测试完成 ===");
    }

    /**
     * 使用反射测试单个跟踪器
     */
    private void testTrackerByReflection(String trackerName) {
        try {
            log.info("测试 {} 跟踪器...", trackerName);

            // 使用反射创建跟踪器
            Object tracker = createTrackerByReflection(trackerName);

            if (tracker != null) {
                log.info("✅ {} 跟踪器创建成功", trackerName);

                // 测试基本功能
                try (Mat testImage = new Mat(480, 640, opencv_core.CV_8UC3)) {
                    Rect testBbox = new Rect(100, 100, 50, 50);

                    // 使用反射调用 init 方法
                    java.lang.reflect.Method initMethod = tracker.getClass().getMethod("init", Mat.class, Rect.class);
                    initMethod.invoke(tracker, testImage, testBbox);

                    log.info("✅ {} 跟踪器初始化成功", trackerName);

                    // 测试更新
                    Rect updateBbox = new Rect();
                    java.lang.reflect.Method updateMethod = tracker.getClass().getMethod("update", Mat.class, Rect.class);
                    Object result = updateMethod.invoke(tracker, testImage, updateBbox);

                    log.info("✅ {} 跟踪器更新成功", trackerName);
                }
            } else {
                log.error("❌ {} 跟踪器创建失败", trackerName);
            }

        } catch (Exception e) {
            log.error("❌ {} 跟踪器测试失败: {}", trackerName, e.getMessage());
        }
    }

    /**
     * 使用反射创建跟踪器
     */
    private Object createTrackerByReflection(String trackerType) {
        try {
            String className = "org.bytedeco.opencv.opencv_tracking.Tracker" + trackerType;
            Class<?> trackerClass = Class.forName(className);
            java.lang.reflect.Method createMethod = trackerClass.getMethod("create");
            return createMethod.invoke(null);
        } catch (Exception e) {
            log.debug("反射创建 {} 跟踪器失败: {}", trackerType, e.getMessage());
            return null;
        }
    }

    /**
     * 获取推荐的跟踪器类型
     */
    public String getRecommendedTracker() {
        String[] trackerPriority = {"MIL", "KCF", "CSRT"};

        for (String trackerType : trackerPriority) {
            if (isTrackerAvailable(trackerType)) {
                log.info("推荐使用跟踪器: {}", trackerType);
                return trackerType;
            }
        }

        log.warn("没有可用的跟踪器，将使用fallback实现");
        return "FALLBACK";
    }

    /**
     * 检查指定跟踪器是否可用
     */
    public boolean isTrackerAvailable(String trackerType) {
        try {
            Object tracker = createTrackerByReflection(trackerType);
            if (tracker != null) {
                // 简单测试是否能创建
                try (Mat testImage = new Mat(100, 100, opencv_core.CV_8UC3)) {
                    Rect testBbox = new Rect(10, 10, 20, 20);
                    java.lang.reflect.Method initMethod = tracker.getClass().getMethod("init", Mat.class, Rect.class);
                    initMethod.invoke(tracker, testImage, testBbox);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.debug("跟踪器 {} 不可用: {}", trackerType, e.getMessage());
            return false;
        }
    }

    /**
     * 获取OpenCV版本信息
     */
    public String getOpenCVVersion() {
        try {
            return opencv_core.CV_VERSION;
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * 获取所有可用的跟踪器列表
     */
    public java.util.List<String> getAvailableTrackers() {
        java.util.List<String> available = new java.util.ArrayList<>();
        String[] allTrackers = {"MIL", "KCF", "CSRT"};

        for (String tracker : allTrackers) {
            if (isTrackerAvailable(tracker)) {
                available.add(tracker);
            }
        }
        return available;
    }

    /**
     * 获取跟踪器性能建议
     */
    public java.util.Map<String, String> getTrackerRecommendations() {
        java.util.Map<String, String> recommendations = new java.util.HashMap<>();

        recommendations.put("MIL", "平衡性能，适合大多数场景");
        recommendations.put("KCF", "快速跟踪，适合实时应用");
        recommendations.put("CSRT", "高精度跟踪，适合精确场景");

        return recommendations;
    }

    /**
     * 执行完整的跟踪器性能测试
     */
    public java.util.Map<String, Object> performCompleteTest() {
        java.util.Map<String, Object> testResults = new java.util.HashMap<>();

        try {
            log.info("🔬 开始完整跟踪器性能测试...");

            // 基本信息
            testResults.put("opencvVersion", getOpenCVVersion());
            testResults.put("testTime", java.time.LocalDateTime.now());

            // 可用跟踪器
            java.util.List<String> availableTrackers = getAvailableTrackers();
            testResults.put("availableTrackers", availableTrackers);
            testResults.put("availableCount", availableTrackers.size());

            // 推荐跟踪器
            String recommended = getRecommendedTracker();
            testResults.put("recommendedTracker", recommended);

            // 性能建议
            testResults.put("recommendations", getTrackerRecommendations());

            // 详细测试结果
            java.util.Map<String, Boolean> detailedResults = new java.util.HashMap<>();
            String[] allTrackers = {"MIL", "KCF", "CSRT"};

            for (String tracker : allTrackers) {
                detailedResults.put(tracker, isTrackerAvailable(tracker));
            }

            testResults.put("detailedResults", detailedResults);
            testResults.put("success", true);

            log.info("✅ 完整跟踪器性能测试完成");

        } catch (Exception e) {
            log.error("❌ 跟踪器性能测试失败", e);
            testResults.put("success", false);
            testResults.put("error", e.getMessage());
        }

        return testResults;
    }

    /**
     * 创建跟踪器的工厂方法 - 用于其他类调用
     */
    public Object createTracker(String trackerType) {
        return createTrackerByReflection(trackerType);
    }
}
