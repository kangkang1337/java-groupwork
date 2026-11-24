package com.pixelforge.io;

import com.pixelforge.model.Document;
import com.pixelforge.render.Compositor;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * 文档级读写门面：按扩展名决定"打开图片"还是"打开工程"，并负责导出。
 */
public final class DocumentIo {

    private DocumentIo() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** 打开文件：.pfp 读工程，其余按图片读成单图层文档。 */
    public static Document open(File file) throws IOException {
        if (isProjectFile(file)) {
            Document document = DocumentFile.load(file);
            document.setFile(file);
            document.clearDirty();
            return document;
        }
        BufferedImage image = ImageCodec.read(file);
        Document document = Document.fromImage(image, file.getName());
        document.setTitle(file.getName());
        document.clearDirty();
        return document;
    }

    public static boolean isProjectFile(File file) {
        return file.getName().toLowerCase(Locale.ROOT).endsWith("." + DocumentFile.EXTENSION);
    }

    /** 保存工程文件。 */
    public static void saveProject(Document document, File file) throws IOException {
        DocumentFile.save(document, file);
    }

    /** 导出 PNG。 */
    public static void exportPng(Document document, File file) throws IOException {
        int[] pixels = compositeOf(document);
        ImageCodec.writePng(pixels, document.getWidth(), document.getHeight(), file);
    }

    /** 导出 JPEG（透明区域压白底）。 */
    public static void exportJpg(Document document, File file, float quality) throws IOException {
        int[] pixels = compositeOf(document);
        ImageCodec.writeJpg(pixels, document.getWidth(), document.getHeight(), file, quality);
    }

    /** 合成当前文档的全部可见图层。 */
    public static int[] compositeOf(Document document) {
        int[] target = new int[document.getWidth() * document.getHeight()];
        Compositor.composite(document, target);
        return target;
    }
}
