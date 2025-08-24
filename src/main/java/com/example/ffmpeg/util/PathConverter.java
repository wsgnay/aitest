package com.example.ffmpeg.util;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PathConverter {

    /**
     * 将本地文件路径转换为Web可访问路径
     */
    public String convertToWebPath(String localPath) {
        if (localPath == null || localPath.isEmpty()) {
            return null;
        }

        log.debug("转换路径: {}", localPath);

        // 标准化路径分隔符
        String normalizedPath = localPath.replace("\\", "/");

        // 转换图片输出路径 outputs/xxx -> /outputs/xxx
        if (normalizedPath.contains("outputs/")) {
            String webPath = "/" + normalizedPath.substring(normalizedPath.indexOf("outputs/"));
            log.debug("图片路径转换: {} -> {}", localPath, webPath);
            return webPath;
        }

        // 转换视频输出路径 video/output/xxx -> /video/output/xxx
        if (normalizedPath.contains("video/output/")) {
            String webPath = "/" + normalizedPath.substring(normalizedPath.indexOf("video/"));
            log.debug("视频路径转换: {} -> {}", localPath, webPath);
            return webPath;
        }

        // 转换视频路径 video/xxx -> /video/xxx
        if (normalizedPath.contains("video/")) {
            String webPath = "/" + normalizedPath.substring(normalizedPath.indexOf("video/"));
            log.debug("视频路径转换: {} -> {}", localPath, webPath);
            return webPath;
        }

        // 转换上传路径 uploads/xxx -> /uploads/xxx
        if (normalizedPath.contains("uploads/")) {
            String webPath = "/" + normalizedPath.substring(normalizedPath.indexOf("uploads/"));
            log.debug("上传路径转换: {} -> {}", localPath, webPath);
            return webPath;
        }

        // 如果路径以项目根目录开始，去掉前缀
        if (normalizedPath.startsWith("./")) {
            String webPath = normalizedPath.substring(1); // 去掉 "./"
            log.debug("相对路径转换: {} -> {}", localPath, webPath);
            return webPath;
        }

        // 默认返回原路径
        log.warn("无法转换路径: {}", localPath);
        return localPath;
    }

    /**
     * 获取文件名（不含路径）
     */
    public String getFileName(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String normalizedPath = path.replace("\\", "/");
        return normalizedPath.substring(normalizedPath.lastIndexOf("/") + 1);
    }

    /**
     * 检查文件是否为图片
     */
    public boolean isImageFile(String fileName) {
        if (fileName == null) return false;
        String extension = fileName.toLowerCase();
        return extension.endsWith(".jpg") || extension.endsWith(".jpeg") ||
                extension.endsWith(".png") || extension.endsWith(".gif") ||
                extension.endsWith(".bmp") || extension.endsWith(".webp");
    }

    /**
     * 检查文件是否为视频
     */
    public boolean isVideoFile(String fileName) {
        if (fileName == null) return false;
        String extension = fileName.toLowerCase();
        return extension.endsWith(".mp4") || extension.endsWith(".avi") ||
                extension.endsWith(".mov") || extension.endsWith(".mkv") ||
                extension.endsWith(".webm") || extension.endsWith(".flv");
    }
}
