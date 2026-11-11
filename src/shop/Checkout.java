package shop;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 计算满减和优惠码折扣。 */
public final class Checkout {

    public record Receipt(List<Cart.Line> lines, BigDecimal subtotal,
                          BigDecimal thresholdDiscount, BigDecimal couponDiscount,
                          BigDecimal total) {

        public Receipt { lines = List.copyOf(lines); }
    }

    public Receipt quote(Cart cart, String coupon) {

        if (cart.isEmpty()) throw new IllegalArgumentException("Cart is empty.");
        if (!coupon.isEmpty() && !coupon.equals("STUDENT10"))
            throw new IllegalArgumentException("Unknown coupon. Use STUDENT10 or leave blank.");
        List<Cart.Line> lines = cart.lines();

        // 加购物车时不扣库存，结账前要重新查一次。
        for (Cart.Line line : lines) {
            cart.checkStock(line.product().id(), line.quantity());
        }
        BigDecimal subtotal = cart.subtotal();

        // 先满 100 减 10，再算优惠码折扣。
        BigDecimal threshold = subtotal.compareTo(new BigDecimal("100.00")) >= 0
                ? new BigDecimal("10.00") : new BigDecimal("0.00");
        BigDecimal remaining = subtotal.subtract(threshold);

        BigDecimal couponDiscount = coupon.equals("STUDENT10")
                ? remaining.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP)
                : new BigDecimal("0.00");
        return new Receipt(lines, subtotal, threshold, couponDiscount, remaining.subtract(couponDiscount));
    }
}
