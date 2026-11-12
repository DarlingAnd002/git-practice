package shop;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

public final class SelectionCheckoutTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("build"), "selection-checkout-");
        try {
            AuthService auth = new AuthService(dir.resolve("users.properties"));
            AuthService.Session seller = user(auth, "seller", Role.SELLER);
            AuthService.Session buyer = user(auth, "buyer", Role.CONSUMER);
            AuthService.Session other = user(auth, "other", Role.CONSUMER);
            Catalog catalog = new Catalog(dir.resolve("store.properties"));
            String a = catalog.add(seller, "Pen", new BigDecimal("12.00"), 10).id();
            String b = catalog.add(seller, "Book", new BigDecimal("5.00"), 20).id();
            Cart cart = new Cart(catalog, buyer);
            cart.add(a, 2);
            cart.add(b, 3);
            reject(() -> cart.selected(buyer, List.of()));
            reject(() -> cart.selected(other, List.of(a)));
            reject(() -> cart.selected(seller, List.of(a)));
            reject(() -> cart.selected(buyer, List.of("missing")));
            Cart selected = cart.selected(buyer, List.of(a, a));
            check(selected.lines().size() == 1 && selected.subtotal().compareTo(new BigDecimal("24.00")) == 0);
            Checkout.Receipt quote = new Checkout().quote(selected);
            check(cart.lines().size() == 2 && catalog.stock(a) == 10);
            Checkout.Receipt firstQuote = quote;
            reject(() -> catalog.purchaseSelected(other, cart, List.of(a), firstQuote));
            Order order = catalog.purchaseSelected(buyer, cart, List.of(a), quote).get(0);
            check(order.items().size() == 1 && order.total().compareTo(new BigDecimal("24.00")) == 0);
            check(cart.lines().size() == 1 && cart.lines().get(0).product().id().equals(b));
            check(cart.lines().get(0).quantity() == 3 && catalog.stock(b) == 20 && catalog.stock(a) == 8);
            cart.add(a, 1);
            quote = new Checkout().quote(cart.selected(buyer, List.of(a)));
            Checkout.Receipt oldPrice = quote;
            catalog.changePrice(seller, a, new BigDecimal("13.00"));
            reject(() -> catalog.purchaseSelected(buyer, cart, List.of(a), oldPrice));
            check(cart.lines().size() == 2 && catalog.stock(a) == 8 && catalog.purchases(buyer).size() == 1);
            Checkout.Receipt current = new Checkout().quote(cart.selected(buyer, List.of(a)));
            cart.update(a, 2);
            reject(() -> catalog.purchaseSelected(buyer, cart, List.of(a), current));
            check(cart.lines().get(1).quantity() == 2 && catalog.stock(a) == 8);
            catalog.changeStock(seller, b, 0);
            reject(() -> cart.selected(buyer, List.of(a, b)));
            check(cart.lines().size() == 2 && catalog.stock(a) == 8);
            // 未勾选的售罄商品不影响购买其他商品，也不会被自动删除。
            Checkout.Receipt onlyA = new Checkout().quote(cart.selected(buyer, List.of(a)));
            catalog.purchaseSelected(buyer, cart, List.of(a), onlyA);
            check(cart.lines().size() == 1 && cart.lines().get(0).product().id().equals(b));
            check(cart.stockProblems().get(0).contains("Sold out"));
            check(catalog.stock(a) == 6 && catalog.purchases(buyer).size() == 2);
            catalog.changeStock(seller, b, 10);
            Checkout.Receipt last = new Checkout().quote(cart.selected(buyer, List.of(b)));
            catalog.purchaseSelected(buyer, cart, List.of(b), last);
            check(cart.isEmpty() && catalog.stock(b) == 7);
            Catalog loaded = new Catalog(dir.resolve("store.properties"));
            check(loaded.purchases(buyer).size() == 3 && loaded.stock(a) == 6 && loaded.stock(b) == 7);
            System.out.println("PASS: " + checks + " checked-checkout checks");
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
        if (!value) throw new AssertionError("Selection check failed: " + checks);
    }
    private static void reject(Runnable action) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection: " + checks);
    }
}
