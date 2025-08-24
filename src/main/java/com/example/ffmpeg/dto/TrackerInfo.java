package com.example.ffmpeg.dto;

import lombok.Data;

@Data
public class TrackerInfo {
    private int id;
    private double[] bbox; // [x, y, w, h]
    private boolean active;
    private double confidence;
    private int[] color;
    private int lostFrames;
    private int createdFrame;
    private String removalReason;
}
