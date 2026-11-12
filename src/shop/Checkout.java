package shop;

import java.math.BigDecimal;
import java.util.List;

/** 检查库存并计算应付金额。 */
public final class Checkout {
    public record Receipt(List<Cart.Line> lines, BigDecimal total) {
        public Receipt {
            lines = List.copyOf(lines);
        }
    }

    public Receipt quote(Cart cart) {
        if (cart.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty.");
        }
        List<String> problems = cart.stockProblems();
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("请先删除购物车中不可购买的商品，再结账：\n"
                    + String.join("\n", problems));
        }
        List<Cart.Line> lines = cart.lines();
        BigDecimal total = new BigDecimal("0.00");
        // 加购物车时不扣库存，结账前要重新查一次。
        for (Cart.Line line : lines) {
            cart.checkStock(line.product().id(), line.quantity());
            total = total.add(line.subtotal());
        }
        return new Receipt(lines, total);
    }
}
