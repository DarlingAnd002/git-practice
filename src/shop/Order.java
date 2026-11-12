package shop;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

/** 订单保留成交时的商品信息和金额。 */
public record Order(String id, String buyer, Instant createdAt, List<Item> items) {
    public Order { items = List.copyOf(items); }
    public record Item(String seller, String productId, String name,
                       BigDecimal unitPrice, int quantity, BigDecimal paid) {
        public BigDecimal subtotal() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public BigDecimal total() {
        return items.stream().map(Item::paid).reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    public BigDecimal subtotal() {
        return items.stream().map(Item::subtotal).reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    // 同一笔订单可能有多个卖家，只取当前卖家的商品。
    public Order forSeller(String seller) {
        List<Item> sellerItems = new ArrayList<>();
        for (Item item : items) {
            if (item.seller().equals(seller)) {
                sellerItems.add(item);
            }
        }
        return new Order(id, buyer, createdAt, sellerItems);
    }
}
