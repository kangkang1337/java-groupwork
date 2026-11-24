package com.pixelforge.dev;

import com.pixelforge.io.DocumentIo;
import com.pixelforge.model.Document;
import com.pixelforge.model.Layer;
import com.pixelforge.render.PixelPainter;
import com.pixelforge.util.ColorUtil;

import java.awt.GraphicsEnvironment;
import java.io.File;

/**
 * 生成一张"工具效果演示图"：把画笔、形状、文字、填充都画一遍，
 * 既作为界面截图的内容，也用来肉眼检查抗锯齿与描边质量。
 */
public final class DemoArtGenerator {

    private DemoArtGenerator() {
    }

    public static void main(String[] args) throws Exception {
        File outDir = new File(args.length > 0 ? args[0] : "demo-art-out");
        if (!outDir.isDirectory() && !outDir.mkdirs()) {
            throw new IllegalStateException("无法创建输出目录");
        }

        int width = 900;
        int height = 600;
        Document doc = Document.blank(width, height, true);

        Layer background = doc.getLayer(0);
        background.setName("背景");
        int[] pixels = background.getPixels();
        for (int y = 0; y < height; y++) {
            double t = y / (double) (height - 1);
            int r = (int) Math.round(18 + t * 26);
            int g = (int) Math.round(24 + t * 34);
            int b = (int) Math.round(38 + t * 60);
            int argb = 0xFF000000 | (r << 16) | (g << 8) | b;
            java.util.Arrays.fill(pixels, y * width, y * width + width, argb);
        }

        // 三个半透明圆，验证 source-over 合成与抗锯齿
        Layer circles = doc.addLayer("彩色圆");
        circles.setOpacity(0.85);
        PixelPainter.fillEllipse(circles, 250, 260, 140, 140,
                ColorUtil.toArgb(javafx.scene.paint.Color.web("#FF5A5F")), 0.9);
        PixelPainter.fillEllipse(circles, 390, 260, 140, 140,
                ColorUtil.toArgb(javafx.scene.paint.Color.web("#1FC77A")), 0.75);
        PixelPainter.fillEllipse(circles, 320, 380, 140, 140,
                ColorUtil.toArgb(javafx.scene.paint.Color.web("#3B82F6")), 0.8);

        // 画笔笔迹：一条波浪线，检查连续性与圆头
        Layer strokes = doc.addLayer("画笔");
        double px = 560;
        double py = 120;
        for (int i = 0; i < 60; i++) {
            double angle = Math.toRadians(i * 14);
            double nx = px + Math.cos(angle) * 8;
            double ny = py + Math.sin(angle) * 8 + i * 3.2;
            PixelPainter.strokeSegment(strokes, px, py, nx, ny, 9,
                    ColorUtil.toArgb(javafx.scene.paint.Color.web("#FFC24B")), 1.0);
            px = nx;
            py = ny;
        }

        // 形状：描边矩形、描边椭圆、填充多边形
        PixelPainter.strokeRect(strokes, 560, 380, 850, 540, 6,
                ColorUtil.toArgb(javafx.scene.paint.Color.web("#7C5CFF")), 1.0);
        PixelPainter.strokeEllipse(strokes, 700, 300, 70, 50, 5,
                ColorUtil.toArgb(javafx.scene.paint.Color.web("#22D3EE")), 1.0);
        PixelPainter.fillPolygon(strokes,
                new double[]{600, 660, 640, 560},
                new double[]{60, 80, 150, 120},
                ColorUtil.toArgb(javafx.scene.paint.Color.web("#FF7AB6")), 1.0 );

        // 文字
        Layer text = doc.addLayer("文字");
        String family = pickFont();
        PixelPainter.drawText(text, "PixelForge", family, 62, true,
                ColorUtil.toArgb(javafx.scene.paint.Color.web("#FFFFFF")), 60, 90);
        PixelPainter.drawText(text, "像素工坊 · Java + JavaFX", family, 28, false,
                ColorUtil.toArgb(javafx.scene.paint.Color.web("#9AA4B2")), 62, 130);

        File project = new File(outDir, "demo-art.pfp");
        DocumentIo.saveProject(doc, project);
        DocumentIo.exportPng(doc, new File(outDir, "demo-art.png"));
        System.out.println("演示图已生成: " + project.getAbsolutePath());
        System.out.println("使用字体: " + family);
    }

    private static String pickFont() {
        String[] candidates = {"Microsoft YaHei", "SimHei", "SansSerif"};
        for (String candidate : candidates) {
            for (String available : GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames()) {
                if (available.equalsIgnoreCase(candidate)) {
                    return candidate;
                }
            }
        }
        return "SansSerif";
    }
}
