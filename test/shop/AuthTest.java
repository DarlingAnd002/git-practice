package shop;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.Comparator;

/** 真实临时账号文件测试：密码、角色、持久化、越权与注销。 */
public final class AuthTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("build"), "shopping-auth-test-");
        try {
            Path file = dir.resolve("users.properties");
            AuthService auth = new AuthService(file);
            auth.register("Seller", "Password123".toCharArray(), Role.SELLER);
            auth.register("Buyer", "Password123".toCharArray(), Role.CONSUMER);
            rejects(() -> auth.register("seller", "OtherPass123".toCharArray(), Role.CONSUMER));
            rejects(() -> auth.register("bad name", "Password123".toCharArray(), Role.SELLER));
            rejects(() -> auth.register("short", "123".toCharArray(), Role.SELLER));
            rejects(() -> auth.login("seller", "wrong".toCharArray()));
            rejects(() -> auth.login("unknown", "Password123".toCharArray()));
            rejects(() -> auth.login("seller", null));
            // 重新构造服务模拟程序重启，账号和角色仍可读取。
            AuthService loaded = new AuthService(file);
            AuthService.Session seller = loaded.login("SELLER", "Password123".toCharArray());
            AuthService.Session buyer = loaded.login("buyer", "Password123".toCharArray());
            check(seller.role() == Role.SELLER);
            check(buyer.role() == Role.CONSUMER);
            check(seller.username().equals("seller"));
            String stored = Files.readString(file);
            check(stored.contains("Password123"));
            java.util.Properties saved = new java.util.Properties();
            try (var reader = Files.newBufferedReader(file)) { saved.load(reader); }
            check(saved.getProperty("seller").equals("SELLER:PLAIN:Password123"));
            Catalog catalog = new Catalog();
            Product product = new Product("P05", "Book", new BigDecimal("20.00"));
            rejects(() -> catalog.add(null, product));
            rejects(() -> catalog.add(buyer, product));
            rejects(() -> catalog.changePrice(buyer, "P01", new BigDecimal("1.00")));
            catalog.add(seller, product);
            check(catalog.find("P05").equals(product));
            rejects(() -> catalog.add(seller, product));
            Cart cart = new Cart(catalog);
            cart.add("P05", 2);
            catalog.changePrice(seller, "P05", new BigDecimal("30.00"));
            check(cart.subtotal().compareTo(new BigDecimal("60.00")) == 0);
            rejects(() -> catalog.changePrice(seller, "P05", new BigDecimal("-1.00")));
            check(catalog.find("P05").price().compareTo(new BigDecimal("30.00")) == 0);
            seller.logout();
            rejects(() -> catalog.changePrice(seller, "P05", BigDecimal.ONE));
            buyer.logout();
            rejects(() -> buyer.require(Role.CONSUMER));
            byte[] salt = new byte[16];
            javax.crypto.spec.PBEKeySpec key = new javax.crypto.spec.PBEKeySpec("LegacyPass123".toCharArray(), salt, 600_000, 256);
            byte[] digest = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).getEncoded();
            key.clearPassword();
            java.util.Properties legacy = new java.util.Properties();
            legacy.setProperty("olduser", "CONSUMER:" + java.util.Base64.getEncoder().encodeToString(salt)
                    + ":" + java.util.Base64.getEncoder().encodeToString(digest));
            Path oldFile = dir.resolve("legacy.properties");
            try (var writer = Files.newBufferedWriter(oldFile)) { legacy.store(writer, "Legacy fixture"); }
            AuthService migrated = new AuthService(oldFile);
            rejects(() -> migrated.login("olduser", "wrong".toCharArray()));
            check(!Files.readString(oldFile).contains("PLAIN"));
            check(migrated.login("olduser", "LegacyPass123".toCharArray()).role() == Role.CONSUMER);
            java.util.Properties converted = new java.util.Properties();
            try (var reader = Files.newBufferedReader(oldFile)) { converted.load(reader); }
            check(converted.getProperty("olduser").equals("CONSUMER:PLAIN:LegacyPass123"));
            check(new AuthService(oldFile).login("olduser", "LegacyPass123".toCharArray()).role() == Role.CONSUMER);
            auth.register("symbols", "  abc:def=中文  ".toCharArray(), Role.CONSUMER);
            check(new AuthService(file).login("symbols", "  abc:def=中文  ".toCharArray()).role() == Role.CONSUMER);
            System.out.println("PASS: " + checks + " auth/role checks");
        } finally {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Auth check failed: " + checks);
    }
    private static void rejects(Action action) throws Exception {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection: " + checks);
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
}
