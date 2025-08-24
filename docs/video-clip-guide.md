# 视频剪辑指南

## 剪辑类型对比

### 无损剪辑（Lossless Clip）

无损剪辑是指在剪辑视频时不重新编码（re-encode）视频内容，而是直接从原视频中提取所需片段的过程。

#### 优点
- 剪辑速度快（不需要重新编码）
- 保持原始视频质量（不会造成画质损失）
- 节省CPU资源（不需要编解码）
- 保持原始视频的所有元数据

#### 限制
- 只支持特定格式（主要是MP4、MOV、M4V等）
- 需要视频关键帧的支持
- 剪辑点必须在关键帧处

#### 实现原理
```java
public String losslessClip(String inputPath, String outputPath, double startTime, double duration) {
    // 1. 检查视频格式是否支持无损剪辑
    if (!isSupportLosslessClip(format)) {
        throw new UnsupportedOperationException("当前格式不支持无损剪辑");
    }

    // 2. 保持所有原始编码参数
    recorder.setVideoCodec(grabber.getVideoCodec());
    recorder.setAudioCodec(grabber.getAudioCodec());
    recorder.setVideoOptions(grabber.getVideoOptions());
    recorder.setAudioOptions(grabber.getAudioOptions());

    // 3. 直接复制数据流，不进行重新编码
    while ((frame = grabber.grab()) != null) {
        recorder.record(frame);
    }
}
```

### 有损剪辑（Lossy Clip）

有损剪辑会对视频进行重新编码，可以更灵活地控制输出参数，但会影响视频质量和处理速度。

#### 优点
- 支持所有视频格式
- 可以修改视频参数（编码、比特率等）
- 剪辑点更精确
- 可以添加特效和转场

#### 限制
- 处理速度较慢
- 会造成一定的质量损失
- 需要更多CPU资源
- 文件大小可能增加

#### 实现原理
```java
public String clipVideo(String inputPath, String outputPath, double startTime, double duration) {
    // 1. 可以自定义编码参数
    if (!preserveQuality) {
        recorder.setVideoCodec(videoCodec);
        recorder.setAudioCodec(audioCodec);
        recorder.setVideoBitrate(bitrate);
    }

    // 2. 逐帧处理，可以添加效果
    while ((frame = grabber.grab()) != null) {
        // 可以在这里添加视频效果
        recorder.record(frame);
    }
}
```

## 使用场景

### 适合无损剪辑的情况
- 需要快速剪辑大文件
- 对视频质量要求极高
- CPU资源有限
- 视频格式符合要求（MP4/MOV/M4V）

### 适合有损剪辑的情况
- 需要改变视频编码格式
- 需要调整视频质量参数
- 需要添加特效或转场
- 需要精确到帧的剪辑点
- 原始格式不支持无损剪辑

## API 使用示例

### 无损剪辑
```bash
curl -X POST "http://localhost:8080/api/media/clip/lossless" \
-H "Content-Type: application/json" \
-d '{
    "inputPath": "/path/to/input.mp4",
    "outputPath": "/path/to/output.mp4",
    "startTime": 10.5,
    "duration": 30.0
}'
```

### 有损剪辑
```bash
curl -X POST "http://localhost:8080/api/media/clip/cut" \
-H "Content-Type: application/json" \
-d '{
    "inputPath": "/path/to/input.mp4",
    "outputPath": "/path/to/output.mp4",
    "startTime": 10.5,
    "duration": 30.0,
    "preserveQuality": false,
    "videoCodec": "h264",
    "audioCodec": "aac"
}'
```

## 注意事项

### 无损剪辑注意事项
1. 使用前检查视频格式是否支持
2. 剪辑点会自动调整到最近的关键帧
3. 某些视频可能因为编码方式不支持无损剪辑
4. 建议在剪辑前检查视频的关键帧分布

### 有损剪辑注意事项
1. 预估处理时间和资源占用
2. 根据需求选择合适的编码参数
3. 注意输出文件的大小
4. 可能需要进行质量测试

## 性能对比

| 特性 | 无损剪辑 | 有损剪辑 |
|-----|---------|---------|
| 处理速度 | 快 | 慢 |
| 质量损失 | 无 | 有 |
| CPU占用 | 低 | 高 |
| 格式支持 | 有限 | 全面 |
| 精确度 | 关键帧级别 | 帧级别 |
| 特效支持 | 不支持 | 支持 |
| 参数调整 | 不支持 | 支持 |
| 文件大小 | 基本不变 | 可能增加 | 