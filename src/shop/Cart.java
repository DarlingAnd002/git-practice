package shop;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 购物车：只维护商品编号与数量，商品信息始终从目录读取。 */
public  class Cart {
    // 每种商品最多购买 99 件，新增和修改都必须遵守这个限制。
    public static final int MAX_QUANTITY = 99;
    private final Catalog catalog;
    // 键是商品编号，值是数量；LinkedHashMap 保留商品加入的先后顺序。
    private final Map<String, Integer> quantities = new LinkedHashMap<>();

    public Cart(Catalog catalog) { this.catalog = catalog; }

    /** 一条只读购物明细：商品、数量及该商品的小计。 */
    public record Line(Product product, int quantity) {
        /** 汇总每条明细的小计，得到折扣前总价。 */
    public BigDecimal subtotal() {
            return product.price().multiply(BigDecimal.valueOf(quantity));
        }
    }

    /** 添加商品；同一商品重复添加时累加数量。 */
    public void add(String id, int quantity) {
        catalog.find(id); // 先验证编号确实存在，禁止添加目录以外的商品。
        validate(quantity);
        int total = quantities.getOrDefault(id, 0) + quantity;
        validate(total); // 累加后的数量也要验证；全部通过后才修改购物车。
        quantities.put(id, total);
    }

    /** 设置新的总数量（不是增量）；数量为 0 时删除该商品。 */
    public void update(String id, int quantity) {
        if (!quantities.containsKey(id)) throw new IllegalArgumentException("Item is not in cart.");
        if (quantity == 0) quantities.remove(id);
        else {
            validate(quantity);
            quantities.put(id, quantity);
        }
    }

    /** 在业务层统一校验数量，即使绕过菜单调用也会受到限制。 */
    private static void validate(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY)
            throw new IllegalArgumentException("Quantity must be between 1 and 99.");
    }

    /** 将内部映射转换为不可修改的明细列表，避免调用方改动购物车状态。 */
    public List<Line> lines() {
        return quantities.entrySet().stream()
                .map(e -> new Line(catalog.find(e.getKey()), e.getValue())).toList();
    }

    /** 汇总每条明细的小计，得到折扣前总价。 */
    public BigDecimal subtotal() {
        return lines().stream().map(Line::subtotal).reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    public boolean isEmpty() { return quantities.isEmpty(); }
    public void clear() { quantities.clear(); }
}
