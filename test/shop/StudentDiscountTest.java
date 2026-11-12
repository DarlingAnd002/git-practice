package shop;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
public final class StudentDiscountTest {
    private static int checks;
    private static void check(boolean result) {checks++;if (!result) throw new AssertionError("student check " + checks);}
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("build"), "student-");
        try {
            AuthService auth = new AuthService(dir.resolve("users"));
            auth.register("seller_a", "Password123".toCharArray(), Role.SELLER);auth.register("seller_b", "Password123".toCharArray(), Role.SELLER);auth.register("buyer", "Password123".toCharArray(), Role.CONSUMER);
            var a = auth.login("seller_a", "Password123".toCharArray());var b = auth.login("seller_b", "Password123".toCharArray());var buyer = auth.login("buyer", "Password123".toCharArray());
            Path file = dir.resolve("store");Catalog c = new Catalog(file);String x = c.add(a, "A", new BigDecimal("100.00"), 10).id();String y = c.add(b, "B", new BigDecimal("0.05"), 10).id();Cart cart = new Cart(c, buyer);cart.add(x, 1);cart.add(y, 1);
            Checkout checkout = new Checkout();var regular = checkout.quote(cart);var student = checkout.quote(cart, true);
            check(regular.total().equals(new BigDecimal("100.05")) && !regular.student());check(student.total().equals(new BigDecimal("90.05")) && student.student());check(cart.lines().size() == 2 && c.stock(x) == 10);
            try {c.purchase(buyer, cart, new Checkout.Receipt(student.lines(), BigDecimal.ONE, true));throw new AssertionError("forged quote");}catch (IllegalArgumentException expected) {checks++;}
            check(c.purchases(buyer).isEmpty() && cart.lines().size() == 2);
            List<Order> orders = c.purchase(buyer, cart, student);check(orders.size() == 2 && cart.isEmpty());check(orders.get(0).total().equals(new BigDecimal("90.00")));check(orders.get(1).total().equals(new BigDecimal("0.05")));
            check(orders.stream().map(Order::total).reduce(BigDecimal.ZERO, BigDecimal::add).equals(student.total()));check(orders.get(0).items().get(0).unitPrice().equals(new BigDecimal("100.00")));check(c.stock(x) == 9 && c.stock(y) == 9);
            Catalog loaded = new Catalog(file);check(loaded.purchases(buyer).equals(orders));check(loaded.sales(a).get(0).total().equals(new BigDecimal("90.00")));
            cart.add(x, 1);var stale = checkout.quote(cart, true);c.updateProduct(a, x, "A", new BigDecimal("200.00"), 9);
            try {c.purchase(buyer, cart, stale);throw new AssertionError("stale student quote");}catch (IllegalArgumentException expected) {checks++;}
            check(cart.lines().size() == 1 && c.stock(x) == 9);check(checkout.quote(cart).total().equals(new BigDecimal("200.00")));
            System.out.println("PASS: " + checks + " student-discount checks");
        } finally {try (var paths = Files.walk(dir)) {for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);}}
    }
}
