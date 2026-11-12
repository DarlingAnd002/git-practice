package shop;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.io.UncheckedIOException;

public final class MerchantOrderTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("build"), "merchant-orders-");
        try {
            AuthService auth = new AuthService(dir.resolve("users.properties"));
            AuthService.Session a = user(auth, "seller_a", Role.SELLER);
            AuthService.Session b = user(auth, "seller_b", Role.SELLER);
            AuthService.Session buyer = user(auth, "buyer", Role.CONSUMER);
            Path file = dir.resolve("store.properties");
            Catalog c = new Catalog(file);
            String first = c.add(a, "A1", new BigDecimal("12.00"), 10).id();
            String second = c.add(a, "A2", new BigDecimal("3.00"), 10).id();
            String third = c.add(b, "B1", new BigDecimal("5.00"), 10).id();
            Cart cart = new Cart(c, buyer);
            cart.addAll(List.of(first, second, third), 1);
            Checkout.Receipt quote = new Checkout().quote(cart);
            byte[] saved = Files.readAllBytes(file);
            Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("block"), "test");
            checks++;
            try { c.purchase(buyer, cart, quote);throw new AssertionError("Expected storage failure"); }
            catch (UncheckedIOException expected) { }
            check(c.purchases(buyer).isEmpty() && c.sales(a).isEmpty() && c.sales(b).isEmpty());
            check(c.stock(first) == 10 && c.stock(second) == 10 && c.stock(third) == 10 && cart.lines().size() == 3);
            Files.delete(file.resolve("block"));Files.delete(file);Files.write(file, saved);
            List<Order> created = c.purchaseSelected(buyer, cart, List.of(first, second, third), quote);
            check(created.size() == 2 && cart.isEmpty());
            check(!created.get(0).id().equals(created.get(1).id()));
            check(created.get(0).items().size() == 2 && created.get(1).items().size() == 1);
            check(created.get(0).total().compareTo(new BigDecimal("15.00")) == 0);
            check(created.get(1).total().compareTo(new BigDecimal("5.00")) == 0);
            check(created.stream().map(Order::total).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(quote.total()) == 0);
            for (Order order : created) check(order.items().stream().map(Order.Item::seller).distinct().count() == 1);
            check(c.sales(a).size() == 1 && c.sales(a).get(0).id().equals(created.get(0).id()));
            check(c.sales(b).size() == 1 && c.sales(b).get(0).id().equals(created.get(1).id()));
            check(c.purchases(buyer).size() == 2 && c.stock(first) == 9 && c.stock(second) == 9 && c.stock(third) == 9);
            Catalog loaded = new Catalog(file);
            check(loaded.purchases(buyer).equals(c.purchases(buyer)));
            check(loaded.sales(a).equals(c.sales(a)) && loaded.sales(b).equals(c.sales(b)));
            cart.add(first, 1);cart.add(third, 2);
            Checkout.Receipt onlyB = new Checkout().quote(cart.selected(buyer, List.of(third)));
            List<Order> last = c.purchaseSelected(buyer, cart, List.of(third), onlyB);
            check(last.size() == 1 && last.get(0).items().get(0).seller().equals("seller_b"));
            check(cart.lines().size() == 1 && cart.lines().get(0).product().id().equals(first));
            check(c.stock(first) == 9 && c.stock(third) == 7 && c.purchases(buyer).size() == 3);
            check(!last.get(0).id().equals(created.get(1).id()));
            System.out.println("PASS: " + checks + " merchant-order checks");
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
    private static void check(boolean value) {checks++;if (!value) throw new AssertionError("Merchant order check " + checks);}
}
