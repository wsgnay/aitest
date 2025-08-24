package com.example.ffmpeg.dto;

import lombok.Data;

@Data
public class PersonDetection {
    private double[] bbox; // [x1, y1, x2, y2]
    private double confidence;
    private String description;
    private int id;

    public PersonDetection() {}

    public PersonDetection(double[] bbox, double confidence, String description) {
        this.bbox = bbox;
        this.confidence = confidence;
        this.description = description;
    }

    /**
     * 边界框辅助类
     */
    @Data
    public static class BoundingBox {
        /** 左上角X坐标 */
        private double x1;

        /** 左上角Y坐标 */
        private double y1;

        /** 右下角X坐标 */
        private double x2;

        /** 右下角Y坐标 */
        private double y2;

        public BoundingBox() {}

        public BoundingBox(double x1, double y1, double x2, double y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        /**
         * 从double数组创建BoundingBox
         */
        public static BoundingBox fromArray(double[] bbox) {
            if (bbox == null || bbox.length < 4) {
                return new BoundingBox();
            }
            return new BoundingBox(bbox[0], bbox[1], bbox[2], bbox[3]);
        }

        /**
         * 转换为double数组
         */
        public double[] toArray() {
            return new double[]{x1, y1, x2, y2};
        }

        /**
         * 获取宽度
         */
        public double getWidth() {
            return x2 - x1;
        }

        /**
         * 获取高度
         */
        public double getHeight() {
            return y2 - y1;
        }

        /**
         * 获取面积
         */
        public double getArea() {
            return getWidth() * getHeight();
        }

        /**
         * 计算与另一个边界框的IoU
         */
        public double calculateIoU(BoundingBox other) {
            if (other == null) return 0.0;

            double intersectionX1 = Math.max(x1, other.x1);
            double intersectionY1 = Math.max(y1, other.y1);
            double intersectionX2 = Math.min(x2, other.x2);
            double intersectionY2 = Math.min(y2, other.y2);

            if (intersectionX2 <= intersectionX1 || intersectionY2 <= intersectionY1) {
                return 0.0;
            }

            double intersectionArea = (intersectionX2 - intersectionX1) * (intersectionY2 - intersectionY1);
            double unionArea = getArea() + other.getArea() - intersectionArea;

            return intersectionArea / unionArea;
        }
    }
}
