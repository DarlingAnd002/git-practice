package shop;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

public final class ProductEditTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("build"), "product-edit-");
        try {
            AuthService auth = new AuthService(dir.resolve("users.properties"));
            AuthService.Session seller = user(auth, "seller", Role.SELLER);
            AuthService.Session other = user(auth, "other", Role.SELLER);
            AuthService.Session buyer = user(auth, "buyer", Role.CONSUMER);
            Path file = dir.resolve("store.properties");
            Catalog catalog = new Catalog(file);
            Product old = catalog.add(seller, "Pen", new BigDecimal("10.00"), 10);
            String id = old.id();
            Cart cart = new Cart(catalog, buyer);
            cart.add(id, 5);
            Checkout.Receipt quote = new Checkout().quote(cart);
            reject(() -> catalog.updateProduct(other, id, "Bad", BigDecimal.ONE, 1));
            reject(() -> catalog.updateProduct(buyer, id, "Bad", BigDecimal.ONE, 1));
            check(catalog.find(id).equals(old) && catalog.stock(id) == 10);
            reject(() -> catalog.updateProduct(seller, id, " ", BigDecimal.ONE, 1));
            reject(() -> catalog.updateProduct(seller, id, "x".repeat(61), BigDecimal.ONE, 1));
            reject(() -> catalog.updateProduct(seller, id, "Bad\nName", BigDecimal.ONE, 1));
            reject(() -> catalog.updateProduct(seller, id, "Changed", BigDecimal.ZERO, 1));
            reject(() -> catalog.updateProduct(seller, id, "Changed", BigDecimal.ONE, -1));
            check(catalog.find(id).equals(old) && catalog.stock(id) == 10);
            catalog.updateProduct(seller, id, "New Pen", new BigDecimal("12.00"), 8);
            check(catalog.find(id).name().equals("New Pen") && catalog.stock(id) == 8);
            check(cart.lines().get(0).product().name().equals("New Pen"));
            check(cart.subtotal().compareTo(new BigDecimal("60.00")) == 0);
            reject(() -> catalog.purchase(buyer, cart, quote));
            check(!cart.isEmpty() && catalog.stock(id) == 8);
            Cart purchase = new Cart(catalog, buyer);
            purchase.add(id, 2);
            Order order = catalog.purchase(buyer, purchase, new Checkout().quote(purchase)).get(0);
            cart.removePurchased(id, 2);
            check(cart.lines().get(0).quantity() == 3 && catalog.stock(id) == 6);
            check(order.total().compareTo(new BigDecimal("24.00")) == 0);
            catalog.updateProduct(seller, id, "Later Name", new BigDecimal("15.00"), 1);
            check(order.items().get(0).name().equals("New Pen"));
            check(order.items().get(0).unitPrice().compareTo(new BigDecimal("12.00")) == 0);
            // 旧购物车数量超过剩余库存时，买走一部分后仍保留剩余项供处理。
            Cart single = new Cart(catalog, buyer);
            single.add(id, 1);
            catalog.purchase(buyer, single, new Checkout().quote(single));
            cart.removePurchased(id, 1);
            check(cart.lines().get(0).quantity() == 2 && cart.stockProblems().get(0).contains("Sold out"));
            catalog.takeDown(seller, id);
            catalog.updateProduct(seller, id, "Hidden", new BigDecimal("20.00"), 5);
            check(!catalog.isListed(id));
            Catalog loaded = new Catalog(file);
            check(loaded.find(id).name().equals("Hidden") && loaded.stock(id) == 5);
            check(loaded.purchases(buyer).size() == 2 && !loaded.isListed(id));
            reject(() -> cart.removePurchased(id, 3));
            check(cart.lines().get(0).quantity() == 2);
            cart.removePurchased(id, 2);
            check(cart.isEmpty());
            System.out.println("PASS: " + checks + " product-edit/single-purchase checks");
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
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Product edit check failed: " + checks);
    }
    private static void reject(Runnable action) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection: " + checks);
    }
}
