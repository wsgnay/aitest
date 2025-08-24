package com.example.ffmpeg.controller;

import com.example.ffmpeg.dto.ThumbnailRequest;
import com.example.ffmpeg.service.ThumbnailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
public class ThumbnailController {

    @Autowired
    private ThumbnailService thumbnailService;

    @PostMapping("/thumbnail")
    public ResponseEntity<?> generateThumbnail(@RequestBody ThumbnailRequest request) {
        try {
            String thumbnailPath = thumbnailService.generateThumbnail(
                request.getVideoPath(),
                request.getOutputPath(),
                request.getTimestamp(),
                request.getWidth(),
                request.getHeight()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("thumbnailPath", thumbnailPath);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
} 