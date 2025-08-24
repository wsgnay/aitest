package com.example.ffmpeg.service;

import com.example.ffmpeg.dto.MediaInfo;
import com.example.ffmpeg.dto.TranscodeRequest;
import reactor.core.publisher.Mono;

public interface MediaService {
    Mono<MediaInfo> extractMediaInfo(String filePath);
    Mono<String> transcodeMedia(TranscodeRequest request);
} 