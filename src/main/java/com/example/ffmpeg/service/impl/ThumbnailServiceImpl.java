package com.example.ffmpeg.service.impl;

import com.example.ffmpeg.service.ThumbnailService;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class ThumbnailServiceImpl implements ThumbnailService {

    @Override
    public String generateThumbnail(String videoPath, String outputPath, double timestamp, int width, int height) throws Exception {
        // 验证输入文件是否存在
        if (!Files.exists(Paths.get(videoPath))) {
            throw new IllegalArgumentException("视频文件不存在: " + videoPath);
        }

        // 创建输出目录（如果不存在）
        Path outputDir = Paths.get(outputPath).getParent();
        if (outputDir != null && !Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        // 初始化视频帧抓取器
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath)) {
            grabber.start();
            
            // 设置跳转到指定时间戳
            grabber.setTimestamp((long) (timestamp * 1000000L)); // 转换为微秒
            
            // 抓取视频帧
            Frame frame = grabber.grabImage();
            if (frame == null) {
                throw new RuntimeException("无法在指定时间戳获取视频帧");
            }

            // 转换帧为BufferedImage
            Java2DFrameConverter converter = new Java2DFrameConverter();
            BufferedImage bufferedImage = converter.convert(frame);
            
            // 如果指定了尺寸且与原图不同，进行缩放
            if (width > 0 && height > 0 && 
                (bufferedImage.getWidth() != width || bufferedImage.getHeight() != height)) {
                BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                resized.getGraphics().drawImage(
                    bufferedImage.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH),
                    0, 0, null);
                bufferedImage = resized;
            }

            // 保存图片
            ImageIO.write(bufferedImage, "jpg", new File(outputPath));
            
            return outputPath;
        }
    }
} 