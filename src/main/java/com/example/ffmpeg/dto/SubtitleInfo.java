package com.example.ffmpeg.dto;

import lombok.Data;

@Data
public class SubtitleInfo {
    private int index;
    private String language;
    private String codec;
    private String title;
    private boolean isDefault;
    private boolean isForced;
} 