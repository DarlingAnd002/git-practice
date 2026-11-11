package shop;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** 交互入口：未登录只能注册或登录，成功后按已保存的角色进入对应菜单。 */
public final class Main {
    private final Catalog catalog = new Catalog();
    private final Checkout checkout = new Checkout();
    private final Scanner input = new Scanner(System.in);
    // 按账号隔离购物车，同一进程内退出登录再登录可继续购物。
    private final Map<String, Cart> carts = new HashMap<>();
    private AuthService auth;

    public static void main(String[] args) { new Main().run(); }

    private void run() {
        System.out.println("校园小铺 | CNY | 注册 → 登录 → 按角色操作");
        try {
            auth = new AuthService(Path.of("data", "users.properties"));
            while (true) {
                System.out.println("\n1 注册  2 登录  0 退出");
                try {
                    switch (read("请选择：")) {
                        case "1" -> register();
                        case "2" -> login();
                        case "0" -> { return; }
                        default -> System.out.println("请选择 0–2。");
                    }
                } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
                catch (IOException e) { System.out.println("保存账号失败，请检查目录写入权限。"); }
            }
        } catch (EndOfInput e) { System.out.println("\n输入结束，程序退出。"); }
        catch (IOException e) { System.out.println("无法读取 data/users.properties，请检查账号文件。"); }
    }

    /** 注册时明确选择角色并二次确认密码；字符数组使用后清空。 */
    private void register() throws IOException {
        String username = read("账号（3–24 位字母、数字或下划线）：");
        Role role = switch (read("选择角色：1 销售者  2 消费者：")) {
            case "1" -> Role.SELLER;
            case "2" -> Role.CONSUMER;
            default -> throw new IllegalArgumentException("角色只能选择 1 或 2。");
        };
        char[] password = password("密码（8–128 位）：");
        try {
            char[] confirm = password("再次输入密码：");
            try {
                if (!Arrays.equals(password, confirm)) throw new IllegalArgumentException("两次密码不一致。");
                auth.register(username, password, role);
                System.out.println("注册成功，请登录。角色：" + label(role));
            } finally { Arrays.fill(confirm, '\0'); }
        } finally { Arrays.fill(password, '\0'); }
    }

    /** 登录只询问账号和密码，不允许用户临时指定角色。 */
    private void login() {
        String username = read("账号：");
        char[] password = password("密码：");
        AuthService.Session session;
        try { session = auth.login(username, password); }
        finally { Arrays.fill(password, '\0'); }
        System.out.println("登录成功：" + session.username() + "（" + label(session.role()) + "）");
        try {
            if (session.role() == Role.SELLER) sellerMenu(session);
            else consumerMenu(session);
        } finally { session.logout(); } // 注销后不能继续使用旧会话管理商品。
    }

    private void sellerMenu(AuthService.Session session) {
        while (true) {
            System.out.println("\n销售者：1 商品列表  2 添加商品  3 修改价格  0 退出登录");
            try {
                session.require(Role.SELLER);
                switch (read("请选择：")) {
                    case "1" -> products();
                    case "2" -> {
                        String id = read("新商品编号：").toUpperCase(Locale.ROOT);
                        String name = read("商品名称：");
                        catalog.add(session, new Product(id, name, price()));
                        System.out.println("商品添加成功。");
                    }
                    case "3" -> {
                        String id = read("商品编号：").toUpperCase(Locale.ROOT);
                        catalog.changePrice(session, id, price());
                        System.out.println("价格修改成功。");
                    }
                    case "0" -> { return; }
                    default -> System.out.println("请选择 0–3。");
                }
            } catch (IllegalArgumentException e) { System.out.println("操作失败：" + e.getMessage()); }
        }
    }

    private void consumerMenu(AuthService.Session session) {
        Cart cart = carts.computeIfAbsent(session.username(), key -> new Cart(catalog));
        while (true) {
            System.out.println("\n消费者：1 商品列表  2 加入购物车  3 查看购物车  4 修改/删除  5 结账  0 退出登录");
            try {
                session.require(Role.CONSUMER);
                switch (read("请选择：")) {
                    case "1" -> products();
                    case "2" -> {
                        cart.add(read("商品编号：").toUpperCase(Locale.ROOT), number("数量："));
                        System.out.println("已加入购物车。");
                    }
                    case "3" -> showCart(cart);
                    case "4" -> cart.update(read("商品编号：").toUpperCase(Locale.ROOT), number("新数量（0 删除）："));
                    case "5" -> pay(cart);
                    case "0" -> { return; }
                    default -> System.out.println("请选择 0–5。");
                }
            } catch (IllegalArgumentException e) { System.out.println("操作失败：" + e.getMessage()); }
        }
    }

    private void products() {
        catalog.products().forEach(p -> System.out.printf("%s  %s  CNY %s%n", p.id(), p.name(), p.price()));
    }

    private BigDecimal price() {
        String value = read("价格（正数，最多两位小数）：");
        if (!value.matches("[0-9]{1,7}(\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException("价格最多 7 位整数和 2 位小数。");
        return new BigDecimal(value);
    }

    private void showCart(Cart cart) {
        if (cart.isEmpty()) System.out.println("购物车为空。");
        cart.lines().forEach(l -> System.out.printf("%s %s ×%d = %s%n",
                l.product().id(), l.product().name(), l.quantity(), l.subtotal()));
        System.out.println("当前目录价小计：CNY " + cart.subtotal());
    }

    /** 先显示报价，确认后才清空当前消费者的购物车。 */
    private void pay(Cart cart) {
        if (cart.isEmpty()) throw new IllegalArgumentException("购物车为空。");
        System.out.println("满 100 减 10，再用 STUDENT10 打九折。");
        Checkout.Receipt receipt = checkout.quote(cart, read("优惠码（回车跳过）：").toUpperCase(Locale.ROOT));
        showCart(cart);
        System.out.printf("满减：%s  优惠码减免：%s  应付：CNY %s%n",
                receipt.thresholdDiscount(), receipt.couponDiscount(), receipt.total());
        if (read("确认模拟结账？（y/N）：").equalsIgnoreCase("y")) {
            cart.clear();
            System.out.println("模拟结账成功，购物车已清空。");
        } else System.out.println("已取消，购物车保留。");
    }

    private String read(String prompt) {
        // 有 Console 时所有输入使用同一读取器，避免 Scanner 预读吞掉密码输入。
        if (System.console() != null) {
            String value = System.console().readLine("%s", prompt);
            if (value == null) throw new EndOfInput();
            return value.trim();
        }
        System.out.print(prompt);
        if (!input.hasNextLine()) throw new EndOfInput();
        return input.nextLine().trim();
    }

    /** 系统终端隐藏密码；IDEA 控制台不支持隐藏时明确提示。不修剪密码空格。 */
    private char[] password(String prompt) {
        if (System.console() != null) {
            char[] value = System.console().readPassword("%s", prompt);
            if (value == null) throw new EndOfInput();
            return value;
        }
        System.out.print(prompt + "（当前控制台输入可见）");
        if (!input.hasNextLine()) throw new EndOfInput();
        return input.nextLine().toCharArray();
    }

    private int number(String prompt) {
        try { return Integer.parseInt(read(prompt)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("请输入整数。"); }
    }

    private static String label(Role role) { return role == Role.SELLER ? "销售者" : "消费者"; }
    private static final class EndOfInput extends RuntimeException { }
}
