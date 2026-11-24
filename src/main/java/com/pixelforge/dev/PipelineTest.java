package com.pixelforge.dev;

import com.pixelforge.io.DocumentIo;
import com.pixelforge.io.ImageCodec;
import com.pixelforge.model.BlendMode;
import com.pixelforge.model.Document;
import com.pixelforge.model.Layer;
import com.pixelforge.render.Compositor;
import com.pixelforge.render.filter.Filters;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 渲染管线自检：不启动界面，直接验证合成、混合、工程文件读写与导出。
 *
 * <p>用法：{@code java ... PipelineTest 输出目录}</p>
 */
public final class PipelineTest {

    private static int passed;
    private static int failed;

    private PipelineTest() {
    }

    public static void main(String[] args) throws Exception {
        File outDir = new File(args.length > 0 ? args[0] : "pipeline-out");
        if (!outDir.isDirectory() && !outDir.mkdirs()) {
            throw new IllegalStateException("无法创建输出目录: " + outDir);
        }

        testBlankWhite();
        testAlphaOverWhite();
        testMultiply();
        testOpacity();
        testMergeDown();
        testProjectRoundTrip(outDir);
        testPngExport(outDir);
        testJpgExport(outDir);
        testFilters();
        createDemoDocument(outDir);

        System.out.println();
        System.out.println("==== 结果: " + passed + " 通过 / " + failed + " 失败 ====");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------

    private static void testBlankWhite() {
        Document doc = Document.blank(64, 48, true);
        int[] pixels = compositeOf(doc);
        check("空白画布尺寸", pixels.length == 64 * 48);
        check("空白画布全白", pixels[0] == 0xFFFFFFFF && pixels[pixels.length - 1] == 0xFFFFFFFF);
    }

    private static void testAlphaOverWhite() {
        Document doc = Document.blank(4, 4, true);
        Layer top = doc.addLayer("半透明红");
        top.fill(0x80FF0000);
        int[] pixels = compositeOf(doc);
        checkNear("半透明红叠白底", pixels[0], 0xFFFF7F7F, 2);
    }

    private static void testMultiply() {
        Document doc = Document.blank(4, 4, true);
        Layer top = doc.addLayer("正片叠底");
        top.fill(0xFF808080);
        top.setBlendMode(BlendMode.MULTIPLY);
        int[] pixels = compositeOf(doc);
        checkNear("正片叠底 白×灰=灰", pixels[0], 0xFF808080, 2);
    }

    private static void testOpacity() {
        Document doc = Document.blank(4, 4, true);
        Layer top = doc.addLayer("半透明层");
        top.fill(0xFFFF0000);
        top.setOpacity(0.5);
        int[] pixels = compositeOf(doc);
        checkNear("图层不透明度 50%", pixels[0], 0xFFFF7F7F, 2);
    }

    private static void testMergeDown() {
        Document doc = Document.blank(4, 4, true);
        Layer top = doc.addLayer("红");
        top.fill(0xFFFF0000);
        boolean merged = doc.mergeDown(1);
        check("向下合并成功", merged && doc.getLayerCount() == 1);
        check("合并后颜色", doc.getLayer(0).getPixel(0, 0) == 0xFFFF0000);
    }

    private static void testProjectRoundTrip(File outDir) throws Exception {
        Document doc = Document.blank(32, 24, true);
        Layer second = doc.addLayer("第二层");
        second.fill(0xFF00FF00);
        second.setOpacity(0.75);
        second.setBlendMode(BlendMode.SCREEN);
        second.setVisible(false);

        File file = new File(outDir, "roundtrip.pfp");
        DocumentIo.saveProject(doc, file);

        Document loaded = DocumentIo.open(file);
        check("工程文件图层数", loaded.getLayerCount() == 2);
        check("工程文件尺寸", loaded.getWidth() == 32 && loaded.getHeight() == 24);
        Layer loadedTop = loaded.getLayer(1);
        check("工程文件像素", loadedTop.getPixel(5, 5) == 0xFF00FF00);
        check("工程文件不透明度", Math.abs(loadedTop.getOpacity() - 0.75) < 1e-6);
        check("工程文件混合模式", loadedTop.getBlendMode() == BlendMode.SCREEN);
        check("工程文件可见性", !loadedTop.isVisible());
    }

    private static void testPngExport(File outDir) throws Exception {
        Document doc = Document.blank(40, 30, true);
        Layer dot = doc.addLayer("点");
        dot.setPixel(3, 4, 0xFFFF0000);

        File file = new File(outDir, "export.png");
        DocumentIo.exportPng(doc, file);
        BufferedImage image = ImageCodec.read(file);
        check("PNG 尺寸", image.getWidth() == 40 && image.getHeight() == 30);
        check("PNG 像素", (image.getRGB(3, 4) & 0xFFFFFF) == 0xFF0000);
        check("PNG 背景", image.getRGB(0, 0) == 0xFFFFFFFF);
    }

    private static void testJpgExport(File outDir) throws Exception {
        Document doc = Document.blank(40, 30, false);
        Layer block = doc.addLayer("色块");
        block.fill(0xFF3366CC);

        File file = new File(outDir, "export.jpg");
        DocumentIo.exportJpg(doc, file, 0.9f);
        check("JPEG 已生成", file.isFile() && file.length() > 500);
    }

    /** 滤镜数值自检。 */
    private static void testFilters() {
        int width = 8;
        int height = 8;
        int[] solid = new int[width * height];
        java.util.Arrays.fill(solid, 0xFF3366CC);

        int[] gray = Filters.grayscale().apply(solid.clone(), width, height);
        int expectedGray = (int) Math.round(0x33 * 0.299 + 0x66 * 0.587 + 0xCC * 0.114);
        check("灰度滤镜", (gray[0] & 0xFF) == expectedGray
                && ((gray[0] >> 16) & 0xFF) == expectedGray);

        int[] inverted = Filters.invert().apply(solid.clone(), width, height);
        check("反色滤镜", (inverted[0] & 0xFF) == 255 - 0xCC);

        // 模糊：纯色模糊后应保持不变
        int[] blurred = Filters.gaussianBlur(4).apply(solid.clone(), width, height);
        check("模糊纯色不变", Math.abs((blurred[0] & 0xFF) - 0xCC) <= 2);

        // 边缘检测：纯色区域应接近黑色
        int[] edges = Filters.edgeDetect().apply(solid.clone(), width, height);
        check("纯色边缘检测为黑", (edges[0] & 0xFF) < 8);

        // 马赛克：块内颜色一致
        int[] gradient = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                gradient[y * width + x] = 0xFF000000 | (x * 8 << 16) | (y * 8 << 8);
            }
        }
        int[] mosaic = Filters.mosaic(4).apply(gradient.clone(), width, height);
        check("马赛克块内一致", mosaic[0] == mosaic[1] && mosaic[0] == mosaic[width]);

        // 亮度提升后更亮
        int[] brighter = Filters.brightnessContrast(20, 0).apply(solid.clone(), width, height);
        check("亮度调整变亮", (brighter[0] & 0xFF) > 0xCC);

        check("滤镜保持 alpha", ((gray[0] >>> 24) & 0xFF) == 0xFF);
    }

    /** 生成一张有内容的演示文档，供界面截图与手工验收使用。 */
    private static void createDemoDocument(File outDir) throws Exception {
        int width = 900;
        int height = 560;
        Document doc = Document.blank(width, height, true);

        Layer background = doc.getLayer(0);
        background.setName("渐变背景");
        int[] bg = background.getPixels();
        for (int y = 0; y < height; y++) {
            double t = y / (double) (height - 1);
            int r = (int) Math.round(28 + t * 60);
            int g = (int) Math.round(58 + t * 120);
            int b = (int) Math.round(140 + t * 90);
            int argb = 0xFF000000 | (r << 16) | (g << 8) | b;
            java.util.Arrays.fill(bg, y * width, y * width + width, argb);
        }

        Layer circle = doc.addLayer("圆");
        circle.setOpacity(0.85);
        int cx = 260;
        int cy = 250;
        int radius = 150;
        int[] cp = circle.getPixels();
        for (int y = Math.max(0, cy - radius); y < Math.min(height, cy + radius); y++) {
            for (int x = Math.max(0, cx - radius); x < Math.min(width, cx + radius); x++) {
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy <= radius * radius) {
                    cp[y * width + x] = 0xFFFFC24B;
                }
            }
        }

        Layer bars = doc.addLayer("色条");
        bars.setBlendMode(BlendMode.SCREEN);
        int[] colors = {0xFFFF3B30, 0xFF34C759, 0xFF0A84FF, 0xFFFFD60A};
        for (int i = 0; i < colors.length; i++) {
            int x0 = 500 + i * 90;
            for (int y = 120; y < 420; y++) {
                for (int x = x0; x < Math.min(width, x0 + 60); x++) {
                    bars.setPixel(x, y, colors[i]);
                }
            }
        }

        doc.mergeDown(2); // 把色条压到圆层上，演示合并功能

        File project = new File(outDir, "demo.pfp");
        DocumentIo.saveProject(doc, project);
        DocumentIo.exportPng(doc, new File(outDir, "demo.png"));
        System.out.println("演示文档已生成: " + project.getAbsolutePath());
    }

    // ------------------------------------------------------------------

    private static int[] compositeOf(Document doc) {
        int[] target = new int[doc.getWidth() * doc.getHeight()];
        Compositor.composite(doc, target);
        return target;
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }

    private static void checkNear(String name, int actual, int expected, int tolerance) {
        int da = Math.abs(((actual >>> 24) & 0xFF) - ((expected >>> 24) & 0xFF));
        int dr = Math.abs(((actual >> 16) & 0xFF) - ((expected >> 16) & 0xFF));
        int dg = Math.abs(((actual >> 8) & 0xFF) - ((expected >> 8) & 0xFF));
        int db = Math.abs((actual & 0xFF) - (expected & 0xFF));
        boolean ok = da <= tolerance && dr <= tolerance && dg <= tolerance && db <= tolerance;
        check(name + " (实际 " + hex(actual) + " 期望 " + hex(expected) + ")", ok);
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }
}
