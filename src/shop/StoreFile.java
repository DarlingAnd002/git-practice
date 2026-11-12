package shop;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** 读取和保存商品、库存、订单。 */
final class StoreFile {
    record State(Map<String, Catalog.Listing> listings, List<Order> orders) { }

    private final Path file;

    StoreFile(Path file) {
        this.file = file.toAbsolutePath();
    }

    State load() throws IOException {
        Map<String, Catalog.Listing> listings = new LinkedHashMap<>();
        List<Order> orders = new ArrayList<>();
        if (!Files.exists(file)) {
            return new State(listings, orders);
        }

        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            data.load(reader);
        } catch (IllegalArgumentException e) {
            throw new IOException("商店文件格式错误。", e);
        }

        try {
            if (!readField(data, "version").equals("1")) {
                throw new IllegalArgumentException("不支持的数据版本。");
            }

            // p.0.id、p.0.name 等字段属于第一个商品，p.1 属于第二个。
            int productCount = readCount(data, "products");
            for (int index = 0; index < productCount; index++) {
                String prefix = "p." + index + ".";
                String id = readField(data, prefix + "id");
                String name = readField(data, prefix + "name");
                BigDecimal price = new BigDecimal(readField(data, prefix + "price"));
                String seller = readField(data, prefix + "seller");
                int stock = Integer.parseInt(readField(data, prefix + "stock"));

                Catalog.validateStock(stock);
                if (listings.containsKey(id)) {
                    throw new IllegalArgumentException("重复商品编号：" + id);
                }
                Product product = new Product(id, name, price);
                // 旧文件没有 active 字段时，原商品仍视为在售。
                boolean active = Boolean.parseBoolean(data.getProperty(prefix + "active", "true"));
                Catalog.Listing listing = new Catalog.Listing(product, seller, stock, active);
                listings.put(id, listing);
            }

            // 每个订单下面还有商品明细，例如 o.0.i.0 是第一笔订单的第一项。
            int orderCount = readCount(data, "orders");
            for (int index = 0; index < orderCount; index++) {
                String prefix = "o." + index + ".";
                String orderId = readField(data, prefix + "id");
                String buyer = readField(data, prefix + "buyer");
                Instant time = Instant.parse(readField(data, prefix + "time"));
                int itemCount = readCount(data, prefix + "items");
                List<Order.Item> items = new ArrayList<>();

                for (int itemIndex = 0; itemIndex < itemCount; itemIndex++) {
                    String itemPrefix = prefix + "i." + itemIndex + ".";
                    String seller = readField(data, itemPrefix + "seller");
                    String productId = readField(data, itemPrefix + "id");
                    String name = readField(data, itemPrefix + "name");
                    BigDecimal price = new BigDecimal(readField(data, itemPrefix + "price"));
                    int quantity = Integer.parseInt(readField(data, itemPrefix + "quantity"));
                    BigDecimal paid = new BigDecimal(readField(data, itemPrefix + "paid"));

                    Order.Item item = new Order.Item(seller, productId, name, price, quantity, paid);
                    items.add(item);
                }

                Order order = new Order(orderId, buyer, time, items);
                orders.add(order);
            }
        } catch (RuntimeException e) {
            throw new IOException("商店数据有误，原文件没有修改。", e);
        }
        return new State(listings, orders);
    }

    void save(State state) throws IOException {
        Properties data = new Properties();
        data.setProperty("version", "1");
        data.setProperty("products", String.valueOf(state.listings().size()));
        data.setProperty("orders", String.valueOf(state.orders().size()));

        int productIndex = 0;
        for (Catalog.Listing listing : state.listings().values()) {
            Product product = listing.product();
            String prefix = "p." + productIndex + ".";
            data.setProperty(prefix + "id", product.id());
            data.setProperty(prefix + "name", product.name());
            data.setProperty(prefix + "price", product.price().toPlainString());
            data.setProperty(prefix + "seller", listing.seller());
            data.setProperty(prefix + "stock", String.valueOf(listing.stock()));
            data.setProperty(prefix + "active", String.valueOf(listing.active()));
            productIndex++;
        }

        for (int index = 0; index < state.orders().size(); index++) {
            Order order = state.orders().get(index);
            String prefix = "o." + index + ".";
            data.setProperty(prefix + "id", order.id());
            data.setProperty(prefix + "buyer", order.buyer());
            data.setProperty(prefix + "time", order.createdAt().toString());
            data.setProperty(prefix + "items", String.valueOf(order.items().size()));

            for (int itemIndex = 0; itemIndex < order.items().size(); itemIndex++) {
                Order.Item item = order.items().get(itemIndex);
                String itemPrefix = prefix + "i." + itemIndex + ".";
                data.setProperty(itemPrefix + "seller", item.seller());
                data.setProperty(itemPrefix + "id", item.productId());
                data.setProperty(itemPrefix + "name", item.name());
                data.setProperty(itemPrefix + "price", item.unitPrice().toPlainString());
                data.setProperty(itemPrefix + "quantity", String.valueOf(item.quantity()));
                data.setProperty(itemPrefix + "paid", item.paid().toPlainString());
            }
        }

        // 先写临时文件，写完才替换旧文件，避免保存失败时丢掉原数据。
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "store-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                data.store(writer, "Shopping inventory and orders");
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private String readField(Properties data, String key) {
        String value = data.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("缺少字段：" + key);
        }
        return value;
    }

    private int readCount(Properties data, String key) {
        int count = Integer.parseInt(readField(data, key));
        if (count < 0 || count > 100_000) {
            throw new IllegalArgumentException("数据数量错误：" + key);
        }
        return count;
    }
}
