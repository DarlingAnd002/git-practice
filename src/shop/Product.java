package shop;

import java.math.BigDecimal;

/** 商品模型：record 的字段不可修改；价格由商店目录统一提供，不接受用户输入。 */
public record Product(String id, String name, BigDecimal price) {
    // 紧凑构造器：创建商品时检查编号、名称和价格，阻止非法数据进入系统。
    public Product {
        if (id == null || id.isBlank() || name == null || name.isBlank()
                || price == null || price.signum() <= 0 || price.scale() > 2) {
            throw new IllegalArgumentException("Invalid product");
        }
        // 统一金额显示为两位小数。
        price = price.setScale(2);
    }
}
