package shop;

import java.math.BigDecimal;
import java.util.List;

/** 商品目录：集中维护商品及价格，供菜单和购物车共同查询。 */
public final class Catalog {
    // List.of 创建不可修改的目录；用字符串构造 BigDecimal，避免浮点精度误差。
    private final List<Product> products = List.of(
            new Product("P01", "Notebook", new BigDecimal("18.00")),
            new Product("P02", "Pen set", new BigDecimal("12.50")),
            new Product("P03", "Desk lamp", new BigDecimal("89.00")),
            new Product("P04", "Backpack", new BigDecimal("129.00")));

    /** 返回只读商品列表，用于展示商店商品。 */
    public List<Product> products() { return products; }

    /** 按编号查找商品；找不到就抛出异常，由菜单显示错误提示。 */
    public Product find(String id) {
        return products.stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown product ID."));
    }
}
