package shop;

import java.math.BigDecimal;

/** No external test dependency; failures always exit nonzero. */
public final class ShopTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Catalog catalog = new Catalog();
        // 商品必须有真实登录销售者；测试固定商品显式创建，不污染实际商店。
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("build"), "shop-fixture-");
        java.nio.file.Path file = dir.resolve("users.properties");
        AuthService auth = new AuthService(file);
        auth.register("fixture", "Password123".toCharArray(), Role.SELLER);
        AuthService.Session seller = auth.login("fixture", "Password123".toCharArray());
        catalog.add(seller, new Product("P01", "Notebook", new BigDecimal("18.00")), 1000);
        catalog.add(seller, new Product("P02", "Pen set", new BigDecimal("12.50")), 1000);
        java.nio.file.Files.delete(file);
        java.nio.file.Files.delete(dir);
        Cart cart = new Cart(catalog);
        Checkout checkout = new Checkout();
        rejects(() -> checkout.quote(cart, ""));
        rejects(() -> cart.add("missing", 1));
        rejects(() -> cart.add("P01", -1));
        rejects(() -> cart.add("P01", 0));
        rejects(() -> cart.add("P01", Integer.MAX_VALUE));
        cart.add("P02", 8); // Exactly 100
        money("100.00", cart.subtotal());
        Checkout.Receipt receipt = checkout.quote(cart, "STUDENT10");
        money("10.00", receipt.thresholdDiscount());
        money("9.00", receipt.couponDiscount());
        money("81.00", receipt.total());
        money("90.00", checkout.quote(cart, "").total());
        rejects(() -> checkout.quote(cart, "INVALID"));
        money("100.00", cart.subtotal()); // Quoting does not clear the cart
        cart.update("P02", 7);
        money("0.00", checkout.quote(cart, "").thresholdDiscount());
        cart.update("P02", 1);
        money("11.25", checkout.quote(cart, "STUDENT10").total());
        cart.add("P02", 98);
        rejects(() -> cart.add("P02", 1));
        money("1237.50", cart.subtotal());
        rejects(() -> cart.update("P02", -1));
        cart.update("P02", 0);
        money("0.00", cart.subtotal());
        cart.add("P01", 1);
        cart.clear();
        money("0.00", cart.subtotal());
        money("81.00", receipt.total()); // Receipt remains a snapshot
        cart.add("P02", 1);
        cart.add("P01", 1);
        money("30.50", cart.subtotal());
        System.out.println("PASS: " + checks + " checks");
    }
    private static void money(String expected, BigDecimal actual) {
        checks++;
        if (new BigDecimal(expected).compareTo(actual) != 0)
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void rejects(Runnable action) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected input rejection");
    }
}
