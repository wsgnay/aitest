-- 无人机检测系统数据库
CREATE DATABASE IF NOT EXISTS drone_detection DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE drone_detection;

-- ===========================
-- 1. 图像检测记录表
-- ===========================
CREATE TABLE image_detections (
                                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                  image_path VARCHAR(500) NOT NULL COMMENT '原始图像路径',
                                  image_name VARCHAR(255) NOT NULL COMMENT '图像文件名',
                                  image_size BIGINT COMMENT '图像文件大小(字节)',
                                  image_width INT COMMENT '图像宽度',
                                  image_height INT COMMENT '图像高度',
                                  output_path VARCHAR(500) COMMENT '检测结果图像路径',

    -- 检测配置
                                  confidence_threshold DECIMAL(3,2) DEFAULT 0.30 COMMENT '置信度阈值',
                                  max_image_size INT DEFAULT 1024 COMMENT '发送给API的最大图像尺寸',
                                  model_name VARCHAR(100) DEFAULT 'qwen2.5-vl-72b-instruct' COMMENT '使用的模型名称',

    -- 检测结果
                                  person_count INT DEFAULT 0 COMMENT '检测到的人数',
                                  detection_result JSON COMMENT '详细检测结果(JSON格式)',
                                  processing_time_ms BIGINT COMMENT '处理耗时(毫秒)',
                                  api_call_time_ms BIGINT COMMENT 'API调用耗时(毫秒)',

    -- 状态
                                  status ENUM('PROCESSING', 'SUCCESS', 'FAILED') DEFAULT 'PROCESSING' COMMENT '检测状态',
                                  error_message TEXT COMMENT '错误信息',

    -- 时间戳
                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                                  INDEX idx_image_name (image_name),
                                  INDEX idx_status (status),
                                  INDEX idx_created_at (created_at),
                                  INDEX idx_person_count (person_count)
) ENGINE=InnoDB COMMENT='图像检测记录表';

-- ===========================
-- 2. 视频检测记录表
-- ===========================
CREATE TABLE video_detections (
                                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                  video_path VARCHAR(500) NOT NULL COMMENT '原始视频路径',
                                  video_name VARCHAR(255) NOT NULL COMMENT '视频文件名',
                                  video_size BIGINT COMMENT '视频文件大小(字节)',
                                  video_duration DECIMAL(10,2) COMMENT '视频时长(秒)',
                                  video_fps INT COMMENT '视频帧率',
                                  video_width INT COMMENT '视频宽度',
                                  video_height INT COMMENT '视频高度',
                                  output_path VARCHAR(500) COMMENT '跟踪结果视频路径',

    -- 检测配置
                                  confidence_threshold DECIMAL(3,2) DEFAULT 0.50 COMMENT '置信度阈值',
                                  tracker_type VARCHAR(20) DEFAULT 'MIL' COMMENT '跟踪器类型',
                                  model_name VARCHAR(100) DEFAULT 'qwen2.5-vl-72b-instruct' COMMENT '使用的模型名称',
                                  detection_frames JSON COMMENT '检测帧列表',
                                  auto_dedup_enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用自动去重',

    -- 检测结果
                                  total_frames INT COMMENT '总帧数',
                                  processed_frames INT COMMENT '已处理帧数',
                                  max_person_count INT DEFAULT 0 COMMENT '最大同时检测人数',
                                  total_api_calls INT DEFAULT 0 COMMENT 'API调用次数',
                                  dedup_operations INT DEFAULT 0 COMMENT '去重操作次数',
                                  active_trackers INT DEFAULT 0 COMMENT '最终活跃跟踪器数量',

    -- 详细结果
                                  tracking_result JSON COMMENT '详细跟踪结果(JSON格式)',
                                  processing_time_ms BIGINT COMMENT '总处理耗时(毫秒)',

    -- 状态
                                  status ENUM('PROCESSING', 'SUCCESS', 'FAILED', 'CANCELLED') DEFAULT 'PROCESSING' COMMENT '处理状态',
                                  progress DECIMAL(5,2) DEFAULT 0.00 COMMENT '处理进度百分比',
                                  error_message TEXT COMMENT '错误信息',

    -- 时间戳
                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                                  INDEX idx_video_name (video_name),
                                  INDEX idx_status (status),
                                  INDEX idx_created_at (created_at),
                                  INDEX idx_progress (progress)
) ENGINE=InnoDB COMMENT='视频检测记录表';

-- ===========================
-- 3. 检测详情表 (存储每个检测到的人物信息)
-- ===========================
CREATE TABLE detection_details (
                                   id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                   detection_type ENUM('IMAGE', 'VIDEO') NOT NULL COMMENT '检测类型',
                                   detection_id BIGINT NOT NULL COMMENT '关联的检测记录ID',
                                   frame_number INT COMMENT '帧号(仅视频)',
                                   timestamp_ms BIGINT COMMENT '时间戳(毫秒，仅视频)',

    -- 人物信息
                                   person_id INT COMMENT '人物编号',
                                   bbox_x1 DECIMAL(10,2) COMMENT '边界框左上角X',
                                   bbox_y1 DECIMAL(10,2) COMMENT '边界框左上角Y',
                                   bbox_x2 DECIMAL(10,2) COMMENT '边界框右下角X',
                                   bbox_y2 DECIMAL(10,2) COMMENT '边界框右下角Y',
                                   confidence DECIMAL(5,4) COMMENT '置信度',
                                   description VARCHAR(500) COMMENT '描述信息',

    -- 跟踪信息(仅视频)
                                   tracker_id INT COMMENT '跟踪器ID',
                                   track_status ENUM('ACTIVE', 'LOST', 'REMOVED') COMMENT '跟踪状态',

                                   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                   INDEX idx_detection (detection_type, detection_id),
                                   INDEX idx_frame (frame_number),
                                   INDEX idx_confidence (confidence),
                                   INDEX idx_tracker (tracker_id)
) ENGINE=InnoDB COMMENT='检测详情表';

-- ===========================
-- 4. 系统配置表
-- ===========================
CREATE TABLE system_configs (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
                                config_value TEXT COMMENT '配置值',
                                config_type ENUM('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'JSON') DEFAULT 'STRING' COMMENT '配置类型',
                                description VARCHAR(500) COMMENT '配置描述',
                                is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用',
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                INDEX idx_config_key (config_key),
                                INDEX idx_active (is_active)
) ENGINE=InnoDB COMMENT='系统配置表';

-- ===========================
-- 5. 预留知识库相关表
-- ===========================

-- 知识库分类表
CREATE TABLE knowledge_categories (
                                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                      category_name VARCHAR(100) NOT NULL COMMENT '分类名称',
                                      category_code VARCHAR(50) NOT NULL UNIQUE COMMENT '分类编码',
                                      parent_id BIGINT COMMENT '父分类ID',
                                      sort_order INT DEFAULT 0 COMMENT '排序',
                                      description TEXT COMMENT '分类描述',
                                      is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用',
                                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                      FOREIGN KEY (parent_id) REFERENCES knowledge_categories(id),
                                      INDEX idx_category_code (category_code),
                                      INDEX idx_parent (parent_id),
                                      INDEX idx_active (is_active)
) ENGINE=InnoDB COMMENT='知识库分类表';

-- 知识库文档表
CREATE TABLE knowledge_documents (
                                     id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                     title VARCHAR(200) NOT NULL COMMENT '文档标题',
                                     category_id BIGINT NOT NULL COMMENT '分类ID',
                                     content LONGTEXT COMMENT '文档内容',
                                     content_type ENUM('TEXT', 'MARKDOWN', 'HTML') DEFAULT 'TEXT' COMMENT '内容类型',
                                     tags VARCHAR(500) COMMENT '标签(逗号分隔)',
                                     keywords VARCHAR(500) COMMENT '关键词(逗号分隔)',
                                     author VARCHAR(100) COMMENT '作者',
                                     source_url VARCHAR(500) COMMENT '来源URL',
                                     view_count INT DEFAULT 0 COMMENT '查看次数',
                                     like_count INT DEFAULT 0 COMMENT '点赞数',
                                     is_public BOOLEAN DEFAULT TRUE COMMENT '是否公开',
                                     is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用',
                                     version VARCHAR(20) DEFAULT '1.0' COMMENT '版本号',
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                     FOREIGN KEY (category_id) REFERENCES knowledge_categories(id),
                                     INDEX idx_title (title),
                                     INDEX idx_category (category_id),
                                     INDEX idx_tags (tags),
                                     INDEX idx_active (is_active),
                                     FULLTEXT idx_content (title, content, tags, keywords)
) ENGINE=InnoDB COMMENT='知识库文档表';

-- 知识库向量索引表(为后期RAG功能预留)
CREATE TABLE knowledge_embeddings (
                                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                      document_id BIGINT NOT NULL COMMENT '文档ID',
                                      chunk_index INT DEFAULT 0 COMMENT '文档块索引',
                                      chunk_content TEXT COMMENT '文档块内容',
                                      embedding_vector JSON COMMENT '向量表示(JSON格式)',
                                      vector_model VARCHAR(100) COMMENT '向量模型名称',
                                      vector_dimension INT COMMENT '向量维度',
                                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                      FOREIGN KEY (document_id) REFERENCES knowledge_documents(id) ON DELETE CASCADE,
                                      INDEX idx_document (document_id),
                                      INDEX idx_chunk (document_id, chunk_index)
) ENGINE=InnoDB COMMENT='知识库向量索引表';

-- ===========================
-- 初始化系统配置数据
-- ===========================
INSERT INTO system_configs (config_key, config_value, config_type, description) VALUES
                                                                                    ('default_confidence_threshold', '0.30', 'DECIMAL', '默认置信度阈值'),
                                                                                    ('default_max_image_size', '1024', 'INTEGER', '默认最大图像尺寸'),
                                                                                    ('default_tracker_type', 'MIL', 'STRING', '默认跟踪器类型'),
                                                                                    ('api_timeout_seconds', '120', 'INTEGER', 'API调用超时时间(秒)'),
                                                                                    ('max_video_size_mb', '500', 'INTEGER', '最大视频文件大小(MB)'),
                                                                                    ('auto_cleanup_days', '30', 'INTEGER', '自动清理天数'),
                                                                                    ('enable_gpu_acceleration', 'false', 'BOOLEAN', '是否启用GPU加速'),
                                                                                    ('supported_image_formats', '["jpg","jpeg","png","bmp"]', 'JSON', '支持的图像格式'),
                                                                                    ('supported_video_formats', '["mp4","avi","mov","mkv"]', 'JSON', '支持的视频格式');

-- 初始化知识库分类数据
INSERT INTO knowledge_categories (category_name, category_code, description) VALUES
                                                                                 ('检测技术', 'detection', '图像和视频检测相关技术文档'),
                                                                                 ('系统操作', 'operation', '系统操作和使用指南'),
                                                                                 ('故障排除', 'troubleshooting', '常见问题和解决方案'),
                                                                                 ('API文档', 'api', 'API接口文档和示例'),
                                                                                 ('更新日志', 'changelog', '系统版本更新记录');

-- 创建视图：检测统计概览
CREATE VIEW detection_stats_view AS
SELECT
        DATE(created_at) as detection_date,
        'IMAGE' as detection_type,
        COUNT(*) as total_detections,
        SUM(person_count) as total_persons,
        AVG(person_count) as avg_persons_per_detection,
        AVG(processing_time_ms) as avg_processing_time,
        SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count,
        SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_count
        FROM image_detections
        GROUP BY DATE(created_at)

        UNION ALL

SELECT
    DATE(created_at) as detection_date,
    'VIDEO' as detection_type,
    COUNT(*) as total_detections,
    SUM(max_person_count) as total_persons,
    AVG(max_person_count) as avg_persons_per_detection,
    AVG(processing_time_ms) as avg_processing_time,
    SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_count
FROM video_detections
GROUP BY DATE(created_at);
