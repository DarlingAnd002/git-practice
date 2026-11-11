package shop;

import java.math.BigDecimal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 控制台菜单。 */
public final class Main {
    private Catalog catalog;
    private final Checkout checkout = new Checkout();
    private final Scanner input = new Scanner(System.in);

    private final Map<String, Cart> carts = new HashMap<>();
    private AuthService auth;

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        System.out.println("注册 → 登录 → 按角色操作");
        try (StoreLock lock = new StoreLock(Path.of("data", "store.lock"))) {
            auth = new AuthService(Path.of("data", "users.properties"));
            catalog = new Catalog(Path.of("data", "store.properties"));
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
        catch (IOException e) { System.out.println("无法打开商店：" + e.getMessage()); }
    }

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
        } finally {
            session.logout();
        }
    }

    private void sellerMenu(AuthService.Session session) {
        while (true) {
            System.out.println("\n销售者：1 我的商品  2 添加商品  3 修改价格  4 设置库存  5 我的订单  0 退出登录");
            try {
                session.require(Role.SELLER);
                switch (read("请选择：")) {
                    case "1" -> showListings(catalog.mine(session));
                    case "2" -> {
                        String id = read("新商品编号：").toUpperCase(Locale.ROOT);
                        String name = read("商品名称：");
                        Product product = new Product(id, name, price());
                        catalog.add(session, product, number("初始库存（0–1000000）："));
                        System.out.println("商品添加成功。");
                    }
                    case "3" -> {
                        String id = read("商品编号：").toUpperCase(Locale.ROOT);
                        catalog.changePrice(session, id, price());
                        System.out.println("价格修改成功。");
                    }
                    case "4" -> {
                        String id = read("自己的商品编号：").toUpperCase(Locale.ROOT);
                        catalog.changeStock(session, id, number("设置可售库存（0–1000000）："));
                        System.out.println("库存修改成功。");
                    }
                    case "5" -> showOrders(catalog.sales(session), true);
                    case "0" -> { return; }
                    default -> System.out.println("请选择 0–5。");
                }
            } catch (IllegalArgumentException e) { System.out.println("操作失败：" + e.getMessage()); }
            catch (UncheckedIOException e) { System.out.println(e.getMessage()); }
        }
    }

    private void consumerMenu(AuthService.Session session) {
        Cart cart = carts.get(session.username());
        if (cart == null) {
            cart = new Cart(catalog, session);
            carts.put(session.username(), cart);
        }
        while (true) {
            System.out.println("\n消费者：1 商品列表  2 加入购物车  3 查看购物车  4 修改/删除  5 结账  6 历史购买记录  0 退出登录");
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
                    case "5" -> pay(session, cart);
                    case "6" -> showOrders(catalog.purchases(session), false);
                    case "0" -> { return; }
                    default -> System.out.println("请选择 0–6。");
                }
            } catch (IllegalArgumentException e) { System.out.println("操作失败：" + e.getMessage()); }
            catch (UncheckedIOException e) { System.out.println(e.getMessage()); }
        }
    }

    private void products() {
        showListings(catalog.listings());
    }

    private void showListings(List<Catalog.Listing> listings) {
        if (listings.isEmpty()) System.out.println("暂无商品，请由销售者添加自己的商品。");
        listings.forEach(listing -> System.out.printf("%s %s  CNY %s  销售者：%s  库存：%d%s%n",
                listing.product().id(), listing.product().name(), listing.product().price(), listing.seller(), listing.stock(),
                listing.stock() == 0 ? "  Sold out" : ""));
    }

    private void showOrders(List<Order> orders, boolean seller) {
        if (orders.isEmpty()) System.out.println("暂无订单记录。");
        DateTimeFormatter time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));
        for (Order order : orders) {
            System.out.printf("订单号：%s  时间：%s  消费者：%s  状态：已完成（模拟）%n",
                    order.id(), time.format(order.createdAt()), order.buyer());
            order.items().forEach(item -> System.out.printf("  %s %s  销售者：%s  单价：%s ×%d  原价：%s  实付：%s%n",
                    item.productId(), item.name(), item.seller(), item.unitPrice(), item.quantity(), item.subtotal(), item.paid()));
            System.out.printf("原价：%s  优惠：%s  %s：CNY %s%n", order.subtotal(),
                    order.subtotal().subtract(order.total()), seller ? "本店实收" : "订单实付", order.total());
        }
    }

    private BigDecimal price() {
        String value = read("价格（正数，最多两位小数）：");
        if (!value.matches("[0-9]{1,7}(\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException("价格最多 7 位整数和 2 位小数。");
        return new BigDecimal(value);
    }

    private void showCart(Cart cart) {
        if (cart.isEmpty()) System.out.println("购物车为空。");
        cart.lines().forEach(listing -> System.out.printf("%s %s ×%d = %s  当前库存：%d%s%n",
                listing.product().id(), listing.product().name(), listing.quantity(), listing.subtotal(), catalog.stock(listing.product().id()),
                catalog.stock(listing.product().id()) == 0 ? " Sold out" : ""));
        System.out.println("当前目录价小计：CNY " + cart.subtotal());
    }

    private void pay(AuthService.Session session, Cart cart) {
        if (cart.isEmpty()) throw new IllegalArgumentException("购物车为空。");
        System.out.println("满 100 减 10，再用 STUDENT10 打九折。");
        String coupon = read("优惠码（回车跳过）：").toUpperCase(Locale.ROOT);
        Checkout.Receipt receipt = checkout.quote(cart, coupon);
        showCart(cart);
        System.out.printf("满减：%s  优惠码减免：%s  应付：CNY %s%n",
                receipt.thresholdDiscount(), receipt.couponDiscount(), receipt.total());
        if (read("确认模拟结账？（y/N）：").equalsIgnoreCase("y")) {
            Order order = catalog.purchase(session, cart, coupon, receipt);
            System.out.println("模拟结账成功，购物车已清空。订单号：" + order.id());
        } else System.out.println("已取消，购物车保留。");
    }

    // 普通输入和密码使用同一个读取器，避免前面的读取把密码一起读走。
    private String read(String prompt) {

        if (System.console() != null) {
            String value = System.console().readLine("%s", prompt);
            if (value == null) throw new EndOfInput();
            return value.trim();
        }
        System.out.print(prompt);
        if (!input.hasNextLine()) throw new EndOfInput();
        return input.nextLine().trim();
    }

    // IDEA 控制台不能隐藏输入，终端里用 readPassword。
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

    private static String label(Role role) {
        return role == Role.SELLER ? "销售者" : "消费者";
    }

    private static final class EndOfInput extends RuntimeException { }
}
