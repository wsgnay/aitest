package com.example.ffmpeg.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.avcodec.*;
import org.bytedeco.ffmpeg.avformat.*;
import org.bytedeco.ffmpeg.avutil.*;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.BytePointer;
import com.example.ffmpeg.dto.AudioTrackInfo;
import com.example.ffmpeg.dto.SubtitleInfo;

import java.io.File;
import java.nio.Buffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

@Slf4j
public class FFmpegUtil {

    static {
        // 设置FFmpeg日志回调
        avutil.av_log_set_level(avutil.AV_LOG_ERROR);
        FFmpegLogCallback.set();
    }

    private static class LogCallback extends Pointer {
        public void call(Pointer ptr, int level, BytePointer msg, Pointer va_list) {
            if (level <= avutil.AV_LOG_ERROR) {
                log.error("FFmpeg: {}", msg.getString().trim());
            }
        }
    }

    @Data
    public static class MediaMetadata {
        private String duration;
        private String resolution;
        private String format;
        private String bitrate;
        private String audioCodec;
        private String videoCodec;
        private List<SubtitleInfo> subtitleStreams;
        private List<AudioTrackInfo> audioTracks;
    }

    /**
     * 从视频中提取字幕信息
     * 包括：内嵌字幕流、隐藏字幕和硬字幕
     */
    private static void extractSubtitleInfo(FFmpegFrameGrabber grabber, MediaMetadata metadata) {
        List<SubtitleInfo> subtitleStreams = new ArrayList<>();
        metadata.setSubtitleStreams(subtitleStreams);
        
        AVFormatContext formatContext = grabber.getFormatContext();
        
        // 1. 检查隐藏字幕流（Closed Captions）
        for (int i = 0; i < formatContext.nb_streams(); i++) {
            AVStream stream = formatContext.streams(i);
            AVCodecParameters codecParams = stream.codecpar();
            
            // 检查是否是数据流（可能包含CEA-608/CEA-708字幕）
            if (codecParams.codec_type() == AVMEDIA_TYPE_DATA) {
                log.info("发现数据流 #{} - 可能包含隐藏字幕", i);
                SubtitleInfo subtitleInfo = new SubtitleInfo();
                subtitleInfo.setIndex(i);
                subtitleInfo.setCodec("cea-608/708");
                subtitleInfo.setTitle("Closed Captions");
                subtitleStreams.add(subtitleInfo);
            }
            
            // 检查是否是字幕流
            if (codecParams.codec_type() == AVMEDIA_TYPE_SUBTITLE) {
                log.info("发现字幕流 #{}", i);
                SubtitleInfo subtitleInfo = new SubtitleInfo();
                subtitleInfo.setIndex(i);
                
                // 获取语言标签
                AVDictionary streamMetadata = stream.metadata();
                if (streamMetadata != null) {
                    AVDictionaryEntry langEntry = av_dict_get(streamMetadata, "language", null, 0);
                    if (langEntry != null && langEntry.value() != null) {
                        subtitleInfo.setLanguage(langEntry.value().getString());
                    }
                    
                    AVDictionaryEntry titleEntry = av_dict_get(streamMetadata, "title", null, 0);
                    if (titleEntry != null && titleEntry.value() != null) {
                        subtitleInfo.setTitle(titleEntry.value().getString());
                    }
                }
                
                // 获取编解码器名称
                AVCodec codec = avcodec_find_decoder(codecParams.codec_id());
                if (codec != null) {
                    subtitleInfo.setCodec(codec.name().getString());
                }
                
                // 获取默认和强制标志
                subtitleInfo.setDefault((stream.disposition() & AV_DISPOSITION_DEFAULT) != 0);
                subtitleInfo.setForced((stream.disposition() & AV_DISPOSITION_FORCED) != 0);
                
                subtitleStreams.add(subtitleInfo);
            }
        }
        
        // 2. 如果没有找到字幕流，尝试检测是否有硬字幕
        if (subtitleStreams.isEmpty()) {
            try {
                if (hasHardSubtitles(grabber)) {
                    log.info("检测到硬字幕");
                    SubtitleInfo subtitleInfo = new SubtitleInfo();
                    subtitleInfo.setIndex(-1); // 特殊标记为硬字幕
                    subtitleInfo.setCodec("burned-in");
                    subtitleInfo.setTitle("Burned-in Subtitles");
                    subtitleInfo.setDefault(true);
                    subtitleStreams.add(subtitleInfo);
                }
            } catch (Exception e) {
                log.warn("检测硬字幕失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 检测视频是否包含硬字幕
     * 通过采样视频帧并进行文本检测来实现
     */
    private static boolean hasHardSubtitles(FFmpegFrameGrabber grabber) throws Exception {
        // 获取视频总时长（微秒）
        long duration = grabber.getLengthInTime();
        if (duration <= 0) {
            return false;
        }

        // 在视频的几个时间点进行采样
        long[] samplePoints = {
            duration / 4,            // 25%
            duration / 2,            // 50%
            duration * 3 / 4         // 75%
        };

        for (long timestamp : samplePoints) {
            grabber.setTimestamp(timestamp);
            org.bytedeco.javacv.Frame frame = grabber.grab();
            if (frame != null && frame.image != null) {
                // 这里可以添加OCR检测逻辑
                // 目前简单返回false
                log.info("在时间点 {} 采样帧进行字幕检测", timestamp / 1000000.0);
            }
        }

        return false; // 暂时返回false，等待添加OCR功能
    }

    public static MediaMetadata getMediaInfo(String filePath) {
        log.info("开始获取媒体信息: {}", filePath);
        // 检查文件是否存在
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }

        // 检查文件是否可读
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("文件无法读取: " + filePath);
        }

        MediaMetadata metadata = new MediaMetadata();
        FFmpegFrameGrabber grabber = null;
        try {
            log.info("初始化FFmpegFrameGrabber");
            grabber = new FFmpegFrameGrabber(filePath);
            grabber.setOption("analyzeduration", "2147483647");
            grabber.setOption("probesize", "2147483647");
            log.info("启动FFmpegFrameGrabber");
            grabber.start();
            
            log.info("获取基本媒体信息");
            metadata.setDuration(String.format("%.2f", grabber.getLengthInTime() / 1000000.0));
            metadata.setResolution(grabber.getImageWidth() + "x" + grabber.getImageHeight());
            metadata.setFormat(grabber.getFormat());
            metadata.setBitrate(grabber.getVideoBitrate() + "");
            metadata.setAudioCodec(grabber.getAudioCodecName());
            metadata.setVideoCodec(grabber.getVideoCodecName());

            // 初始化列表
            List<AudioTrackInfo> audioTracks = new ArrayList<>();
            metadata.setAudioTracks(audioTracks);
            
            log.info("开始提取字幕信息");
            extractSubtitleInfo(grabber, metadata);
            
            log.info("开始提取音频轨道信息");
            AVFormatContext formatContext = grabber.getFormatContext();
            for (int i = 0; i < formatContext.nb_streams(); i++) {
                AVStream stream = formatContext.streams(i);
                AVCodecParameters codecParams = stream.codecpar();
                
                log.info("处理流 #{} - 类型: {}", i, codecParams.codec_type());
                
                if (codecParams.codec_type() == AVMEDIA_TYPE_AUDIO) {
                    log.info("发现音频流 #{}", i);
                    AudioTrackInfo audioTrack = new AudioTrackInfo();
                    audioTrack.setIndex(i);
                    
                    // 获取音轨元数据
                    AVDictionary streamMetadata = stream.metadata();
                    if (streamMetadata != null) {
                        AVDictionaryEntry langEntry = av_dict_get(streamMetadata, "language", null, 0);
                        if (langEntry != null && langEntry.value() != null) {
                            audioTrack.setLanguage(langEntry.value().getString());
                        }
                        
                        AVDictionaryEntry titleEntry = av_dict_get(streamMetadata, "title", null, 0);
                        if (titleEntry != null && titleEntry.value() != null) {
                            audioTrack.setTitle(titleEntry.value().getString());
                        }
                    }
                    
                    // 获取编解码器信息
                    AVCodec codec = avcodec_find_decoder(codecParams.codec_id());
                    if (codec != null) {
                        audioTrack.setCodec(codec.name().getString());
                    }
                    
                    // 获取音频参数
                    audioTrack.setChannels(codecParams.channels());
                    audioTrack.setSampleRate(codecParams.sample_rate());
                    audioTrack.setBitrate(codecParams.bit_rate() > 0 ? 
                        String.format("%d", codecParams.bit_rate()) : "unknown");
                    
                    // 获取默认标志
                    audioTrack.setDefault((stream.disposition() & AV_DISPOSITION_DEFAULT) != 0);
                    
                    // 提取音轨文本（如果有）
                    try {
                        String audioText = extractAudioText(grabber, i);
                        if (audioText != null && !audioText.isEmpty()) {
                            audioTrack.setText(audioText);
                        }
                    } catch (Exception e) {
                        log.warn("提取音轨{}文本失败: {}", i, e.getMessage());
                    }
                    
                    audioTracks.add(audioTrack);
                }
            }
            
            log.info("媒体信息提取完成");
            return metadata;
        } catch (Exception e) {
            String errorMsg = String.format("获取媒体信息失败 [文件: %s]: %s", filePath, e.getMessage());
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg);
        } finally {
            if (grabber != null) {
                try {
                    log.info("关闭FFmpegFrameGrabber");
                    grabber.release();
                } catch (Exception e) {
                    log.warn("关闭媒体流失败", e);
                }
            }
        }
    }

    private static String extractAudioText(FFmpegFrameGrabber grabber, int streamIndex) {
        StringBuilder text = new StringBuilder();
        try {
            grabber.setAudioStream(streamIndex);
            
            // 读取音频帧并尝试提取文本
            org.bytedeco.javacv.Frame frame;
            while ((frame = grabber.grab()) != null) {
                if (frame.samples != null && frame.samples.length > 0) {
                    // 如果是语音识别或其他文本数据，可能会在samples中
                    for (java.nio.Buffer buffer : frame.samples) {
                        if (buffer != null && buffer.hasArray()) {
                            byte[] data = new byte[buffer.remaining()];
                            if (buffer instanceof java.nio.ByteBuffer) {
                                ((java.nio.ByteBuffer) buffer).get(data);
                                // 尝试将数据解析为文本
                                String decoded = new String(data, "UTF-8");
                                if (isValidText(decoded)) {
                                    text.append(decoded).append("\n");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("音轨文本提取失败: {}", e.getMessage());
        }
        return text.length() > 0 ? text.toString().trim() : null;
    }

    private static boolean isValidText(String text) {
        // 简单的文本有效性检查
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        // 检查是否包含可打印字符
        return text.chars().anyMatch(ch -> Character.isLetterOrDigit(ch) || Character.isWhitespace(ch));
    }

    public static void transcodeVideo(String inputPath, String outputPath, String videoCodec, 
                                    String audioCodec, String resolution, String bitrate) {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {
            grabber.start();
            
            // 解析分辨率
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            if (resolution != null && !resolution.isEmpty()) {
                String[] dims = resolution.split("x");
                width = Integer.parseInt(dims[0]);
                height = Integer.parseInt(dims[1]);
            }
            
            // 创建输出目录
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs();
            
            // 配置转码器
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height);
            
            // 复制音频参数
            recorder.setAudioChannels(grabber.getAudioChannels());
            recorder.setSampleRate(grabber.getSampleRate());
            
            // 设置视频编解码器
            if (videoCodec != null && !videoCodec.isEmpty()) {
                if ("h264".equalsIgnoreCase(videoCodec)) {
                    recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                } else if ("h265".equalsIgnoreCase(videoCodec)) {
                    recorder.setVideoCodec(avcodec.AV_CODEC_ID_HEVC);
                } else {
                    throw new IllegalArgumentException("不支持的视频编码格式: " + videoCodec);
                }
            } else {
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            }
            
            // 设置音频编解码器
            if (audioCodec != null && !audioCodec.isEmpty()) {
                if ("aac".equalsIgnoreCase(audioCodec)) {
                    recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                } else if ("mp3".equalsIgnoreCase(audioCodec)) {
                    recorder.setAudioCodec(avcodec.AV_CODEC_ID_MP3);
                } else {
                    throw new IllegalArgumentException("不支持的音频编码格式: " + audioCodec);
                }
            } else {
                recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            }
            
            if (bitrate != null && !bitrate.isEmpty()) {
                recorder.setVideoBitrate(parseBitrate(bitrate));
            }
            
            recorder.setFormat("mp4");
            recorder.start();
            
            // 转码过程
            while (true) {
                var frame = grabber.grab();
                if (frame == null) {
                    break;
                }
                recorder.record(frame);
            }
            
            recorder.stop();
            recorder.release();
            
        } catch (Exception e) {
            log.error("视频转码失败", e);
            throw new RuntimeException("视频转码失败: " + e.getMessage());
        }
    }
    
    private static int parseBitrate(String bitrate) {
        bitrate = bitrate.toLowerCase();
        int multiplier = 1;
        
        if (bitrate.endsWith("k")) {
            multiplier = 1000;
            bitrate = bitrate.substring(0, bitrate.length() - 1);
        } else if (bitrate.endsWith("m")) {
            multiplier = 1000000;
            bitrate = bitrate.substring(0, bitrate.length() - 1);
        }
        
        return (int) (Double.parseDouble(bitrate) * multiplier);
    }
} 