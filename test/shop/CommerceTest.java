package shop;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.io.UncheckedIOException;

/** 验证归属、库存、购买事务、订单隔离和持久化，而不仅是菜单显示。 */
public final class CommerceTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("build"), "commerce-test-");
        try {
            AuthService auth = new AuthService(dir.resolve("users.properties"));
            AuthService.Session a = user(auth, "seller_a", Role.SELLER);
            AuthService.Session b = user(auth, "seller_b", Role.SELLER);
            AuthService.Session buyer = user(auth, "buyer_a", Role.CONSUMER);
            AuthService.Session other = user(auth, "buyer_b", Role.CONSUMER);
            Path file = dir.resolve("store.properties");
            Catalog c = new Catalog(file);
            Product pen = new Product("P01", "Pen", money("12.50"));
            c.add(a, pen, 8);
            c.add(b, new Product("P02", "Book", money("9.99")), 2);
            check(c.mine(a).size() == 1 && c.mine(a).get(0).product().id().equals("P01"));
            check(c.mine(b).size() == 1 && c.mine(b).get(0).product().id().equals("P02"));
            reject(() -> c.changePrice(b, "P01", money("1.00")), "自己的商品");
            reject(() -> c.changeStock(b, "P01", 100), "自己的商品");
            reject(() -> c.changeStock(buyer, "P01", 100), "权限");
            reject(() -> c.add(a, pen, 1), "已存在");
            reject(() -> c.changeStock(a, "P01", -1), "库存");
            reject(() -> c.changeStock(a, "P01", Catalog.MAX_STOCK + 1), "库存");
            Cart cart = new Cart(c, buyer);
            reject(() -> cart.add("P01", 9), "库存不足");
            check(cart.isEmpty());
            cart.add("P01", 7);
            reject(() -> cart.add("P01", 2), "库存不足");
            check(cart.lines().get(0).quantity() == 7);
            reject(() -> cart.update("P01", 9), "库存不足");
            cart.update("P01", 8);
            check(c.stock("P01") == 8); // 加购不扣库存，未确认不会产生订单。
            check(c.sales(a).isEmpty() && c.purchases(buyer).isEmpty());
            cart.add("P02", 1);
            Checkout checkout = new Checkout();
            Checkout.Receipt quote = checkout.quote(cart);
            check(quote.total().compareTo(money("109.99")) == 0);
            reject(() -> c.purchase(other, cart, quote), "他人的购物车");
            reject(() -> c.purchase(a, cart, quote), "权限");
            check(c.stock("P01") == 8 && c.purchases(buyer).isEmpty());
            List<Order> created = c.purchase(buyer, cart, quote);
            BigDecimal combined = created.stream().map(Order::total).reduce(BigDecimal.ZERO, BigDecimal::add);
            check(cart.isEmpty());
            check(c.stock("P01") == 0 && c.stock("P02") == 1);
            check(combined.compareTo(quote.total()) == 0);
            check(c.purchases(buyer).size() == 2 && c.purchases(other).isEmpty());
            check(c.sales(a).size() == 1 && c.sales(a).get(0).items().size() == 1);
            check(c.sales(b).get(0).items().get(0).productId().equals("P02"));
            check(c.sales(a).get(0).total().add(c.sales(b).get(0).total()).compareTo(combined) == 0);
            for (Order.Item item : created.stream().flatMap(order -> order.items().stream()).toList()) check(item.paid().compareTo(item.subtotal()) == 0);
            reject(() -> c.purchase(buyer, cart, quote), "empty");
            reject(() -> cart.add("P01", 1), "Sold out");
            check(cart.isEmpty());
            reject(() -> c.purchases(a), "权限");
            reject(() -> c.sales(buyer), "权限");
            Catalog reloaded = new Catalog(file);
            check(reloaded.stock("P01") == 0 && reloaded.stock("P02") == 1);
            check(reloaded.purchases(buyer).equals(c.purchases(buyer)));
            check(reloaded.sales(a).equals(c.sales(a)));
            // 旧订单按原记录显示，修改计价方式不重算历史金额。
            Path legacyFile = dir.resolve("legacy.properties");
            Order legacy = new Order("legacy-order", buyer.username(), java.time.Instant.now(),
                    List.of(new Order.Item(a.username(), "P01", "Pen", money("12.50"), 8, money("81.00"))));
            new StoreFile(legacyFile).save(new StoreFile.State(Map.of(), List.of(legacy)));
            Catalog legacyStore = new Catalog(legacyFile);
            check(legacyStore.purchases(buyer).get(0).total().compareTo(money("81.00")) == 0);
            check(legacyStore.sales(a).get(0).total().compareTo(money("81.00")) == 0);
            c.changePrice(b, "P02", money("25.00"));
            check(c.purchases(buyer).get(1).items().get(0).unitPrice().compareTo(money("9.99")) == 0);

            // 已加购后别的消费者买走库存，整次购买失败，其他商品也不能被部分扣库。
            c.changeStock(a, "P01", 1);
            Cart stale = new Cart(c, buyer);
            stale.add("P01", 1);
            stale.add("P02", 1);
            Checkout.Receipt staleQuote = checkout.quote(stale);
            Cart winner = new Cart(c, other);
            winner.add("P01", 1);
            c.purchase(other, winner, checkout.quote(winner));
            int count = c.purchases(buyer).size();
            reject(() -> c.purchase(buyer, stale, staleQuote), "Sold out");
            check(c.stock("P02") == 1 && stale.lines().size() == 2 && c.purchases(buyer).size() == count);
            stale.update("P01", 0); // 售罄项仍可删除。
            check(stale.lines().size() == 1);
            Checkout.Receipt oldPrice = checkout.quote(stale);
            c.changePrice(b, "P02", money("30.00"));
            reject(() -> c.purchase(buyer, stale, oldPrice), "重新确认报价");
            check(c.stock("P02") == 1 && !stale.isEmpty());

            // 强制制造存储失败，确认失败不会扣库存、生成订单或清空购物车。
            byte[] saved = Files.readAllBytes(file);
            Files.delete(file);
            Files.createDirectory(file);
            Files.writeString(file.resolve("block"), "nonempty directory");
            checks++;
            try { c.purchase(buyer, stale, checkout.quote(stale)); throw new AssertionError("Expected save failure"); }
            catch (UncheckedIOException expected) { }
            check(c.stock("P02") == 1 && c.purchases(buyer).size() == count && !stale.isEmpty());
            Files.delete(file.resolve("block"));
            Files.delete(file);
            Files.write(file, saved);
            check(new Catalog(file).stock("P02") == 1);
            c.purchase(buyer, stale, checkout.quote(stale));
            check(c.stock("P02") == 0 && stale.isEmpty());
            // 两个消费者均已加购最后一件商品，也只能有一个购买成功。
            c.changeStock(a, "P01", 1);
            Cart first = new Cart(c, buyer);
            Cart second = new Cart(c, other);
            first.add("P01", 1);
            second.add("P01", 1);
            Checkout.Receipt q1 = checkout.quote(first);
            Checkout.Receipt q2 = checkout.quote(second);
            var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var f1 = executor.submit(() -> attempt(c, buyer, first, q1));
                var f2 = executor.submit(() -> attempt(c, other, second, q2));
                check((f1.get() ? 1 : 0) + (f2.get() ? 1 : 0) == 1);
                check(c.stock("P01") == 0);
                check(first.isEmpty() != second.isEmpty());
            } finally { executor.shutdownNow(); }
            a.logout();
            reject(() -> c.sales(a), "权限");
            reject(() -> c.changeStock(a, "P01", 2), "权限");
            buyer.logout();
            reject(() -> c.purchases(buyer), "权限");
            System.out.println("PASS: " + checks + " ownership/inventory/order checks");
        } finally {
            try (var paths = Files.walk(dir)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }
    private static AuthService.Session user(AuthService auth, String name, Role role) throws Exception {
        auth.register(name, "Password123".toCharArray(), role);
        return auth.login(name, "Password123".toCharArray());
    }
    private static boolean attempt(Catalog c, AuthService.Session session, Cart cart, Checkout.Receipt quote) {
        try { c.purchase(session, cart, quote); return true; }
        catch (IllegalArgumentException e) {
            if (!e.getMessage().contains("Sold out")) throw e;
            return false;
        }
    }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Commerce check failed: " + checks);
    }
    private static void reject(Runnable action, String message) {
        checks++;
        try { action.run(); }
        catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains(message)) throw new AssertionError(expected);
            return;
        }
        throw new AssertionError("Expected rejection: " + message);
    }
}
