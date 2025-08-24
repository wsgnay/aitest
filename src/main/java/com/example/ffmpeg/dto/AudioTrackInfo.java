package com.example.ffmpeg.dto;

import lombok.Data;

@Data
public class AudioTrackInfo {
    private int index;           // 音轨索引
    private String language;     // 音轨语言
    private String codec;        // 音频编码
    private String title;        // 音轨标题
    private boolean isDefault;   // 是否默认音轨
    private int channels;        // 声道数
    private int sampleRate;      // 采样率
    private String bitrate;      // 比特率
    private String text;         // 音轨文本内容（如果有）
} 