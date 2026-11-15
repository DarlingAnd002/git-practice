package shop;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** 图片复制到数据目录，原文件移动或删除后仍可显示。 */
final class ProductImages {
    private final Path directory;
    private final Map<String, BufferedImage> cache = new HashMap<>();
    private final Map<String, BufferedImage> originals = new HashMap<>();

    ProductImages(Path directory) {this.directory = directory;}

    private Path file(String id) {
        if (!id.matches("[A-Z0-9]{3,12}")) throw new IllegalArgumentException("商品编号格式错误。");
        return directory.resolve(id + ".png");
    }

    BufferedImage get(String id) {
        if (cache.containsKey(id)) return cache.get(id);
        BufferedImage image = null;
        try {
            BufferedImage full = original(id);
            if (full != null) image = resize(full, 512);
        } catch (IOException | IllegalArgumentException ignored) {
            // 图片丢失或损坏时使用占位，不影响商品购买。
        }
        cache.put(id, image);return image;
    }

    BufferedImage original(String id) throws IOException {
        if (directory == null) return originals.get(id);
        Path file = file(id);
        return Files.isRegularFile(file) ? read(file, 64L * 1024 * 1024) : null;
    }

    void upload(String id, Path source) throws IOException {
        BufferedImage image = read(source, 10L * 1024 * 1024);
        if (directory != null) {
            Files.createDirectories(directory);
            Path temp = Files.createTempFile(directory, "image-", ".tmp");
            try {
                if (!ImageIO.write(image, "png", temp.toFile())) throw new IOException("无法保存图片。");
                // 新图片写完后才替换旧图，失败时旧图仍保留。
                Files.move(temp, file(id), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {Files.deleteIfExists(temp);}
        }
        if (directory == null) originals.put(id, image);
        cache.put(id, resize(image, 512));
    }

    void remove(String id) throws IOException {
        if (directory != null) Files.deleteIfExists(file(id));
        originals.remove(id);cache.put(id, null);
    }

    private BufferedImage read(Path file, long maxBytes) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > maxBytes) throw new IllegalArgumentException("请选择不超过 10 MB 的 PNG 或 JPG 图片。");
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) throw new IllegalArgumentException("无法读取图片。");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("文件不是有效的 PNG 或 JPG 图片。");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) throw new IllegalArgumentException("仅支持 PNG 和 JPG 图片。");
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long)width * height > 12_000_000) throw new IllegalArgumentException("图片总像素不能超过 1200 万。");
                BufferedImage image = reader.read(0);
                if (image == null) throw new IllegalArgumentException("图片内容无法读取。");
                return image;
            } finally {reader.dispose();}
        }
    }

    static BufferedImage resize(BufferedImage source, int limit) {
        double scale = Math.min(1.0, (double)limit / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int)Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int)Math.round(source.getHeight() * scale));
        // 大幅缩小时逐级采样，避免细线和文字直接缩成锯齿。
        BufferedImage result = source;
        while (result.getWidth() != width || result.getHeight() != height) {
            int nextWidth = Math.max(width, result.getWidth() / 2);
            int nextHeight = Math.max(height, result.getHeight() / 2);
            BufferedImage next = new BufferedImage(nextWidth, nextHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(result, 0, 0, nextWidth, nextHeight, null);g.dispose();result = next;
        }
        return result;
    }
}
