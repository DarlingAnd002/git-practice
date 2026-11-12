package shop;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

/** 100 件库存，10 个消费者各加购 20 件，前 5 人购买后的购物车状态。 */
public final class CartStockTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("build"), "cart-stock-test-");
        try {
            AuthService auth = new AuthService(dir.resolve("users.properties"));
            auth.register("seller", "Password123".toCharArray(), Role.SELLER);
            AuthService.Session seller = auth.login("seller", "Password123".toCharArray());
            Catalog catalog = new Catalog();
            catalog.add(seller, new Product("P01", "Pen", new BigDecimal("2.00")), 100);
            catalog.add(seller, new Product("P02", "Book", new BigDecimal("5.00")), 10);
            Checkout checkout = new Checkout();
            List<AuthService.Session> buyers = new ArrayList<>();
            List<Cart> carts = new ArrayList<>();
            List<Checkout.Receipt> quotes = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                String username = "buyer_" + i;
                auth.register(username, "Password123".toCharArray(), Role.CONSUMER);
                AuthService.Session buyer = auth.login(username, "Password123".toCharArray());
                Cart cart = new Cart(catalog, buyer);
                cart.add("P01", 20);
                if (i >= 5) cart.add("P02", 1);
                buyers.add(buyer);
                carts.add(cart);
                quotes.add(checkout.quote(cart));
                check(cart.stockProblems().isEmpty());
            }
            check(catalog.stock("P01") == 100);
            for (int i = 0; i < 5; i++) {
                catalog.purchase(buyers.get(i), carts.get(i), quotes.get(i));
                check(catalog.stock("P01") == 100 - (i + 1) * 20);
            }
            check(catalog.stock("P01") == 0);
            check(catalog.sales(seller).size() == 5);
            for (int i = 5; i < 10; i++) {
                Cart cart = carts.get(i);
                AuthService.Session buyer = buyers.get(i);
                Checkout.Receipt oldQuote = quotes.get(i);
                check(cart.lines().size() == 2 && cart.lines().get(0).quantity() == 20);
                check(cart.stockProblems().size() == 1 && cart.stockProblems().get(0).contains("Sold out"));
                reject(() -> checkout.quote(cart));
                reject(() -> catalog.purchase(buyer, cart, oldQuote));
                check(catalog.purchases(buyer).isEmpty() && cart.lines().size() == 2);
            }
            check(catalog.stock("P02") == 10); // 不会自动结算剩余可买商品。
            for (int i = 5; i < 10; i++) {
                Cart cart = carts.get(i);
                cart.update("P01", 0);
                check(cart.stockProblems().isEmpty() && cart.lines().size() == 1);
                Order order = catalog.purchase(buyers.get(i), cart, checkout.quote(cart));
                check(order.total().compareTo(new BigDecimal("5.00")) == 0 && cart.isEmpty());
            }
            check(catalog.stock("P01") == 0 && catalog.stock("P02") == 5);

            // 还有库存但不足购物车数量时，也要拦住，数量不能被自动修改。
            catalog.changeStock(seller, "P01", 20);
            Cart cart = carts.get(0);
            cart.add("P01", 20);
            catalog.changeStock(seller, "P01", 19);
            check(cart.stockProblems().get(0).contains("库存不足"));
            reject(() -> checkout.quote(cart));
            check(cart.lines().get(0).quantity() == 20);
            cart.update("P01", 0);
            check(cart.isEmpty() && catalog.stock("P01") == 19);
            System.out.println("PASS: " + checks + " shared-stock/cart checks");
        } finally {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Cart stock check failed: " + checks);
    }

    private static void reject(Runnable action) {
        checks++;
        try { action.run(); }
        catch (IllegalArgumentException e) {
            if (!e.getMessage().contains("请先删除")) throw new AssertionError(e);
            return;
        }
        throw new AssertionError("Checkout should require removing unavailable items");
    }
}
