package com.pixelforge.io;

import com.pixelforge.model.BlendMode;
import com.pixelforge.model.Document;
import com.pixelforge.model.Layer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 工程文件（.pfp）读写。
 *
 * <p>格式（大端）：
 * <pre>
 *   "PFP1" | int version | int width | int height | int layerCount
 *   每个图层: UTF名称 | bool visible | double opacity | int blendMode | int pngLength | png 字节
 * </pre>
 * 图层像素用 PNG 编码存储，既省空间又能保留完整透明通道。</p>
 */
public final class DocumentFile {

    public static final String EXTENSION = "pfp";
    private static final byte[] MAGIC = {'P', 'F', 'P', '1'};
    private static final int VERSION = 1;

    private DocumentFile() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** 保存文档到 .pfp 文件。 */
    public static void save(Document document, File file) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {
            out.write(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(document.getWidth());
            out.writeInt(document.getHeight());

            var layers = document.getLayers();
            out.writeInt(layers.size());
            for (Layer layer : layers) {
                byte[] nameBytes = layer.getName().getBytes(StandardCharsets.UTF_8);
                out.writeInt(nameBytes.length);
                out.write(nameBytes);

                out.writeBoolean(layer.isVisible());
                out.writeDouble(layer.getOpacity());
                out.writeInt(layer.getBlendMode().ordinal());

                byte[] png = encodeLayerPng(layer);
                out.writeInt(png.length);
                out.write(png);
            }
            out.flush();
        }
    }

    /** 从 .pfp 文件恢复文档。 */
    public static Document load(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != MAGIC[0] || magic[1] != MAGIC[1]
                    || magic[2] != MAGIC[2] || magic[3] != MAGIC[3]) {
                throw new IOException("不是有效的 PixelForge 工程文件");
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("不支持的工程文件版本: " + version);
            }
            int width = in.readInt();
            int height = in.readInt();
            int count = in.readInt();
            if (count <= 0 || count > 512) {
                throw new IOException("工程文件图层数量异常: " + count);
            }

            Document document = Document.empty(width, height);

            for (int i = 0; i < count; i++) {
                int nameLength = in.readInt();
                byte[] nameBytes = new byte[nameLength];
                in.readFully(nameBytes);
                String name = new String(nameBytes, StandardCharsets.UTF_8);

                boolean visible = in.readBoolean();
                double opacity = in.readDouble();
                int blendOrdinal = in.readInt();

                int pngLength = in.readInt();
                byte[] png = new byte[pngLength];
                in.readFully(png);

                Layer layer = new Layer(name, width, height);
                decodeLayerPng(png, layer);
                layer.setVisible(visible);
                layer.setOpacity(opacity);
                BlendMode[] modes = BlendMode.values();
                layer.setBlendMode(blendOrdinal >= 0 && blendOrdinal < modes.length
                        ? modes[blendOrdinal] : BlendMode.NORMAL);
                document.addLayer(layer, document.getLayerCount());
            }

            if (document.getLayerCount() == 0) {
                document.addLayer(new Layer("图层 1", width, height), 0);
            }
            document.setActiveIndex(0);
            document.clearDirty();
            return document;
        }
    }

    private static byte[] encodeLayerPng(Layer layer) throws IOException {
        BufferedImage image = ImageCodec.toBufferedImageArgb(
                layer.getPixels(), layer.getWidth(), layer.getHeight());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(1 << 16);
        if (!ImageIO.write(image, "png", buffer)) {
            throw new IOException("PNG 编码失败");
        }
        return buffer.toByteArray();
    }

    private static void decodeLayerPng(byte[] png, Layer layer) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null) {
            throw new IOException("图层数据损坏");
        }
        if (image.getWidth() != layer.getWidth() || image.getHeight() != layer.getHeight()) {
            throw new IOException("图层尺寸与文档不一致");
        }
        ImageCodec.copyInto(image, layer.getPixels());
    }
}
