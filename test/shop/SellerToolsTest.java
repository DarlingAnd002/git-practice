package shop;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

public final class SellerToolsTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("build"), "seller-tools-");
        try {
            AuthService auth = new AuthService(dir.resolve("users.properties"));
            AuthService.Session seller = user(auth, "seller", Role.SELLER);
            AuthService.Session other = user(auth, "other", Role.SELLER);
            AuthService.Session buyer = user(auth, "buyer", Role.CONSUMER);
            Path file = dir.resolve("store.properties");
            Catalog catalog = new Catalog(file);
            Product first = catalog.add(seller, "Pen", new BigDecimal("10.00"), 10);
            Product second = catalog.add(other, "Book", new BigDecimal("20.00"), 10);
            check(first.id().equals("P001") && second.id().equals("P002"));
            reject(() -> catalog.add(buyer, "Bad", BigDecimal.ONE, 1));
            Cart cart = new Cart(catalog, buyer);
            cart.add(first.id(), 1);
            Checkout.Receipt oldQuote = new Checkout().quote(cart);
            catalog.purchase(buyer, cart, oldQuote);
            cart.add(first.id(), 2);
            reject(() -> catalog.takeDown(other, first.id()));
            reject(() -> catalog.takeDown(buyer, first.id()));
            check(catalog.isListed(first.id()));
            catalog.takeDown(seller, first.id());
            check(!catalog.isListed(first.id()) && catalog.stock(first.id()) == 9);
            check(catalog.listings().size() == 1 && catalog.mine(seller).size() == 1);
            check(cart.lines().size() == 1 && cart.stockProblems().get(0).contains("已下架"));
            reject(() -> cart.add(first.id(), 1));
            reject(() -> new Checkout().quote(cart));
            reject(() -> catalog.purchase(buyer, cart, oldQuote));
            check(cart.lines().size() == 1 && catalog.stock(first.id()) == 9);
            check(catalog.sales(seller).size() == 1 && catalog.purchases(buyer).size() == 1);
            catalog.changePrice(seller, first.id(), new BigDecimal("11.00"));
            catalog.changeStock(seller, first.id(), 50);
            check(!catalog.isListed(first.id()));
            check(catalog.purchases(buyer).get(0).total().compareTo(new BigDecimal("10.00")) == 0);
            Catalog loaded = new Catalog(file);
            check(!loaded.isListed(first.id()) && loaded.stock(first.id()) == 50);
            Product third = loaded.add(seller, "Lamp", new BigDecimal("30.00"), 1);
            check(third.id().equals("P003"));
            reject(() -> loaded.takeDown(seller, first.id()));
            cart.update(first.id(), 0);
            check(cart.isEmpty());
            // 旧文件缺少 active 字段，默认原商品在售。
            Properties old = new Properties();
            try (var reader = Files.newBufferedReader(file)) { old.load(reader); }
            for (String key : new ArrayList<>(old.stringPropertyNames())) {
                if (key.endsWith(".active")) old.remove(key);
            }
            Path legacy = dir.resolve("old-store.properties");
            try (var writer = Files.newBufferedWriter(legacy)) { old.store(writer, "Old fixture"); }
            check(new Catalog(legacy).isListed(first.id()));
            System.out.println("PASS: " + checks + " auto-ID/take-down checks");
        } finally {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    private static AuthService.Session user(AuthService auth, String name, Role role) throws Exception {
        auth.register(name, "Password123".toCharArray(), role);
        return auth.login(name, "Password123".toCharArray());
    }
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Seller tools check failed: " + checks);
    }
    private static void reject(Runnable action) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection: " + checks);
    }
}
