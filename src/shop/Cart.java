package shop;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 购物车，记录商品编号和购买数量。 */
public class Cart {

    public static final int MAX_QUANTITY = 99;
    private final Catalog catalog;
    private final String buyer;

    private final Map<String, Integer> quantities = new LinkedHashMap<>();

    // 测试报价时不绑定账号，这种购物车不能生成订单。
    public Cart(Catalog catalog) {
        this.catalog = catalog;
        this.buyer = null;
    }

    public Cart(Catalog catalog, AuthService.Session session) {
        if (session == null) throw new IllegalArgumentException("请先登录。");
        session.require(Role.CONSUMER);
        this.catalog = catalog;
        this.buyer = session.username();
    }

    boolean belongsTo(Catalog catalog, String buyer) {
        return this.catalog == catalog && buyer.equals(this.buyer);
    }

    void checkStock(String id, int quantity) {
        catalog.checkStock(id, quantity);
    }

    public record Line(Product product, int quantity) {

        public BigDecimal subtotal() {
            return product.price().multiply(BigDecimal.valueOf(quantity));
        }
    }

    public void add(String id, int quantity) {
        catalog.find(id);
        validate(quantity);
        int total = quantities.getOrDefault(id, 0) + quantity;
        validate(total);
        catalog.checkStock(id, total);
        quantities.put(id, total);
    }

    // quantity 是新的总数量，设为 0 就删除商品。
    public void update(String id, int quantity) {
        if (!quantities.containsKey(id)) throw new IllegalArgumentException("Item is not in cart.");
        if (quantity == 0) quantities.remove(id);
        else {
            validate(quantity);
            catalog.checkStock(id, quantity);
            quantities.put(id, quantity);
        }
    }

    private static void validate(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY)
            throw new IllegalArgumentException("Quantity must be between 1 and 99.");
    }

    public List<Line> lines() {
        List<Line> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            Product product = catalog.find(entry.getKey());
            result.add(new Line(product, entry.getValue()));
        }
        return List.copyOf(result);
    }

    // 每次查看都查当前库存，不删除购物车里的商品。
    public List<String> stockProblems() {
        List<String> problems = new ArrayList<>();
        for (Line line : lines()) {
            String id = line.product().id();
            int stock = catalog.stock(id);
            if (!catalog.isListed(id)) {
                problems.add(id + " " + line.product().name() + " 已下架");
            } else if (stock == 0) {
                problems.add(id + " " + line.product().name() + " Sold out");
            } else if (line.quantity() > stock) {
                problems.add(id + " " + line.product().name() + " 库存不足，购物车数量："
                        + line.quantity() + "，当前库存：" + stock);
            }
        }
        return List.copyOf(problems);
    }

    public BigDecimal subtotal() {
        BigDecimal total = new BigDecimal("0.00");
        for (Line line : lines()) {
            total = total.add(line.subtotal());
        }
        return total;
    }

    public boolean isEmpty() {
        return quantities.isEmpty();
    }

    public void clear() {
        quantities.clear();
    }
}
