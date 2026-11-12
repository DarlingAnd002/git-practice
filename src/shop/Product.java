package shop;

import java.math.BigDecimal;

/** 商品编号、名称和单价。 */
public record Product(String id, String name, BigDecimal price) {

    public Product {
        if (id == null || id.isBlank() || name == null || name.isBlank()
                || price == null || price.signum() <= 0 || price.scale() > 2) {
            throw new IllegalArgumentException("Invalid product");
        }

        price = price.setScale(2);
    }
}
