// src/main/resources/static/js/utils.js - 工具函数

/**
 * 通用工具函数库
 */
const Utils = {

    /**
     * 格式化文件大小
     * @param {number} bytes 字节数
     * @returns {string} 格式化后的文件大小
     */
    formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    },

    /**
     * 格式化时间
     * @param {Date|string} date 日期对象或字符串
     * @returns {string} 格式化后的时间字符串
     */
    formatDateTime(date) {
        if (!date) return '';
        const d = new Date(date);
        return d.toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    },

    /**
     * 格式化相对时间
     * @param {Date|string} date 日期对象或字符串
     * @returns {string} 相对时间字符串
     */
    formatRelativeTime(date) {
        if (!date) return '';
        const now = new Date();
        const target = new Date(date);
        const diffMs = now - target;
        const diffSecs = Math.floor(diffMs / 1000);
        const diffMins = Math.floor(diffSecs / 60);
        const diffHours = Math.floor(diffMins / 60);
        const diffDays = Math.floor(diffHours / 24);

        if (diffSecs < 60) return '刚刚';
        if (diffMins < 60) return `${diffMins}分钟前`;
        if (diffHours < 24) return `${diffHours}小时前`;
        if (diffDays < 7) return `${diffDays}天前`;
        return this.formatDateTime(date);
    },

    /**
     * 格式化处理时间
     * @param {number} milliseconds 毫秒数
     * @returns {string} 格式化后的处理时间
     */
    formatProcessingTime(milliseconds) {
        if (!milliseconds) return '0秒';
        const seconds = milliseconds / 1000;
        if (seconds < 60) return `${seconds.toFixed(1)}秒`;
        const minutes = Math.floor(seconds / 60);
        const remainingSeconds = Math.floor(seconds % 60);
        return `${minutes}分${remainingSeconds}秒`;
    },

    /**
     * 防抖函数
     * @param {Function} func 要防抖的函数
     * @param {number} wait 等待时间（毫秒）
     * @returns {Function} 防抖后的函数
     */
    debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    },

    /**
     * 节流函数
     * @param {Function} func 要节流的函数
     * @param {number} limit 时间限制（毫秒）
     * @returns {Function} 节流后的函数
     */
    throttle(func, limit) {
        let inThrottle;
        return function() {
            const args = arguments;
            const context = this;
            if (!inThrottle) {
                func.apply(context, args);
                inThrottle = true;
                setTimeout(() => inThrottle = false, limit);
            }
        };
    },

    /**
     * 生成随机ID
     * @param {number} length ID长度
     * @returns {string} 随机ID
     */
    generateId(length = 8) {
        const chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
        let result = '';
        for (let i = 0; i < length; i++) {
            result += chars.charAt(Math.floor(Math.random() * chars.length));
        }
        return result;
    },

    /**
     * 深拷贝对象
     * @param {any} obj 要拷贝的对象
     * @returns {any} 拷贝后的对象
     */
    deepClone(obj) {
        if (obj === null || typeof obj !== 'object') return obj;
        if (obj instanceof Date) return new Date(obj);
        if (obj instanceof Array) return obj.map(item => this.deepClone(item));
        if (typeof obj === 'object') {
            const copy = {};
            Object.keys(obj).forEach(key => {
                copy[key] = this.deepClone(obj[key]);
            });
            return copy;
        }
    },

    /**
     * 验证文件类型
     * @param {File} file 文件对象
     * @param {string[]} allowedTypes 允许的文件类型
     * @returns {boolean} 是否为允许的文件类型
     */
    validateFileType(file, allowedTypes) {
        if (!file || !allowedTypes) return false;
        return allowedTypes.some(type => {
            if (type.startsWith('.')) {
                return file.name.toLowerCase().endsWith(type.toLowerCase());
            }
            return file.type.startsWith(type);
        });
    },

    /**
     * 验证图像文件
     * @param {File} file 文件对象
     * @returns {boolean} 是否为有效的图像文件
     */
    validateImageFile(file) {
        const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/bmp'];
        return this.validateFileType(file, allowedTypes);
    },

    /**
     * 验证视频文件
     * @param {File} file 文件对象
     * @returns {boolean} 是否为有效的视频文件
     */
    validateVideoFile(file) {
        const allowedTypes = ['video/mp4', 'video/avi', 'video/mov', 'video/mkv'];
        return this.validateFileType(file, allowedTypes);
    },

    /**
     * 创建并下载文件
     * @param {string} content 文件内容
     * @param {string} filename 文件名
     * @param {string} contentType 文件类型
     */
    downloadFile(content, filename, contentType = 'text/plain') {
        const blob = new Blob([content], { type: contentType });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
    },

    /**
     * 获取图像预览URL
     * @param {File} file 图像文件
     * @returns {Promise<string>} 预览URL
     */
    getImagePreviewUrl(file) {
        return new Promise((resolve, reject) => {
            if (!this.validateImageFile(file)) {
                reject(new Error('不支持的图像格式'));
                return;
            }
            const reader = new FileReader();
            reader.onload = e => resolve(e.target.result);
            reader.onerror = reject;
            reader.readAsDataURL(file);
        });
    },

    /**
     * 压缩图像
     * @param {File} file 图像文件
     * @param {number} maxWidth 最大宽度
     * @param {number} maxHeight 最大高度
     * @param {number} quality 压缩质量 (0-1)
     * @returns {Promise<Blob>} 压缩后的图像
     */
    compressImage(file, maxWidth = 1920, maxHeight = 1080, quality = 0.8) {
        return new Promise((resolve, reject) => {
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            const img = new Image();

            img.onload = () => {
                // 计算新尺寸
                let { width, height } = img;
                if (width > maxWidth || height > maxHeight) {
                    const ratio = Math.min(maxWidth / width, maxHeight / height);
                    width *= ratio;
                    height *= ratio;
                }

                canvas.width = width;
                canvas.height = height;

                // 绘制并压缩
                ctx.drawImage(img, 0, 0, width, height);
                canvas.toBlob(resolve, 'image/jpeg', quality);
            };

            img.onerror = reject;
            img.src = URL.createObjectURL(file);
        });
    },

    /**
     * 显示通知
     * @param {string} message 消息内容
     * @param {string} type 通知类型
     * @param {number} duration 显示时长（毫秒）
     */
    showNotification(message, type = 'info', duration = 3000) {
        // 检查浏览器是否支持通知
        if ('Notification' in window && Notification.permission === 'granted') {
            new Notification('无人机检测系统', {
                body: message,
                icon: '/favicon.ico'
            });
        }

        // 同时显示页面内通知
        this.showToast(message, type, duration);
    },

    /**
     * 显示Toast通知
     * @param {string} message 消息内容
     * @param {string} type 通知类型
     * @param {number} duration 显示时长（毫秒）
     */
    showToast(message, type = 'info', duration = 3000) {
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            z-index: 10000;
            padding: 12px 20px;
            background: ${this.getToastBgColor(type)};
            color: white;
            border-radius: 6px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
            transform: translateX(100%);
            transition: transform 0.3s ease;
            max-width: 300px;
            word-wrap: break-word;
        `;
        toast.textContent = message;

        document.body.appendChild(toast);

        // 显示动画
        setTimeout(() => {
            toast.style.transform = 'translateX(0)';
        }, 10);

        // 自动隐藏
        setTimeout(() => {
            toast.style.transform = 'translateX(100%)';
            setTimeout(() => {
                if (toast.parentNode) {
                    toast.parentNode.removeChild(toast);
                }
            }, 300);
        }, duration);
    },

    /**
     * 获取Toast背景颜色
     * @param {string} type 通知类型
     * @returns {string} 背景颜色
     */
    getToastBgColor(type) {
        const colors = {
            success: '#10b981',
            error: '#ef4444',
            warning: '#f59e0b',
            info: '#3b82f6'
        };
        return colors[type] || colors.info;
    },

    /**
     * 请求通知权限
     */
    requestNotificationPermission() {
        if ('Notification' in window && Notification.permission === 'default') {
            Notification.requestPermission();
        }
    },

    /**
     * 复制文本到剪贴板
     * @param {string} text 要复制的文本
     * @returns {Promise<boolean>} 是否复制成功
     */
    async copyToClipboard(text) {
        try {
            if (navigator.clipboard) {
                await navigator.clipboard.writeText(text);
                return true;
            }

            // 备用方法
            const textArea = document.createElement('textarea');
            textArea.value = text;
            textArea.style.position = 'fixed';
            textArea.style.opacity = '0';
            document.body.appendChild(textArea);
            textArea.select();
            const successful = document.execCommand('copy');
            document.body.removeChild(textArea);
            return successful;
        } catch (err) {
            console.error('复制失败:', err);
            return false;
        }
    },

    /**
     * 获取设备信息
     * @returns {object} 设备信息
     */
    getDeviceInfo() {
        return {
            userAgent: navigator.userAgent,
            platform: navigator.platform,
            language: navigator.language,
            cookieEnabled: navigator.cookieEnabled,
            onLine: navigator.onLine,
            screenWidth: screen.width,
            screenHeight: screen.height,
            windowWidth: window.innerWidth,
            windowHeight: window.innerHeight,
            pixelRatio: window.devicePixelRatio || 1
        };
    },

    /**
     * 检查网络状态
     * @returns {boolean} 是否在线
     */
    isOnline() {
        return navigator.onLine;
    },

    /**
     * 监听网络状态变化
     * @param {Function} onOnline 上线回调
     * @param {Function} onOffline 离线回调
     */
    watchNetworkStatus(onOnline, onOffline) {
        window.addEventListener('online', onOnline);
        window.addEventListener('offline', onOffline);

        // 返回清理函数
        return () => {
            window.removeEventListener('online', onOnline);
            window.removeEventListener('offline', onOffline);
        };
    }
};

// 导出到全局
window.Utils = Utils;
