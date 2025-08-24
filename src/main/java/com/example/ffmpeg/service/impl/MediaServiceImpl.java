package com.example.ffmpeg.service.impl;

import com.example.ffmpeg.dto.MediaInfo;
import com.example.ffmpeg.dto.SubtitleInfo;
import com.example.ffmpeg.dto.TranscodeRequest;
import com.example.ffmpeg.service.MediaService;
import com.example.ffmpeg.util.FFmpegUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MediaServiceImpl implements MediaService {

    @Override
    public Mono<MediaInfo> extractMediaInfo(String filePath) {
        return Mono.fromCallable(() -> {
            MediaInfo mediaInfo = new MediaInfo();
            FFmpegUtil.MediaMetadata metadata = FFmpegUtil.getMediaInfo(filePath);
            
            mediaInfo.setDuration(metadata.getDuration());
            mediaInfo.setResolution(metadata.getResolution());
            mediaInfo.setFormat(metadata.getFormat());
            mediaInfo.setBitrate(metadata.getBitrate());
            mediaInfo.setAudioCodec(metadata.getAudioCodec());
            mediaInfo.setVideoCodec(metadata.getVideoCodec());
            
            // 设置字幕信息
            if (metadata.getSubtitleStreams() != null) {
                mediaInfo.setSubtitles(metadata.getSubtitleStreams());
            } else {
                mediaInfo.setSubtitles(new ArrayList<>());
            }

            // 设置音轨信息
            if (metadata.getAudioTracks() != null) {
                mediaInfo.setAudioTracks(metadata.getAudioTracks());
            } else {
                mediaInfo.setAudioTracks(new ArrayList<>());
            }
            
            return mediaInfo;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<String> transcodeMedia(TranscodeRequest request) {
        return Mono.fromCallable(() -> {
            FFmpegUtil.transcodeVideo(
                request.getInputPath(),
                request.getOutputPath(),
                request.getVideoCodec(),
                request.getAudioCodec(),
                request.getResolution(),
                request.getBitrate()
            );
            return "转码完成";
        }).subscribeOn(Schedulers.boundedElastic());
    }
} 