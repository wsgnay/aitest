package com.example.ffmpeg.controller;

import com.example.ffmpeg.dto.MediaInfo;
import com.example.ffmpeg.dto.TranscodeRequest;
import com.example.ffmpeg.service.MediaService;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Validated
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @GetMapping("/info")
    public Mono<MediaInfo> getMediaInfo(@RequestParam @NotBlank String filePath) {
        return mediaService.extractMediaInfo(filePath);
    }

    @PostMapping("/transcode")
    public Mono<String> transcodeMedia(@RequestBody @Valid TranscodeRequest request) {
        return mediaService.transcodeMedia(request);
    }
}
