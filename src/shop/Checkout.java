package shop;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 结账计价：只计算报价，不读取输入，也不清空购物车，便于独立测试。 */
public final class Checkout {
    /** 收据快照：保存明细、原价、两种优惠额和最终应付金额。 */
    public record Receipt(List<Cart.Line> lines, BigDecimal subtotal,
                          BigDecimal thresholdDiscount, BigDecimal couponDiscount,
                          BigDecimal total) {
        // 复制为不可修改的列表，后续购物车变化不会改变本次收据。
        public Receipt { lines = List.copyOf(lines); }
    }

    /** 优惠顺序：先满 100 减 10，再用 STUDENT10 减去剩余金额的 10%。 */
    public Receipt quote(Cart cart, String coupon) {
        // 空车不可结账；空优惠码表示不用优惠码，其他值必须在允许名单内。
        if (cart.isEmpty()) throw new IllegalArgumentException("Cart is empty.");
        if (!coupon.isEmpty() && !coupon.equals("STUDENT10"))
            throw new IllegalArgumentException("Unknown coupon. Use STUDENT10 or leave blank.");
        List<Cart.Line> lines = cart.lines();
        BigDecimal subtotal = cart.subtotal();
        // compareTo 按数值比较金额；达到门槛只减一次，不按倍数满减。
        BigDecimal threshold = subtotal.compareTo(new BigDecimal("100.00")) >= 0
                ? new BigDecimal("10.00") : new BigDecimal("0.00");
        BigDecimal remaining = subtotal.subtract(threshold);
        // 计算的是“优惠额”；HALF_UP 表示四舍五入到分，再从余额扣除。
        BigDecimal couponDiscount = coupon.equals("STUDENT10")
                ? remaining.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP)
                : new BigDecimal("0.00");
        return new Receipt(lines, subtotal, threshold, couponDiscount, remaining.subtract(couponDiscount));
    }
}
