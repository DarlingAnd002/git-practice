package shop;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/** 直接绘制较高分辨率的缩略图，让系统按屏幕缩放比例采样。 */
final class ProductImageIcon implements Icon {
    private final BufferedImage image;
    private final int width;
    private final int height;
    private BufferedImage rendered;
    private int renderedSize;

    ProductImageIcon(BufferedImage image, int size) {
        this.image = image;
        double scale = Math.min(1.0, (double)size / Math.max(image.getWidth(), image.getHeight()));
        width = Math.max(1, (int)Math.round(image.getWidth() * scale));
        height = Math.max(1, (int)Math.round(image.getHeight() * scale));
    }
    public int getIconWidth() {return width;}
    public int getIconHeight() {return height;}
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double screenScale = Math.max(Math.abs(g.getTransform().getScaleX()), Math.abs(g.getTransform().getScaleY()));
        int pixels = Math.max(1, (int)Math.ceil(Math.max(width, height) * screenScale));
        if (rendered == null || renderedSize != pixels) {rendered = ProductImages.resize(image, pixels);renderedSize = pixels;}
        g.drawImage(rendered, x, y, width, height, null);g.dispose();
    }
}
