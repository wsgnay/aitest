package com.example.ffmpeg;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;

public class FFmpegUtil {
    
    public static VideoInfo getVideoInfo(String filePath) throws FrameGrabber.Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(filePath)) {
            grabber.start();
            
            VideoInfo info = new VideoInfo();
            info.setDuration(grabber.getLengthInTime() / 1000000.0); // 转换为秒
            info.setWidth(grabber.getImageWidth());
            info.setHeight(grabber.getImageHeight());
            info.setFormat(grabber.getFormat());
            info.setFrameRate(grabber.getVideoFrameRate());
            info.setVideoCodec(grabber.getVideoCodec());
            info.setAudioCodec(grabber.getAudioCodec());
            info.setBitRate(grabber.getVideoBitrate());
            
            return info;
        }
    }


    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("请提供视频文件路径");
            return;
        }
        
        String filePath = args[0];
        
        try {
            VideoInfo info = getVideoInfo(filePath);
            System.out.println("视频信息：");
            System.out.println(info);
        } catch (FrameGrabber.Exception e) {
            System.err.println("获取视频信息失败: " + e.getMessage());
        }
    }
    
    public static class VideoInfo {
        private double duration;
        private int width;
        private int height;
        private String format;
        private double frameRate;
        private int videoCodec;
        private int audioCodec;
        private int bitRate;
        
        // Getters and Setters
        public double getDuration() { return duration; }
        public void setDuration(double duration) { this.duration = duration; }
        
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
        
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        
        public double getFrameRate() { return frameRate; }
        public void setFrameRate(double frameRate) { this.frameRate = frameRate; }
        
        public int getVideoCodec() { return videoCodec; }
        public void setVideoCodec(int videoCodec) { this.videoCodec = videoCodec; }
        
        public int getAudioCodec() { return audioCodec; }
        public void setAudioCodec(int audioCodec) { this.audioCodec = audioCodec; }
        
        public int getBitRate() { return bitRate; }
        public void setBitRate(int bitRate) { this.bitRate = bitRate; }
        
        @Override
        public String toString() {
            return "VideoInfo{" +
                    "duration=" + duration +
                    ", width=" + width +
                    ", height=" + height +
                    ", format='" + format + '\'' +
                    ", frameRate=" + frameRate +
                    ", videoCodec=" + videoCodec +
                    ", audioCodec=" + audioCodec +
                    ", bitRate=" + bitRate +
                    '}';
        }
    }
} 