package com.pixelforge.io;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * 位图读写工具：ARGB 像素数组 ↔ 图片文件。
 *
 * <p>项目内部统一用 {@code int[]} 存像素，只有在与磁盘交互时才转换为
 * {@link BufferedImage}。JPEG 不支持透明通道，导出时会先合成到白色背景上。</p>
 */
public final class ImageCodec {

    private ImageCodec() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** 读取图片文件。 */
    public static BufferedImage read(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("无法识别的图片格式: " + file.getName());
        }
        return image;
    }

    /**
     * 把 ARGB 像素数组转成 BufferedImage。
     *
     * @param background 若不为 0，则先把透明区域合成到该背景色上（JPEG 用）
     */
    public static BufferedImage toBufferedImage(int[] argb, int width, int height, int background) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                row[x] = background == 0 ? argb[offset + x] : flatten(argb[offset + x], background);
            }
            image.setRGB(0, y, width, 1, row, 0, width);
        }
        return image;
    }

    /** 带透明通道的 BufferedImage。 */
    public static BufferedImage toBufferedImageArgb(int[] argb, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(argb, y * width, row, 0, width);
            image.setRGB(0, y, width, 1, row, 0, width);
        }
        return image;
    }

    /** 把半透明像素压到底色上。 */
    public static int flatten(int argb, int background) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 255) {
            return argb;
        }
        if (a == 0) {
            return background;
        }
        double alpha = a / 255.0;
        int br = (background >> 16) & 0xFF;
        int bg = (background >> 8) & 0xFF;
        int bb = background & 0xFF;
        int r = (int) Math.round(((argb >> 16) & 0xFF) * alpha + br * (1 - alpha));
        int g = (int) Math.round(((argb >> 8) & 0xFF) * alpha + bg * (1 - alpha));
        int b = (int) Math.round((argb & 0xFF) * alpha + bb * (1 - alpha));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** 导出 PNG（保留透明通道）。 */
    public static void writePng(int[] argb, int width, int height, File file) throws IOException {
        BufferedImage image = toBufferedImageArgb(argb, width, height);
        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("当前环境不支持 PNG 编码");
        }
    }

    /** 导出 JPG（合成到白色背景，指定压缩质量 0..1）。 */
    public static void writeJpg(int[] argb, int width, int height, File file, float quality)
            throws IOException {
        BufferedImage image = toBufferedImage(argb, width, height, 0xFFFFFFFF);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("当前环境不支持 JPEG 编码");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file)) {
            if (out == null) {
                throw new IOException("无法写入文件: " + file);
            }
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0.05f, Math.min(1.0f, quality)));
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /** 把 BufferedImage 的像素拷进目标数组（尺寸须一致）。 */
    public static void copyInto(BufferedImage image, int[] target) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (target.length != w * h) {
            throw new IllegalArgumentException("像素数组长度与图片尺寸不符");
        }
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            System.arraycopy(row, 0, target, y * w, w);
        }
    }
}
