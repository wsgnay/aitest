// js/app.js - 无人机检测系统前端JavaScript

// class DroneDetectionApp {
//     constructor() {
//         this.apiBaseUrl = '/api/drone';
//         this.currentSection = 'dashboard';
//         this.settings = this.loadSettingsFromStorage();
//         this.currentImageFile = null;
//         this.currentVideoFile = null;
//         this.init();
//     }
class DroneDetectionApp {
    constructor() {
        this.apiBaseUrl = 'http://localhost:8080/api/drone';
        this.isDebugMode = true; // 调试模式
        this.currentSection = 'dashboard';
        this.settings = this.loadSettingsFromStorage();
        this.currentImageFile = null;
        this.currentVideoFile = null;
        this.init(); // 修复：使用 init() 而不是 initializeApp()
    }

    init() {
        this.setupEventListeners();
        this.loadDashboard();
        this.loadSettings();

        // 定期刷新仪表板数据
        setInterval(() => {
            if (this.currentSection === 'dashboard') {
                this.loadDashboard();
            }
        }, 30000); // 30秒刷新一次
    }

    setupEventListeners() {
        // 文件上传事件
        this.setupFileUpload();

        // 检测按钮事件
        document.getElementById('startImageDetection').addEventListener('click', () => {
            this.startImageDetection();
        });

        document.getElementById('startVideoTracking').addEventListener('click', () => {
            this.startVideoTracking();
        });

        // 置信度滑块同步
        const imageConfidence = document.getElementById('imageConfidence');
        const imageConfidenceValue = document.getElementById('imageConfidenceValue');

        if (imageConfidence && imageConfidenceValue) {
            imageConfidence.addEventListener('input', (e) => {
                imageConfidenceValue.value = e.target.value;
            });

            imageConfidenceValue.addEventListener('input', (e) => {
                imageConfidence.value = e.target.value;
            });
        }

        // 设置页面今日日期
        const dateFilter = document.getElementById('dateFilter');
        if (dateFilter) {
            dateFilter.value = new Date().toISOString().split('T')[0];
        }
    }

    setupFileUpload() {
        // 图像上传
        const imageUploadArea = document.getElementById('imageUploadArea');
        const imageInput = document.getElementById('imageInput');

        if (imageUploadArea && imageInput) {
            ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
                imageUploadArea.addEventListener(eventName, this.preventDefaults, false);
            });

            ['dragenter', 'dragover'].forEach(eventName => {
                imageUploadArea.addEventListener(eventName, () => {
                    imageUploadArea.classList.add('dragover');
                });
            });

            ['dragleave', 'drop'].forEach(eventName => {
                imageUploadArea.addEventListener(eventName, () => {
                    imageUploadArea.classList.remove('dragover');
                });
            });

            imageUploadArea.addEventListener('drop', (e) => {
                const files = e.dataTransfer.files;
                if (files.length > 0 && files[0].type.startsWith('image/')) {
                    this.handleImageFile(files[0]);
                }
            });

            imageInput.addEventListener('change', (e) => {
                if (e.target.files.length > 0) {
                    this.handleImageFile(e.target.files[0]);
                }
            });
        }

        // 视频上传
        const videoUploadArea = document.getElementById('videoUploadArea');
        const videoInput = document.getElementById('videoInput');

        if (videoUploadArea && videoInput) {
            ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
                videoUploadArea.addEventListener(eventName, this.preventDefaults, false);
            });

            ['dragenter', 'dragover'].forEach(eventName => {
                videoUploadArea.addEventListener(eventName, () => {
                    videoUploadArea.classList.add('dragover');
                });
            });

            ['dragleave', 'drop'].forEach(eventName => {
                videoUploadArea.addEventListener(eventName, () => {
                    videoUploadArea.classList.remove('dragover');
                });
            });

            videoUploadArea.addEventListener('drop', (e) => {
                const files = e.dataTransfer.files;
                if (files.length > 0 && files[0].type.startsWith('video/')) {
                    this.handleVideoFile(files[0]);
                }
            });

            videoInput.addEventListener('change', (e) => {
                if (e.target.files.length > 0) {
                    this.handleVideoFile(e.target.files[0]);
                }
            });
        }
    }

    preventDefaults(e) {
        e.preventDefault();
        e.stopPropagation();
    }

    handleImageFile(file) {
        const maxSize = 50 * 1024 * 1024; // 50MB
        if (file.size > maxSize) {
            this.showAlert('文件太大，请选择小于50MB的图像文件', 'warning');
            return;
        }

        const reader = new FileReader();
        reader.onload = (e) => {
            const preview = `
                <div class="text-center">
                    <img src="${e.target.result}" class="result-image mb-3" style="max-height: 200px;">
                    <div class="d-flex justify-content-between align-items-center">
                        <span class="badge bg-primary">${file.name}</span>
                        <span class="text-muted">${this.formatFileSize(file.size)}</span>
                    </div>
                </div>
            `;
            const imageResult = document.getElementById('imageResult');
            if (imageResult) {
                imageResult.innerHTML = preview;
            }

            const startButton = document.getElementById('startImageDetection');
            if (startButton) {
                startButton.disabled = false;
            }
        };
        reader.readAsDataURL(file);
        this.currentImageFile = file;
    }

    handleVideoFile(file) {
        const maxSize = 500 * 1024 * 1024; // 500MB
        if (file.size > maxSize) {
            this.showAlert('文件太大，请选择小于500MB的视频文件', 'warning');
            return;
        }

        const preview = `
            <div class="text-center">
                <i class="bi bi-camera-video-fill fs-1 text-primary mb-3"></i>
                <div class="d-flex justify-content-between align-items-center">
                    <span class="badge bg-primary">${file.name}</span>
                    <span class="text-muted">${this.formatFileSize(file.size)}</span>
                </div>
                <div class="mt-2">
                    <small class="text-muted">视频已准备就绪</small>
                </div>
            </div>
        `;
        const videoResult = document.getElementById('videoResult');
        if (videoResult) {
            videoResult.innerHTML = preview;
        }

        const startButton = document.getElementById('startVideoTracking');
        if (startButton) {
            startButton.disabled = false;
        }
        this.currentVideoFile = file;
    }

    async startImageDetection() {
        if (!this.currentImageFile) {
            this.showAlert('请先选择图像文件', 'warning');
            return;
        }

        if (!this.settings.apiKey) {
            this.showAlert('请先在设置中配置API Key', 'warning');
            this.showSection('settings');
            return;
        }

        const formData = new FormData();
        formData.append('file', this.currentImageFile);

        const request = {
            imagePath: this.currentImageFile.name,
            confThreshold: parseFloat(document.getElementById('imageConfidenceValue')?.value || 0.3),
            maxImageSize: parseInt(this.settings.maxImageSize)
        };

        try {
            this.showProgress('image', true);
            this.updateProgress('image', 10);
            this.addLog('开始图像检测...', 'info'); // 添加日志

            // 模拟上传进度
            this.simulateUploadProgress('image', 10, 50, 2000);

            // 首先上传文件
            const uploadResponse = await fetch('/api/drone/upload/image', {
                method: 'POST',
                body: formData
            });

            if (!uploadResponse.ok) {
                throw new Error(`文件上传失败: ${uploadResponse.status}`);
            }

            const uploadResult = await uploadResponse.json();
            request.imagePath = uploadResult.filePath;

            this.updateProgress('image', 60);
            this.addLog('图像上传完成，开始AI检测...', 'info'); // 添加日志

            // 然后进行检测
            const response = await fetch(`${this.apiBaseUrl}/image/detect`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${this.settings.apiKey}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(request)
            });

            this.updateProgress('image', 80);

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const result = await response.json();
            this.updateProgress('image', 100);
            this.addLog('图像检测完成', 'success'); // 添加日志

            setTimeout(() => {
                this.showProgress('image', false);
                this.displayImageResult(result);
                this.addLog(`图像检测完成: 检测到 ${result.totalPersons || 0} 个人物`, 'success');
            }, 500);

        } catch (error) {
            this.showProgress('image', false);
            this.showAlert(`检测失败: ${error.message}`, 'danger');
            this.addLog(`图像检测失败: ${error.message}`, 'error'); // 添加错误日志
        }
    }

    async startVideoTracking() {
        if (!this.currentVideoFile) {
            this.showAlert('请先选择视频文件', 'warning');
            return;
        }

        if (!this.settings.apiKey) {
            this.showAlert('请先在设置中配置API Key', 'warning');
            this.showSection('settings');
            return;
        }

        // 创建FormData用于文件上传
        const formData = new FormData();
        formData.append('file', this.currentVideoFile);

        const request = {
            videoSource: this.currentVideoFile.name,
            apiKey: this.settings.apiKey,
            confThreshold: parseFloat(document.getElementById('videoConfidence')?.value || 0.5),
            trackerType: document.getElementById('trackerType')?.value || 'MIL',
            enableAutoDedup: document.getElementById('autoDedup')?.checked || true
        };

        try {
            this.showProgress('video', true);
            this.updateProgress('video', 5);
            this.addLog('开始视频上传和处理...', 'info');

            // 首先上传视频文件
            const uploadResponse = await fetch('/api/drone/upload/video', {
                method: 'POST',
                body: formData
            });

            if (!uploadResponse.ok) {
                throw new Error(`视频上传失败: ${uploadResponse.status}`);
            }

            const uploadResult = await uploadResponse.json();
            request.videoSource = uploadResult.filePath;

            this.updateProgress('video', 20);
            this.addLog('视频上传完成，开始跟踪处理...', 'info');

            // 模拟长时间处理
            this.simulateVideoProcessing();

            const response = await fetch(`${this.apiBaseUrl}/video/track`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(request)
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const result = await response.json();
            this.updateProgress('video', 100);

            setTimeout(() => {
                this.showProgress('video', false);
                this.displayVideoResult(result);
                this.addLog('视频跟踪处理完成', 'success');
            }, 1000);

        } catch (error) {
            this.showProgress('video', false);
            this.showAlert(`跟踪失败: ${error.message}`, 'danger');
            this.addLog(`视频跟踪失败: ${error.message}`, 'error');
        }
    }

    displayImageResult(result) {
        const imageResult = document.getElementById('imageResult');
        if (!imageResult) return;

        if (result.success) {
            const detections = result.detections || [];
            let html = `
            <div class="alert alert-success">
                <i class="bi bi-check-circle me-2"></i>
                检测完成！发现 <strong>${result.totalPersons || 0}</strong> 个人物
            </div>
        `;

            // 显示检测详情
            if (detections.length > 0) {
                html += '<div class="detection-summary mb-3">';
                html += '<h6><i class="bi bi-list-check me-2"></i>检测详情</h6>';
                detections.forEach((detection, index) => {
                    html += `
                    <div class="detection-item d-flex justify-content-between align-items-center mb-2 p-2 bg-light rounded">
                        <span><i class="bi bi-person-fill me-2 text-primary"></i>人物 ${index + 1}</span>
                        <span class="badge bg-success">${(detection.confidence * 100).toFixed(1)}%</span>
                    </div>
                `;
                });
                html += '</div>';
            }

            // 显示处理后的图片 - 使用 outputImagePath 字段
            if (result.outputImagePath) {
                html += `
                <div class="mt-3">
                    <h6><i class="bi bi-image me-2"></i>检测结果图片</h6>
                    <div class="text-center">
                        <img src="${result.outputImagePath}" 
                             class="img-fluid rounded shadow-sm" 
                             alt="检测结果图片"
                             style="max-height: 500px; cursor: pointer;"
                             onclick="viewImageFullscreen('${result.outputImagePath}')"
                             onerror="handleImageError(this, '${result.outputImagePath}')">
                        <div class="mt-2">
                            <button class="btn btn-sm btn-outline-primary" onclick="downloadImage('${result.outputImagePath}')">
                                <i class="bi bi-download me-1"></i>下载图片
                            </button>
                        </div>
                    </div>
                </div>
            `;
            }

            // 显示处理信息
            if (result.processingTime) {
                html += `
                <div class="mt-3 p-2 bg-light rounded">
                    <small class="text-muted">
                        <i class="bi bi-clock me-1"></i>处理时间: ${result.processingTime}ms
                    </small>
                </div>
            `;
            }

            imageResult.innerHTML = html;
        } else {
            this.showAlert(result.error || '检测失败', 'danger');
        }
    }


    displayVideoResult(result) {
        const videoResult = document.getElementById('videoResult');
        if (!videoResult) return;

        if (result.success) {
            // 数据可能在 result 或 result.result 中
            const data = result.result || result;

            let html = `
            <div class="alert alert-success">
                <i class="bi bi-check-circle me-2"></i>
                视频跟踪完成！
            </div>
        `;

            // 统计信息卡片 - 使用实际的字段名
            html += `
            <div class="row text-center mb-3">
                <div class="col-md-3 col-6 mb-3">
                    <div class="card bg-primary text-white">
                        <div class="card-body">
                            <h5>${result.totalFrames || data.totalFrames || 0}</h5>
                            <small>总帧数</small>
                        </div>
                    </div>
                </div>
                <div class="col-md-3 col-6 mb-3">
                    <div class="card bg-success text-white">
                        <div class="card-body">
                            <h5>${result.maxPersonCount || data.maxPersonCount || 0}</h5>
                            <small>最大人数</small>
                        </div>
                    </div>
                </div>
                <div class="col-md-3 col-6 mb-3">
                    <div class="card bg-warning text-white">
                        <div class="card-body">
                            <h5>${result.apiCallCount || data.apiCallCount || 0}</h5>
                            <small>API调用</small>
                        </div>
                    </div>
                </div>
                <div class="col-md-3 col-6 mb-3">
                    <div class="card bg-info text-white">
                        <div class="card-body">
                            <h5>${result.dedupOperations || data.dedupOperations || 0}</h5>
                            <small>去重操作</small>
                        </div>
                    </div>
                </div>
            </div>
        `;

            // 显示处理后的视频 - 使用 outputVideoPath 字段
            const videoPath = result.outputVideoPath || data.outputVideoPath;
            if (videoPath) {
                html += `
                <div class="mt-3">
                    <h6><i class="bi bi-play-circle me-2"></i>跟踪结果视频</h6>
                    <div class="text-center">
                        <video controls 
                               class="w-100 rounded shadow-sm" 
                               style="max-height: 500px;"
                               onloadstart="this.volume=0.3"
                               onerror="handleVideoError(this, '${videoPath}')">
                            <source src="${videoPath}" type="video/mp4">
                            您的浏览器不支持视频播放。
                        </video>
                        <div class="mt-2">
                            <button class="btn btn-sm btn-outline-primary" onclick="downloadVideo('${videoPath}')">
                                <i class="bi bi-download me-1"></i>下载视频
                            </button>
                        </div>
                    </div>
                </div>
            `;
            } else {
                html += `
                <div class="alert alert-info">
                    <i class="bi bi-info-circle me-2"></i>
                    跟踪完成，但未生成输出视频文件
                </div>
            `;
            }

            // 显示处理时间信息
            if (result.processingTimeMs) {
                html += `
                <div class="mt-3 p-2 bg-light rounded">
                    <small class="text-muted">
                        <i class="bi bi-clock me-1"></i>处理时间: ${(result.processingTimeMs / 1000).toFixed(2)}s
                    </small>
                </div>
            `;
            }

            videoResult.innerHTML = html;
        } else {
            this.showAlert(result.error || '跟踪失败', 'danger');
        }
    }


    simulateUploadProgress(type, start, end, duration) {
        const startTime = Date.now();
        const update = () => {
            const elapsed = Date.now() - startTime;
            const progress = Math.min(start + (end - start) * (elapsed / duration), end);
            this.updateProgress(type, progress);

            if (progress < end) {
                requestAnimationFrame(update);
            }
        };
        update();
    }

    simulateVideoProcessing() {
        let progress = 20;
        const steps = [
            { progress: 25, message: '初始化视频处理...', delay: 1000 },
            { progress: 35, message: '分析视频帧...', delay: 2000 },
            { progress: 50, message: '调用AI检测API...', delay: 3000 },
            { progress: 65, message: '初始化跟踪器...', delay: 2000 },
            { progress: 80, message: '处理跟踪数据...', delay: 2500 },
            { progress: 90, message: '生成结果视频...', delay: 2000 },
            { progress: 95, message: '保存处理结果...', delay: 1000 }
        ];

        let stepIndex = 0;
        const processStep = () => {
            if (stepIndex < steps.length) {
                const step = steps[stepIndex];
                setTimeout(() => {
                    this.updateProgress('video', step.progress);
                    this.addLog(step.message, 'info');
                    stepIndex++;
                    processStep();
                }, step.delay);
            }
        };
        processStep();
    }

    showProgress(type, show) {
        const progressElement = document.getElementById(`${type}Progress`);
        if (progressElement) {
            if (show) {
                progressElement.style.display = 'block';
                this.updateProgress(type, 0);
            } else {
                progressElement.style.display = 'none';
            }
        }
    }

    updateProgress(type, percent) {
        const circle = document.getElementById(`${type}ProgressCircle`);
        const text = document.getElementById(`${type}ProgressText`);

        if (circle && text) {
            const circumference = 2 * Math.PI * 26;
            const offset = circumference - (percent / 100 * circumference);

            circle.style.strokeDashoffset = offset;
            text.textContent = Math.round(percent) + '%';
        }
    }

    addLog(message, type = 'info') {
        // 同时更新图像和视频日志容器
        const logContainers = document.querySelectorAll('#logContainer, #imageLogContainer');
        if (!logContainers.length) return;

        const timestamp = new Date().toLocaleTimeString();
        const logClass = type === 'success' ? 'log-success' :
            type === 'error' ? 'log-error' :
                type === 'warning' ? 'log-warning' : '';

        const logEntry = document.createElement('div');
        logEntry.className = `log-entry ${logClass}`;
        logEntry.textContent = `[${timestamp}] ${message}`;

        logContainers.forEach(logContainer => {
            logContainer.appendChild(logEntry.cloneNode(true));
            logContainer.scrollTop = logContainer.scrollHeight;

            // 限制日志条数
            if (logContainer.children.length > 100) {
                logContainer.removeChild(logContainer.firstChild);
            }
        });
    }

    async loadDashboard() {
        try {
            // 尝试从API加载真实数据
            const response = await fetch(`${this.apiBaseUrl}/data/dashboard/stats`);
            let stats;

            if (response.ok) {
                stats = await response.json();
            } else {
                // 如果API不可用，使用模拟数据
                stats = await this.fetchDashboardStats();
            }

            const totalImages = document.getElementById('total-images');
            const totalVideos = document.getElementById('total-videos');
            const totalPersons = document.getElementById('total-persons');
            const apiCalls = document.getElementById('api-calls');

            if (totalImages) totalImages.textContent = stats.totalImages || 0;
            if (totalVideos) totalVideos.textContent = stats.totalVideos || 0;
            if (totalPersons) totalPersons.textContent = stats.totalPersons || 0;
            if (apiCalls) apiCalls.textContent = stats.apiCalls || 0;

            this.loadRecentActivities(stats.recentActivities || []);

        } catch (error) {
            console.error('加载仪表板数据失败:', error);
            // 使用模拟数据
            const stats = await this.fetchDashboardStats();
            this.loadRecentActivities(stats.recentActivities || []);
        }
    }

    async fetchDashboardStats() {
        // 模拟API调用
        return new Promise(resolve => {
            setTimeout(() => {
                resolve({
                    totalImages: Math.floor(Math.random() * 100) + 50,
                    totalVideos: Math.floor(Math.random() * 20) + 10,
                    totalPersons: Math.floor(Math.random() * 500) + 200,
                    apiCalls: Math.floor(Math.random() * 1000) + 500,
                    recentActivities: [
                        {
                            type: 'image',
                            name: 'drone_image_001.jpg',
                            persons: 3,
                            time: '2分钟前',
                            status: 'success'
                        },
                        {
                            type: 'video',
                            name: 'surveillance_video.mp4',
                            persons: 5,
                            time: '10分钟前',
                            status: 'success'
                        },
                        {
                            type: 'image',
                            name: 'aerial_shot.png',
                            persons: 1,
                            time: '1小时前',
                            status: 'success'
                        }
                    ]
                });
            }, 500);
        });
    }

    loadRecentActivities(activities) {
        const recentActivities = document.getElementById('recent-activities');
        if (!recentActivities) return;

        if (activities.length === 0) {
            recentActivities.innerHTML = `
                <div class="text-center text-muted py-4">
                    <i class="bi bi-inbox fs-1 mb-2"></i>
                    <p>暂无检测记录</p>
                </div>
            `;
            return;
        }

        let html = '';
        activities.forEach(activity => {
            const icon = activity.type === 'image' ? 'bi-image' : 'bi-camera-video';
            const badge = activity.status === 'success' ? 'bg-success' : 'bg-danger';

            html += `
                <div class="d-flex align-items-center mb-3 p-2 bg-light rounded">
                    <div class="me-3">
                        <i class="bi ${icon} fs-4 text-primary"></i>
                    </div>
                    <div class="flex-grow-1">
                        <div class="fw-bold">${activity.name}</div>
                        <small class="text-muted">检测到 ${activity.persons} 个人物 • ${activity.time}</small>
                    </div>
                    <span class="badge ${badge}">
                        ${activity.status === 'success' ? '成功' : '失败'}
                    </span>
                </div>
            `;
        });

        recentActivities.innerHTML = html;
    }

    async loadHistory() {
        const filter = document.getElementById('historyFilter')?.value || 'all';
        const status = document.getElementById('statusFilter')?.value || 'all';
        const date = document.getElementById('dateFilter')?.value || '';

        // 显示加载状态
        this.showHistoryLoadingState();

        try {
            const url = `${this.apiBaseUrl}/data/history?filter=${filter}&status=${status}&date=${date}`;
            const response = await fetch(url);
            let historyData = [];

            if (response.ok) {
                const result = await response.json();

                if (result && typeof result === 'object') {
                    if (result.data !== undefined && result.data !== null) {
                        if (Array.isArray(result.data)) {
                            historyData = result.data;
                        } else {
                            historyData = [];
                        }
                    } else if (Array.isArray(result)) {
                        historyData = result;
                    } else {
                        historyData = [];
                    }
                } else {
                    historyData = [];
                }
            } else {
                try {
                    const errorData = await response.json();
                    this.showAlert(`加载失败: ${errorData.error || '服务器错误'}`, 'danger');
                } catch (e) {
                    this.showAlert('服务器连接失败，请检查网络连接', 'danger');
                }
                historyData = [];
            }

            // 最终数据验证
            if (!Array.isArray(historyData)) {
                historyData = [];
            }

            this.displayHistory(historyData);
            this.hideHistoryLoadingState();

        } catch (error) {
            this.hideHistoryLoadingState();
            this.displayHistory([]);
            this.showAlert('网络连接失败: ' + error.message, 'danger');
        }
    }

    // 增强的 displayHistory 方法
    displayHistory(history) {
        const tbody = document.getElementById('historyTable');
        if (!tbody) {
            return;
        }

        // 确保history是数组
        if (!Array.isArray(history)) {
            if (history && typeof history === 'object' && history.data && Array.isArray(history.data)) {
                history = history.data;
            } else {
                history = [];
            }
        }

        if (history.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="7" class="text-center text-muted py-4">
                        <i class="bi bi-inbox fs-1 mb-2"></i>
                        <div>没有找到匹配的记录</div>
                        <small class="text-muted">请先进行一些图像或视频检测，或调整过滤条件</small>
                    </td>
                </tr>
            `;
            return;
        }

        let html = '';
        try {
            history.forEach((item, index) => {
                try {
                    const safeItem = this.normalizeHistoryItem(item, index);
                    const typeIcon = safeItem.type === 'image' ? 'bi-image' : 'bi-camera-video';
                    const typeName = safeItem.type === 'image' ? '图像检测' : '视频跟踪';
                    const statusClass = this.getStatusClass(safeItem.status);
                    const statusText = this.getStatusText(safeItem.status);

                    html += `
                        <tr>
                            <td>
                                <div class="d-flex align-items-center">
                                    <i class="bi ${typeIcon} me-2 text-primary"></i>
                                    <span class="fw-medium">${typeName}</span>
                                </div>
                            </td>
                            <td>
                                <div class="text-truncate" style="max-width: 200px;" title="${safeItem.fileName}">
                                    ${safeItem.fileName}
                                </div>
                            </td>
                            <td>
                                <span class="badge bg-info">${safeItem.personCount}</span>
                            </td>
                            <td>${safeItem.processingTime.toFixed(1)}s</td>
                            <td>
                                <span class="status-badge ${statusClass}">${statusText}</span>
                            </td>
                            <td>
                                <small class="text-muted">${safeItem.createdAtStr}</small>
                            </td>
                            <td>
                                <div class="btn-group btn-group-sm">
                                    <button class="btn btn-outline-primary btn-sm" 
                                            onclick="viewHistoryDetail('${safeItem.type}', ${safeItem.id})" 
                                            title="查看详情">
                                        <i class="bi bi-eye"></i>
                                    </button>
                                    <button class="btn btn-outline-danger btn-sm" 
                                            onclick="deleteHistoryRecord('${safeItem.type}', ${safeItem.id})"
                                            title="删除">
                                        <i class="bi bi-trash"></i>
                                    </button>
                                </div>
                            </td>
                        </tr>
                    `;
                } catch (itemError) {
                    // 静默跳过有问题的数据项
                }
            });

            tbody.innerHTML = html;

        } catch (forEachError) {
            // 显示错误信息
            tbody.innerHTML = `
                <tr>
                    <td colspan="7" class="text-center text-muted py-4">
                        <i class="bi bi-exclamation-triangle fs-1 mb-2 text-warning"></i>
                        <div>数据处理出错</div>
                        <small class="text-muted">请刷新页面重试</small>
                    </td>
                </tr>
            `;
        }
    }

    // 数据规范化方法
    normalizeHistoryItem(item, index) {
        const defaultItem = {
            id: index + 1,
            type: 'unknown',
            fileName: '未知文件',
            personCount: 0,
            processingTime: 0,
            status: 'unknown',
            createdAt: null,
            createdAtStr: '未知时间'
        };

        if (!item || typeof item !== 'object') {
            console.warn('无效的历史记录项:', item);
            return defaultItem;
        }

        // 处理时间字段
        let createdAtStr = '未知时间';
        if (item.createdAtStr) {
            createdAtStr = item.createdAtStr;
        } else if (item.createdAt) {
            // 如果createdAt是时间戳或ISO字符串，进行转换
            try {
                const date = new Date(item.createdAt);
                if (!isNaN(date.getTime())) {
                    createdAtStr = date.toLocaleString('zh-CN', {
                        year: 'numeric',
                        month: '2-digit',
                        day: '2-digit',
                        hour: '2-digit',
                        minute: '2-digit',
                        second: '2-digit'
                    });
                }
            } catch (e) {
                console.warn('时间格式转换失败:', item.createdAt);
            }
        }

        return {
            id: item.id || defaultItem.id,
            type: item.type || defaultItem.type,
            fileName: item.fileName || item.name || defaultItem.fileName,
            personCount: this.safeNumber(item.personCount || item.persons, 0),
            processingTime: this.safeNumber(item.processingTime, 0),
            status: item.status || defaultItem.status,
            createdAt: item.createdAt,
            createdAtStr: createdAtStr,
            confidence: this.safeNumber(item.confidence, 0),
            modelName: item.modelName || '',
            errorMessage: item.errorMessage || ''
        };
    }

    // 安全数字转换
    safeNumber(value, defaultValue = 0) {
        if (typeof value === 'number' && !isNaN(value)) {
            return value;
        }
        if (typeof value === 'string') {
            const parsed = parseFloat(value);
            if (!isNaN(parsed)) {
                return parsed;
            }
        }
        return defaultValue;
    }

    // 获取状态样式类
    getStatusClass(status) {
        switch (status?.toLowerCase()) {
            case 'success':
                return 'status-success';
            case 'processing':
                return 'status-processing';
            case 'failed':
            case 'error':
                return 'status-failed';
            default:
                return 'status-unknown';
        }
    }

    // 获取状态文本
    getStatusText(status) {
        switch (status?.toLowerCase()) {
            case 'success':
                return '成功';
            case 'processing':
                return '处理中';
            case 'failed':
            case 'error':
                return '失败';
            default:
                return '未知';
        }
    }

    // 显示历史记录加载状态
    showHistoryLoadingState() {
        const tbody = document.getElementById('historyTable');
        if (tbody) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="7" class="text-center py-4">
                        <div class="spinner-border text-primary" role="status">
                            <span class="visually-hidden">加载中...</span>
                        </div>
                        <div class="mt-2">正在加载历史记录...</div>
                    </td>
                </tr>
            `;
        }
    }

    // 隐藏历史记录加载状态
    hideHistoryLoadingState() {
        // 加载状态会被实际数据替换，所以这里不需要特别处理
    }

    // 查看历史记录详情
    viewHistoryDetail(type, id) {
        console.log('查看详情:', type, id);
        // TODO: 实现详情查看功能
        this.showAlert(`查看${type === 'image' ? '图像' : '视频'}检测详情 (ID: ${id})`, 'info');
    }

    loadSettings() {
        const elements = {
            apiKey: document.getElementById('apiKey'),
            modelName: document.getElementById('modelName'),
            apiTimeout: document.getElementById('apiTimeout'),
            defaultConfidence: document.getElementById('defaultConfidence'),
            maxImageSize: document.getElementById('maxImageSize'),
            defaultTracker: document.getElementById('defaultTracker')
        };

        if (elements.apiKey) elements.apiKey.value = this.settings.apiKey || '';
        if (elements.modelName) elements.modelName.value = this.settings.modelName || 'qwen2.5-vl-72b-instruct';
        if (elements.apiTimeout) elements.apiTimeout.value = this.settings.apiTimeout || 120;
        if (elements.defaultConfidence) elements.defaultConfidence.value = this.settings.defaultConfidence || 0.3;
        if (elements.maxImageSize) elements.maxImageSize.value = this.settings.maxImageSize || 1024;
        if (elements.defaultTracker) elements.defaultTracker.value = this.settings.defaultTracker || 'MIL';
    }

    saveSettings() {
        const elements = {
            apiKey: document.getElementById('apiKey'),
            modelName: document.getElementById('modelName'),
            apiTimeout: document.getElementById('apiTimeout'),
            defaultConfidence: document.getElementById('defaultConfidence'),
            maxImageSize: document.getElementById('maxImageSize'),
            defaultTracker: document.getElementById('defaultTracker')
        };

        this.settings = {
            apiKey: elements.apiKey?.value || '',
            modelName: elements.modelName?.value || 'qwen2.5-vl-72b-instruct',
            apiTimeout: parseInt(elements.apiTimeout?.value || 120),
            defaultConfidence: parseFloat(elements.defaultConfidence?.value || 0.3),
            maxImageSize: parseInt(elements.maxImageSize?.value || 1024),
            defaultTracker: elements.defaultTracker?.value || 'MIL'
        };

        localStorage.setItem('droneDetectionSettings', JSON.stringify(this.settings));
        this.showAlert('设置已保存', 'success');
    }

    loadSettingsFromStorage() {
        const saved = localStorage.getItem('droneDetectionSettings');
        if (saved) {
            try {
                return JSON.parse(saved);
            } catch (e) {
                console.error('解析设置失败:', e);
            }
        }
        return {
            apiKey: '',
            modelName: 'qwen2.5-vl-72b-instruct',
            apiTimeout: 120,
            defaultConfidence: 0.3,
            maxImageSize: 1024,
            defaultTracker: 'MIL'
        };
    }

    async testApiConnection() {
        const apiKey = document.getElementById('apiKey')?.value;
        if (!apiKey) {
            this.showAlert('请输入API Key', 'warning');
            return;
        }

        try {
            const response = await fetch(`${this.apiBaseUrl}/test`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    apiKey: apiKey,
                    model: document.getElementById('modelName')?.value || 'qwen2.5-vl-72b-instruct'
                })
            });

            const result = await response.json();

            if (result.success) {
                this.showAlert('API连接测试成功！', 'success');
            } else {
                this.showAlert(`API连接失败: ${result.error}`, 'danger');
            }
        } catch (error) {
            this.showAlert(`连接测试失败: ${error.message}`, 'danger');
        }
    }

    showSection(sectionName) {
        // 隐藏所有部分
        document.querySelectorAll('.section-content').forEach(section => {
            section.style.display = 'none';
        });

        // 显示目标部分
        const targetSection = document.getElementById(`${sectionName}-section`);
        if (targetSection) {
            targetSection.style.display = 'block';
        }
        this.currentSection = sectionName;

        // 更新导航栏活跃状态
        document.querySelectorAll('.navbar-nav .nav-link').forEach(link => {
            link.classList.remove('active');
        });
        const activeLink = document.querySelector(`[href="#${sectionName}"]`);
        if (activeLink) {
            activeLink.classList.add('active');
        }

        // 根据部分加载相应数据
        switch(sectionName) {
            case 'dashboard':
                this.loadDashboard();
                break;
            case 'history':
                this.loadHistory();
                break;
            case 'settings':
                this.loadSettings();
                break;
        }
    }

    showAlert(message, type = 'info') {
        const alertContainer = document.createElement('div');
        alertContainer.className = `alert alert-${type} alert-dismissible fade show position-fixed`;
        alertContainer.style.cssText = 'top: 20px; right: 20px; z-index: 9999; min-width: 300px;';
        alertContainer.innerHTML = `
            ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        `;

        document.body.appendChild(alertContainer);

        // 5秒后自动关闭
        setTimeout(() => {
            if (alertContainer.parentNode) {
                alertContainer.remove();
            }
        }, 5000);
    }

    formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }
}

// 全局函数：查看历史记录详情
function viewHistoryDetail(type, id) {
    if (window.app) {
        window.app.viewHistoryDetail(type, id);
    }
}

// 全局函数：删除历史记录
async function deleteHistoryRecord(type, id) {
    if (!confirm(`确定要删除这条${type === 'image' ? '图像' : '视频'}检测记录吗？`)) {
        return;
    }

    try {
        const response = await fetch(`${window.app.apiBaseUrl}/data/history/${type}/${id}`, {
            method: 'DELETE'
        });

        if (response.ok) {
            const result = await response.json();
            if (result.success) {
                window.app.showAlert('记录删除成功', 'success');
                // 重新加载历史记录
                window.app.loadHistory();
            } else {
                window.app.showAlert('删除失败: ' + result.error, 'danger');
            }
        } else {
            window.app.showAlert('删除请求失败', 'danger');
        }
    } catch (error) {
        console.error('删除记录失败:', error);
        window.app.showAlert('删除失败: ' + error.message, 'danger');
    }
}

// 全局函数
function showSection(sectionName) {
    if (window.app) {
        window.app.showSection(sectionName);
    }
}

function toggleApiKeyVisibility() {
    const apiKeyInput = document.getElementById('apiKey');
    const toggleIcon = document.getElementById('apiKeyToggle');

    if (apiKeyInput && toggleIcon) {
        if (apiKeyInput.type === 'password') {
            apiKeyInput.type = 'text';
            toggleIcon.className = 'bi bi-eye-slash';
        } else {
            apiKeyInput.type = 'password';
            toggleIcon.className = 'bi bi-eye';
        }
    }
}

function testApiConnection() {
    if (window.app) {
        window.app.testApiConnection();
    }
}

function saveSettings() {
    if (window.app) {
        window.app.saveSettings();
    }
}

function loadHistory() {
    if (window.app) {
        window.app.loadHistory();
    }
}
// 工具函数
function handleImageError(img, imagePath) {
    console.log('图片加载失败:', imagePath);
    img.style.display = 'none';
    img.insertAdjacentHTML('afterend', `
        <div class="alert alert-warning">
            <i class="bi bi-exclamation-triangle me-2"></i>
            图片加载失败，请检查文件路径: ${imagePath}
        </div>
    `);
}

function handleVideoError(video, videoPath) {
    console.log('视频加载失败:', videoPath);
    video.style.display = 'none';
    video.insertAdjacentHTML('afterend', `
        <div class="alert alert-warning">
            <i class="bi bi-exclamation-triangle me-2"></i>
            视频加载失败，请检查文件路径: ${videoPath}
        </div>
    `);
}

function downloadImage(imagePath) {
    const link = document.createElement('a');
    link.href = imagePath;
    link.download = 'detection_result.png';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
}

function downloadVideo(videoPath) {
    const link = document.createElement('a');
    link.href = videoPath;
    link.download = 'tracking_result.mp4';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
}

function viewImageFullscreen(imagePath) {
    window.open(imagePath, '_blank');
}

// 初始化应用
document.addEventListener('DOMContentLoaded', function() {
    window.app = new DroneDetectionApp();
});
