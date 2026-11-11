package shop;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** 注册、登录和账号保存。 */
public final class AuthService {
    private static final int ITERATIONS = 600_000;
    private final Path file;
    private final Properties accounts = new Properties();

    public static final class Session {
        private final String username;
        private final Role role;
        private boolean active = true;
        private Session(String username, Role role) {
            this.username = username;
            this.role = role;
        }
        public String username() {
            return username;
        }
        public Role role() {
            return role;
        }
        public void logout() {
            active = false;
        }
        public void require(Role required) {
            if (!active || role != required) throw new IllegalArgumentException("当前账号无此权限，请重新登录正确角色。");
        }
    }

    public AuthService(Path file) throws IOException {
        this.file = file.toAbsolutePath();
        if (Files.exists(this.file)) {
            try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) { accounts.load(reader); }
        }
    }

    public void register(String username, char[] password, Role role) throws IOException {
        username = normalize(username);
        if (password == null || password.length < 8 || password.length > 128)
            throw new IllegalArgumentException("密码长度必须为 8–128 个字符。");
        if (role == null) throw new IllegalArgumentException("请选择角色。");
        if (accounts.containsKey(username)) throw new IllegalArgumentException("账号已存在。");
        // 相同密码也使用不同的盐，不直接保存密码。
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String entry = role.name() + ":" + Base64.getEncoder().encodeToString(salt)
                + ":" + Base64.getEncoder().encodeToString(hash(password, salt));
        Properties updated = new Properties();
        updated.putAll(accounts);
        updated.setProperty(username, entry);
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "users-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                updated.store(writer, "Local classroom accounts: role, salt, PBKDF2 hash");
            }

            // 写好临时文件后再替换，防止写到一半把旧数据弄坏。
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            accounts.setProperty(username, entry);
        } finally { Files.deleteIfExists(temp); }
    }

    public Session login(String username, char[] password) {
        username = normalize(username);
        if (password == null || password.length > 128) throw new IllegalArgumentException("账号或密码错误。");
        String entry = accounts.getProperty(username);
        String[] fields = entry == null ? null : entry.split(":");

        // 账号不存在时也算一次摘要，不在提示中区分账号和密码错误。
        byte[] salt = fields == null ? new byte[16] : Base64.getDecoder().decode(fields[1]);
        byte[] actual = hash(password, salt);
        if (fields == null || !MessageDigest.isEqual(actual, Base64.getDecoder().decode(fields[2])))
            throw new IllegalArgumentException("账号或密码错误。");
        return new Session(username, Role.valueOf(fields[0]));
    }

    private static String normalize(String username) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,24}"))
            throw new IllegalArgumentException("账号须为 3–24 位字母、数字或下划线。");
        return username.toLowerCase(Locale.ROOT);
    }

    private static byte[] hash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("无法计算密码摘要。", e); }
        finally { spec.clearPassword(); }
    }
}
