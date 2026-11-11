package shop;

import java.util.Locale;
import java.util.Scanner;

/** 控制台交互层：组织菜单、读取输入并调用各业务模块。 */
public final class Main {
    // 同一轮程序共享一个目录和购物车，便于连续进行多次购物操作。
    private final Catalog catalog = new Catalog();
    private final Cart cart = new Cart(catalog);
    private final Checkout checkout = new Checkout();
    private final Scanner input = new Scanner(System.in);

    public static void main(String[] args) { new Main().run(); }

    /** 持续显示菜单，直到用户选择退出或输入流结束。 */
    private void run() {
        System.out.println("Campus Corner | Java team shopping demo | CNY");
        System.out.println("Discount: subtotal >= 100 saves 10, then STUDENT10 saves 10%.");
        try {
            while (true) {
                System.out.println("\n1 Products  2 Add  3 Cart  4 Update/remove  5 Checkout  0 Exit");
                try {
                // 箭头式 switch 每次只执行对应选项，无须手动写 break。
                switch (read("Choice: ")) {
                    case "1" -> catalog.products().forEach(p ->
                            System.out.printf("%s  %-14s CNY %s%n", p.id(), p.name(), p.price()));
                    case "2" -> {
                        // 编号统一转为大写；ROOT 避免大小写转换受系统语言影响。
                        cart.add(read("Product ID: ").toUpperCase(Locale.ROOT), number("Quantity: "));
                        System.out.println("Added.");
                    }
                    case "3" -> showCart();
                    case "4" -> {
                        cart.update(read("Product ID: ").toUpperCase(Locale.ROOT), number("New quantity (0 removes): "));
                        System.out.println("Updated.");
                    }
                    case "5" -> pay();
                    case "0" -> { System.out.println("Goodbye."); return; }
                    default -> System.out.println("Choose 0 to 5.");
                }
                // 只处理预期的输入错误，提示后继续菜单，让用户重新操作。
                } catch (IllegalArgumentException e) {
                    System.out.println("Cannot complete: " + e.getMessage());
                }
            }
        } catch (EndOfInput e) {
            System.out.println("\nInput closed. Goodbye.");
        }
    }

    /** 按整行读取并去除首尾空白；无输入时通知外层正常退出。 */
    private String read(String prompt) {
        System.out.print(prompt);
        if (!input.hasNextLine()) throw new EndOfInput();
        return input.nextLine().trim();
    }

    /** 将输入转换为整数；非数字或超出 int 范围时给出统一错误提示。 */
    private int number(String prompt) {
        try { return Integer.parseInt(read(prompt)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Enter a whole number."); }
    }

    /** 展示每项商品的小计，以及未扣除优惠的总价。 */
    private void showCart() {
        if (cart.isEmpty()) System.out.println("Cart is empty.");
        cart.lines().forEach(l -> System.out.printf("%s %s x%d = CNY %s%n",
                l.product().id(), l.product().name(), l.quantity(), l.subtotal()));
        System.out.println("Subtotal: CNY " + cart.subtotal());
    }

    /** 先显示报价，再确认结账；只有确认成功才清空购物车。 */
    private void pay() {
        if (cart.isEmpty()) throw new IllegalArgumentException("Cart is empty.");
        // 报价不会修改购物车，所以输入无效优惠码或取消结账都不会丢失商品。
        Checkout.Receipt receipt = checkout.quote(cart, read("Coupon (blank for none): ").toUpperCase(Locale.ROOT));
        showCart();
        System.out.printf("Threshold discount: %s%nCoupon discount: %s%nTotal: CNY %s%n",
                receipt.thresholdDiscount(), receipt.couponDiscount(), receipt.total());
        if (read("Confirm simulated checkout? (y/N): ").equalsIgnoreCase("y")) {
            cart.clear();
            System.out.println("Checkout complete (simulation). Cart cleared.");
        } else System.out.println("Cancelled. Cart retained.");
    }

    /** 内部控制信号：输入流结束时跳出菜单循环。 */
    private static final class EndOfInput extends RuntimeException { }
}
