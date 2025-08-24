# FFmpeg视频处理服务API压测工具

这是一个用于压测FFmpeg视频处理服务API的Python脚本，特别是针对媒体信息获取接口(`/api/media/info`)的性能测试工具。该工具支持多线程并发请求，可配置并发用户数、请求总数和测试持续时间等参数，并生成详细的性能报告。

## 功能特性

- 支持多线程并发请求
- 可配置并发用户数、请求总数和测试持续时间
- 支持用户逐步启动（ramp-up）
- 自动重试失败请求
- 生成详细的性能报告（控制台输出、JSON、Markdown）
- 生成响应时间分布图和HTTP状态码分布图
- 计算关键性能指标（RPS、响应时间百分位数等）

## 环境要求

- Python 3.6+
- 依赖库：
  - requests
  - matplotlib
  - numpy

## 安装依赖

```bash
pip install requests matplotlib numpy
```

## 使用方法

### 基本用法

```bash
python ffmpeg_api_benchmark.py --url http://example.com --file /path/to/video.mp4 --users 10 --duration 60
```

### 参数说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--url` | API基础URL，例如: http://example.com | 必填 |
| `--file` | 视频文件路径 | 必填 |
| `--users` | 并发用户数 | 10 |
| `--requests` | 总请求数 | 无 |
| `--duration` | 测试持续时间(秒) | 无 |
| `--ramp-up` | 用户启动时间(秒) | 0 |
| `--timeout` | 请求超时时间(秒) | 10 |
| `--output` | 结果输出目录 | results |

**注意**: 必须指定 `--requests` 或 `--duration` 参数之一。

### 示例

1. 运行10个并发用户，持续60秒：

```bash
python ffmpeg_api_benchmark.py --url http://ffmpeg-zppoiczbch.cn-hangzhou.fcapp.run --file /home/data/17.mp4 --users 10 --duration 60
```

2. 运行50个并发用户，每个用户发送20个请求（总共1000个请求）：

```bash
python ffmpeg_api_benchmark.py --url http://ffmpeg-zppoiczbch.cn-hangzhou.fcapp.run --file /home/data/17.mp4 --users 50 --requests 1000
```

3. 运行100个并发用户，在30秒内逐步启动，持续测试5分钟：

```bash
python ffmpeg_api_benchmark.py --url http://ffmpeg-zppoiczbch.cn-hangzhou.fcapp.run --file /home/data/17.mp4 --users 100 --ramp-up 30 --duration 300
```

## 输出结果

脚本运行完成后，会在控制台输出测试结果摘要，并在指定的输出目录（默认为`results`）生成以下文件：

- `report_YYYYMMDD_HHMMSS.json`: JSON格式的详细测试报告
- `report_YYYYMMDD_HHMMSS.md`: Markdown格式的测试报告
- `response_time_histogram.png`: 响应时间分布图
- `status_code_pie.png`: HTTP状态码分布图

## 测试报告示例

测试报告包含以下内容：

1. 测试信息：
   - 测试URL
   - 视频文件路径
   - 并发用户数
   - 测试开始/结束时间
   - 测试持续时间
   - 用户启动时间
   - 请求超时时间

2. 测试结果摘要：
   - 总请求数
   - 成功请求数
   - 失败请求数
   - 请求成功率
   - 平均每秒请求数 (RPS)

3. 响应时间统计：
   - 最小响应时间
   - 平均响应时间
   - 最大响应时间
   - 中位数 (P50)
   - P90/P95/P99 响应时间

4. HTTP状态码分布

5. 错误分布

6. 图表：
   - 响应时间分布图
   - HTTP状态码分布图

## 性能优化建议

1. 使用会话（Session）对象和连接池，减少连接建立的开销
2. 配置适当的重试策略，提高测试的稳定性
3. 使用线程池管理并发用户，避免创建过多线程
4. 使用线程安全的数据结构和锁机制，确保结果统计的准确性
5. 支持用户逐步启动，模拟真实的用户行为

## 注意事项

1. 请确保有足够的网络带宽和系统资源来支持高并发测试
2. 高并发测试可能会对被测服务造成较大压力，请在适当的环境中进行测试
3. 请确保视频文件路径是被测服务可以访问的
4. 测试结果可能受网络环境、服务器负载等因素影响

## 常见问题

1. **Q: 如何选择合适的并发用户数？**  
   A: 这取决于你的测试目标和被测服务的预期负载。建议从小规模开始，逐步增加并发用户数，直到找到性能瓶颈。

2. **Q: 为什么要使用ramp-up时间？**  
   A: 逐步启动用户可以模拟更真实的用户行为，避免瞬间的高负载可能导致的异常情况。

3. **Q: 如何解释响应时间百分位数？**  
   A: P50表示50%的请求响应时间低于该值；P90表示90%的请求响应时间低于该值，以此类推。P95/P99通常用于评估系统在高负载下的性能表现。

## 许可证

MIT License 