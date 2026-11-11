package shop;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

/** 商品目录：集中维护商品及价格，供菜单和购物车共同查询。 */
public final class Catalog {
    // 内部允许销售者维护，外部只返回快照；金额用字符串构造。
    private final List<Product> products = new ArrayList<>(List.of(
            new Product("P01", "Notebook", new BigDecimal("18.00")),
            new Product("P02", "Pen set", new BigDecimal("12.50")),
            new Product("P03", "Desk lamp", new BigDecimal("89.00")),
            new Product("P04", "Backpack", new BigDecimal("129.00"))));

    /** 返回只读商品列表，用于展示商店商品。 */
    public List<Product> products() { return List.copyOf(products); }

    /** 在业务层校验销售者权限，防止消费者绕过菜单调用。 */
    public void add(AuthService.Session session, Product product) {
        requireSeller(session);
        if (product == null || !product.id().matches("[A-Z0-9]{3,12}")
                || product.name().length() > 60 || product.name().chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("编号须为 3–12 位大写字母或数字，名称最多 60 字且不含控制字符。");
        if (products.stream().anyMatch(p -> p.id().equals(product.id())))
            throw new IllegalArgumentException("商品编号已存在。");
        products.add(product);
    }

    /** 调价后购物车按最新目录价报价；已生成的收据不变。 */
    public void changePrice(AuthService.Session session, String id, BigDecimal price) {
        requireSeller(session);
        Product old = find(id);
        products.set(products.indexOf(old), new Product(old.id(), old.name(), price));
    }

    private static void requireSeller(AuthService.Session session) {
        if (session == null) throw new IllegalArgumentException("请先登录。");
        session.require(Role.SELLER);
    }

    /** 按编号查找商品；找不到就抛出异常，由菜单显示错误提示。 */
    public Product find(String id) {
        return products.stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown product ID."));
    }
}
