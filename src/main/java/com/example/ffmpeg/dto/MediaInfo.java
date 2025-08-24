package com.example.ffmpeg.dto;

import lombok.Data;
import java.util.List;

@Data
public class MediaInfo {
    private String duration;
    private String resolution;
    private String format;
    private String bitrate;
    private String audioCodec;
    private String videoCodec;
    private List<SubtitleInfo> subtitles;
    private List<AudioTrackInfo> audioTracks;
}