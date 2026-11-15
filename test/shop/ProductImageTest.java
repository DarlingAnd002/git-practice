package shop;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.Comparator;

public final class ProductImageTest {
    private static int checks;
    private interface Work {void run() throws Exception;}
    private static void check(boolean value) {checks++;if (!value) throw new AssertionError("Image check " + checks);}
    private static void fails(Work work) throws Exception {
        checks++;try {work.run();}catch (IllegalArgumentException | IOException expected) {return;}
        throw new AssertionError("Expected failure " + checks);
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("build"), "product-images-");
        try {
            AuthService auth = new AuthService(dir.resolve("users"));
            auth.register("seller_a", "Password123".toCharArray(), Role.SELLER);
            auth.register("seller_b", "Password123".toCharArray(), Role.SELLER);
            auth.register("buyer", "Password123".toCharArray(), Role.CONSUMER);
            var owner = auth.login("seller_a", "Password123".toCharArray());
            var other = auth.login("seller_b", "Password123".toCharArray());
            var buyer = auth.login("buyer", "Password123".toCharArray());
            Path store = dir.resolve("store");Catalog catalog = new Catalog(store);
            String id = catalog.add(owner, "商品", BigDecimal.TEN, 10).id();
            check(catalog.image(id) == null);
            Path source = dir.resolve("source.png");writeImage(source, "png", 1600, 800, Color.RED);
            fails(() -> catalog.uploadImage(other, id, source));
            fails(() -> catalog.uploadImage(buyer, id, source));
            fails(() -> catalog.removeImage(other, id));
            fails(() -> catalog.uploadImage(null, id, source));
            catalog.uploadImage(owner, id, source);
            Path saved = dir.resolve("images").resolve(id + ".png");
            check(Files.isRegularFile(saved));
            BufferedImage full = ImageIO.read(saved.toFile());check(full.getWidth() == 1600 && full.getHeight() == 800);
            check(catalog.image(id).getWidth() == 512 && catalog.image(id).getHeight() == 256);
            Files.delete(source);Catalog reload = new Catalog(store);check(reload.image(id) != null);
            check((reload.image(id).getRGB(0, 0) & 0xffffff) == 0xff0000);
            check(reload.fullImage(id).getWidth() == 1600 && reload.fullImage(id).getHeight() == 800);
            ProductImageIcon icon = new ProductImageIcon(reload.image(id), 82);
            check(icon.getIconWidth() == 82 && icon.getIconHeight() == 41);
            Path bad = dir.resolve("bad.png");Files.writeString(bad, "not an image");byte[] original = Files.readAllBytes(saved);
            fails(() -> catalog.uploadImage(owner, id, bad));check(java.util.Arrays.equals(original, Files.readAllBytes(saved)));
            check((catalog.image(id).getRGB(0, 0) & 0xffffff) == 0xff0000);
            Path large = dir.resolve("large.png");try (RandomAccessFile f = new RandomAccessFile(large.toFile(), "rw")) {f.setLength(10 * 1024 * 1024 + 1);}
            fails(() -> catalog.uploadImage(owner, id, large));
            Path huge = dir.resolve("huge.png");writeImage(huge, "png", 4001, 3000, Color.WHITE);fails(() -> catalog.uploadImage(owner, id, huge));
            Path gif = dir.resolve("wrong.png");writeImage(gif, "gif", 20, 20, Color.GREEN);fails(() -> catalog.uploadImage(owner, id, gif));
            Path jpg = dir.resolve("photo.jpg");writeImage(jpg, "jpg", 200, 100, Color.BLUE);catalog.uploadImage(owner, id, jpg);
            check((catalog.image(id).getRGB(0, 0) & 0xff) > 200);
            catalog.updateProduct(owner, id, "改名", new BigDecimal("20.00"), 5);catalog.takeDown(owner, id);
            check(catalog.image(id) != null);catalog.relist(owner, id, "改名", BigDecimal.TEN, 10);check(catalog.image(id) != null);
            catalog.removeImage(owner, id);check(!Files.exists(saved) && catalog.image(id) == null);check(new Catalog(store).image(id) == null);
            catalog.uploadImage(owner, id, jpg);Files.delete(saved);Files.createDirectory(saved);Files.writeString(saved.resolve("block"), "x");
            fails(() -> catalog.uploadImage(owner, id, jpg));check(catalog.image(id) != null);
            fails(() -> catalog.removeImage(owner, id));check(catalog.image(id) != null);
            Files.delete(saved.resolve("block"));Files.delete(saved);catalog.uploadImage(owner, id, jpg);
            Files.writeString(saved, "damaged");check(new Catalog(store).image(id) == null);
            catalog.uploadImage(owner, id, jpg);owner.logout();fails(() -> catalog.removeImage(owner, id));
            check(catalog.image(id) != null);
            System.out.println("PASS: " + checks + " product-image checks");
        } finally {
            try (var paths = Files.walk(dir)) {for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);}
        }
    }
    private static void writeImage(Path path, String format, int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();g.setColor(color);g.fillRect(0, 0, width, height);g.dispose();
        if (!ImageIO.write(image, format, path.toFile())) throw new IOException("test fixture write failed");
    }
}
