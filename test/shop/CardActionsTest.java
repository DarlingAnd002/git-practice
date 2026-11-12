package shop;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

public final class CardActionsTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("build"), "card-actions-");
        try {
            AuthService auth = new AuthService(dir.resolve("users.properties"));
            AuthService.Session seller = user(auth, "seller", Role.SELLER);
            AuthService.Session other = user(auth, "other", Role.SELLER);
            AuthService.Session buyer = user(auth, "buyer", Role.CONSUMER);
            Path file = dir.resolve("store.properties");
            Catalog catalog = new Catalog(file);
            String a = catalog.add(seller, "Pen", BigDecimal.ONE, 10).id();
            String b = catalog.add(seller, "Book", BigDecimal.TEN, 2).id();
            String foreign = catalog.add(other, "Other", BigDecimal.ONE, 5).id();
            reject(() -> catalog.setActive(buyer, List.of(a, b), false));
            reject(() -> catalog.setActive(seller, List.of(a, foreign), false));
            check(catalog.isListed(a) && catalog.isListed(foreign));
            catalog.setActive(seller, List.of(a, b), false);
            check(!catalog.isListed(a) && !catalog.isListed(b));
            check(catalog.stock(a) == 10 && catalog.stock(b) == 2);
            catalog.setActive(seller, List.of(a, b), true);
            check(catalog.isListed(a) && catalog.isListed(b));
            check(new Catalog(file).isListed(a));
            Cart cart = new Cart(catalog, buyer);
            reject(() -> cart.addAll(List.of(a, b), 3));
            check(cart.isEmpty());
            cart.add(a, 1);
            reject(() -> cart.addAll(List.of(a, b), 3));
            check(cart.lines().size() == 1 && cart.lines().get(0).quantity() == 1);
            cart.addAll(List.of(a, b, a), 2);
            check(cart.lines().size() == 2 && cart.lines().get(0).quantity() == 3 && cart.lines().get(1).quantity() == 2);
            check(catalog.stock(a) == 10 && catalog.stock(b) == 2);
            reject(() -> cart.addAll(List.of(a, "missing"), 1));
            check(cart.lines().get(0).quantity() == 3);
            catalog.setActive(seller, List.of(b), false);
            reject(() -> cart.addAll(List.of(a, b), 1));
            check(cart.lines().get(0).quantity() == 3);
            catalog.setActive(seller, List.of(b), true);
            Checkout.Receipt quote = new Checkout().quote(cart);
            catalog.purchaseSelected(buyer, cart, List.of(a, b), quote);
            check(cart.isEmpty() && catalog.stock(a) == 7 && catalog.stock(b) == 0);
            check(catalog.purchases(buyer).size() == 1 && catalog.seller(a).equals("seller"));
            System.out.println("PASS: " + checks + " batch-card-action checks");
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
    private static void check(boolean value) {checks++;if (!value) throw new AssertionError("Card action check " + checks);}
    private static void reject(Runnable action) {
        checks++;try {action.run();}catch (IllegalArgumentException expected) {return;}
        throw new AssertionError("Expected rejection: " + checks);
    }
}
