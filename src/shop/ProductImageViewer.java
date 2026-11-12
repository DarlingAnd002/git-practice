package shop;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.Callable;

/** 无边框图片预览，滚轮缩放、拖动查看，Esc 或右键关闭。 */
final class ProductImageViewer extends JDialog {
    private BufferedImage image;
    private double zoom = 1;
    private double offsetX;
    private double offsetY;
    private Point dragStart;
    private boolean dragged;
    private final JPanel canvas = new JPanel() {
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (image == null) return;
            Rectangle bounds = imageBounds();
            Graphics2D g = (Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, null);g.dispose();
        }
    };

    ProductImageViewer(Window owner, String name, Callable<BufferedImage> loader) {
        super(owner, name, ModalityType.MODELESS);
        setName("image-viewer");setUndecorated(true);setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        // 不阻挡原窗口；点击别处切换窗口焦点时收起图片。
        addWindowFocusListener(new WindowAdapter() {
            @Override public void windowLostFocus(WindowEvent event) {dispose();}
        });
        getRootPane().setBorder(BorderFactory.createEmptyBorder());
        canvas.setName("image-canvas");canvas.setBackground(new Color(16, 18, 20));setContentPane(canvas);
        setSize(320, 240);setLocationRelativeTo(owner);
        getRootPane().registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseWheelMoved(MouseWheelEvent event) {
                if (image != null) zoomAt(zoom * Math.pow(1.15, -event.getPreciseWheelRotation()), event.getPoint());
            }
            @Override public void mousePressed(MouseEvent event) {
                dragged = false;
                if (SwingUtilities.isLeftMouseButton(event) && image != null && imageBounds().contains(event.getPoint())) dragStart = event.getPoint();
            }
            @Override public void mouseDragged(MouseEvent event) {
                if (dragStart == null) return;
                int dx = event.getX() - dragStart.x, dy = event.getY() - dragStart.y;
                if (Math.abs(dx) + Math.abs(dy) > 2) dragged = true;
                offsetX += dx;offsetY += dy;dragStart = event.getPoint();clampOffsets();canvas.repaint();
            }
            @Override public void mouseReleased(MouseEvent event) {dragStart = null;}
            @Override public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isRightMouseButton(event)) {dispose();return;}
                if (!SwingUtilities.isLeftMouseButton(event) || dragged || image == null) return;
                if (!imageBounds().contains(event.getPoint())) dispose();
                else if (event.getClickCount() == 2) {
                    if (Math.abs(zoom - fitZoom()) < 0.001) zoomAt(1, event.getPoint());else fit();
                }
            }
        };
        canvas.addMouseListener(mouse);canvas.addMouseMotionListener(mouse);canvas.addMouseWheelListener(mouse);
        canvas.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {if (image != null) {clampOffsets();canvas.repaint();}}
        });
        // 原图在后台读取，预览窗口不额外显示工具栏或加载文字。
        new SwingWorker<BufferedImage, Void>() {
            @Override protected BufferedImage doInBackground() throws Exception {return loader.call();}
            @Override protected void done() {
                if (!isDisplayable()) return;
                try {
                    image = get();
                    if (image == null) {failed();return;}
                    sizeToImage();fit();
                } catch (Exception e) {failed();}
            }
        }.execute();
    }

    private void sizeToImage() {
        GraphicsConfiguration screen = getOwner() == null ? getGraphicsConfiguration() : getOwner().getGraphicsConfiguration();
        Rectangle area = screen.getBounds();Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(screen);
        int maxWidth = Math.min(1000, (int)((area.width - insets.left - insets.right) * 0.8));
        int maxHeight = Math.min(760, (int)((area.height - insets.top - insets.bottom) * 0.8));
        double width = image.getWidth() / screen.getDefaultTransform().getScaleX();
        double height = image.getHeight() / screen.getDefaultTransform().getScaleY();
        double scale = Math.min(1, Math.min(maxWidth / width, maxHeight / height));
        // 窗口贴合图片比例，小图不撑成整屏黑底。
        setSize(Math.max(1, (int)Math.round(width * scale)), Math.max(1, (int)Math.round(height * scale)));
        setLocationRelativeTo(getOwner());validate();
    }

    private void failed() {
        dispose();JOptionPane.showMessageDialog(getOwner(), "图片无法读取，请让商家重新上传。", "图片", JOptionPane.INFORMATION_MESSAGE);
    }
    private double screenScale() {return getGraphicsConfiguration().getDefaultTransform().getScaleX();}
    private double displayScale() {return zoom / screenScale();}
    private double fitZoom() {
        return Math.min(1, Math.min((double)canvas.getWidth() * screenScale() / image.getWidth(), (double)canvas.getHeight() * screenScale() / image.getHeight()));
    }
    private Rectangle imageBounds() {
        int width = Math.max(1, (int)Math.round(image.getWidth() * displayScale()));
        int height = Math.max(1, (int)Math.round(image.getHeight() * displayScale()));
        return new Rectangle((int)Math.round((canvas.getWidth() - width) / 2.0 + offsetX), (int)Math.round((canvas.getHeight() - height) / 2.0 + offsetY), width, height);
    }
    private void fit() {zoom = fitZoom();offsetX = 0;offsetY = 0;clampOffsets();canvas.repaint();}
    private void zoomAt(double value, Point anchor) {
        Rectangle before = imageBounds();
        double sourceX = (anchor.x - before.x) / displayScale(), sourceY = (anchor.y - before.y) / displayScale();
        zoom = Math.max(Math.min(0.05, fitZoom()), Math.min(4, value));
        offsetX = anchor.x - canvas.getWidth() / 2.0 + (image.getWidth() / 2.0 - sourceX) * displayScale();
        offsetY = anchor.y - canvas.getHeight() / 2.0 + (image.getHeight() / 2.0 - sourceY) * displayScale();
        clampOffsets();canvas.repaint();
    }
    private void clampOffsets() {
        double maxX = Math.max(0, (image.getWidth() * displayScale() - canvas.getWidth()) / 2);
        double maxY = Math.max(0, (image.getHeight() * displayScale() - canvas.getHeight()) / 2);
        offsetX = Math.max(-maxX, Math.min(maxX, offsetX));offsetY = Math.max(-maxY, Math.min(maxY, offsetY));
        canvas.setCursor(Cursor.getPredefinedCursor(maxX > 0 || maxY > 0 ? Cursor.MOVE_CURSOR : Cursor.DEFAULT_CURSOR));
    }
}
