package com.pixelforge.model;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * 浮动内容：被选区"抠出来"的一块像素，以及它当前的位置 / 缩放 / 旋转 / 倾斜。
 *
 * <p>编辑时它悬浮在图层之上，只有提交时才按仿射变换栅格化回图层，
 * 因此可以反复调整而不会损失原图像素。</p>
 */
public class FloatingContent {

    private final BufferedImage image;
    private final int originX;
    private final int originY;

    private double translateX;
    private double translateY;
    private double scaleX = 1.0;
    private double scaleY = 1.0;
    private double rotation;
    private double skewX;

    public FloatingContent(BufferedImage image, int originX, int originY) {
        this.image = image;
        this.originX = originX;
        this.originY = originY;
    }

    /** 从"本地坐标（裁剪图内的像素坐标）"到"文档坐标"的变换。 */
    public AffineTransform transform() {
        double cx = image.getWidth() / 2.0;
        double cy = image.getHeight() / 2.0;
        AffineTransform t = new AffineTransform();
        t.translate(originX + translateX + cx, originY + translateY + cy);
        t.rotate(rotation);
        t.scale(scaleX, scaleY);
        t.shear(skewX, 0);
        t.translate(-cx, -cy);
        return t;
    }

    /** 内容中心的文档坐标。 */
    public double[] centerInDocument() {
        AffineTransform t = transform();
        double[] point = {image.getWidth() / 2.0, image.getHeight() / 2.0};
        t.transform(point, 0, point, 0, 1);
        return point;
    }

    /** 四个角在文档坐标中的位置，顺序：左上、右上、右下、左下。 */
    public double[][] cornersInDocument() {
        AffineTransform t = transform();
        double w = image.getWidth();
        double h = image.getHeight();
        double[] src = {0, 0, w, 0, w, h, 0, h};
        double[] dst = new double[8];
        t.transform(src, 0, dst, 0, 4);
        return new double[][]{
                {dst[0], dst[1]}, {dst[2], dst[3]}, {dst[4], dst[5]}, {dst[6], dst[7]}
        };
    }

    /** 四条边的中点（文档坐标），顺序：上、右、下、左。 */
    public double[][] edgeMidpointsInDocument() {
        AffineTransform t = transform();
        double w = image.getWidth();
        double h = image.getHeight();
        double[] src = {w / 2, 0, w, h / 2, w / 2, h, 0, h / 2};
        double[] dst = new double[8];
        t.transform(src, 0, dst, 0, 4);
        return new double[][]{
                {dst[0], dst[1]}, {dst[2], dst[3]}, {dst[4], dst[5]}, {dst[6], dst[7]}
        };
    }

    public void moveBy(double dx, double dy) {
        translateX += dx;
        translateY += dy;
    }

    public void setUniformScale(double scale) {
        this.scaleX = Math.max(0.02, scale);
        this.scaleY = Math.max(0.02, scale);
    }

    public void setScale(double sx, double sy) {
        this.scaleX = Math.max(0.02, sx);
        this.scaleY = Math.max(0.02, sy);
    }

    public void setRotation(double radians) {
        this.rotation = radians;
    }

    public void setSkewX(double skew) {
        this.skewX = Math.max(-2.0, Math.min(2.0, skew));
    }

    public void rotateBy(double radians) {
        this.rotation += radians;
    }

    public void resetTransform() {
        translateX = 0;
        translateY = 0;
        scaleX = 1;
        scaleY = 1;
        rotation = 0;
        skewX = 0;
    }

    public BufferedImage getImage() {
        return image;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public double getScaleX() {
        return scaleX;
    }

    public double getScaleY() {
        return scaleY;
    }

    public double getRotation() {
        return rotation;
    }

    public double getSkewX() {
        return skewX;
    }

    /** 是否与原位完全一致（用于判断是否需要真的提交）。 */
    public boolean isIdentity() {
        return Math.abs(translateX) < 0.01 && Math.abs(translateY) < 0.01
                && Math.abs(scaleX - 1) < 0.001 && Math.abs(scaleY - 1) < 0.001
                && Math.abs(rotation) < 0.0001 && Math.abs(skewX) < 0.0001;
    }
}
