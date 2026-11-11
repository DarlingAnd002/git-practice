package shop;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** 读写商品和订单文件。 */
final class StoreFile {
    record State(Map<String, Catalog.Listing> listings, List<Order> orders) { }
    private final Path file;
    StoreFile(Path file) {
        this.file = file.toAbsolutePath();
    }

    State load() throws IOException {
        if (!Files.exists(file)) return new State(Map.of(), List.of());
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
        try {
            if (!get(properties, "version").equals("1")) throw new IllegalArgumentException("不支持的数据版本");
            Map<String, Catalog.Listing> listings = new LinkedHashMap<>();
            for (int index = 0; index < count(properties, "products"); index++) {
                String prefix = "p." + index + ".";
                Product product = new Product(get(properties, prefix + "id"), get(properties, prefix + "name"), decimal(properties, prefix + "price"));
                int stock = Integer.parseInt(get(properties, prefix + "stock"));
                Catalog.validateStock(stock);
                if (listings.put(product.id(), new Catalog.Listing(product, get(properties, prefix + "seller"), stock)) != null)
                    throw new IllegalArgumentException("重复商品编号");
            }
            List<Order> orders = new ArrayList<>();
            for (int index = 0; index < count(properties, "orders"); index++) {
                String prefix = "o." + index + ".";
                List<Order.Item> items = new ArrayList<>();
                for (int itemIndex = 0; itemIndex < count(properties, prefix + "items"); itemIndex++) {
                    String itemPrefix = prefix + "i." + itemIndex + ".";
                    items.add(new Order.Item(get(properties, itemPrefix + "seller"), get(properties, itemPrefix + "id"), get(properties, itemPrefix + "name"),
                            decimal(properties, itemPrefix + "price"), Integer.parseInt(get(properties, itemPrefix + "quantity")), decimal(properties, itemPrefix + "paid")));
                }
                orders.add(new Order(get(properties, prefix + "id"), get(properties, prefix + "buyer"), Instant.parse(get(properties, prefix + "time")), items));
            }
            return new State(listings, orders);
        } catch (RuntimeException e) { throw new IOException("商店数据格式错误，未覆盖原文件。", e); }
    }

    private static String get(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) throw new IllegalArgumentException("缺少字段：" + key);
        return value;
    }

    private static BigDecimal decimal(Properties properties, String key) {
        return new BigDecimal(get(properties, key));
    }

    private static int count(Properties properties, String key) {
        int index = Integer.parseInt(get(properties, key));
        if (index < 0 || index > 100_000) throw new IllegalArgumentException("数据数量错误");
        return index;
    }

    void save(State state) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", "1");
        properties.setProperty("products", "" + state.listings().size());
        int index = 0;
        for (Catalog.Listing listing : state.listings().values()) {
            String prefix = "p." + index++ + ".";
            put(properties, prefix, "id", listing.product().id(), "name", listing.product().name(), "price", listing.product().price().toPlainString(), "seller", listing.seller(), "stock", "" + listing.stock());
        }
        properties.setProperty("orders", "" + state.orders().size());
        for (int i = 0; i < state.orders().size(); i++) {
            Order order = state.orders().get(i);
            String prefix = "o." + i + ".";
            put(properties, prefix, "id", order.id(), "buyer", order.buyer(), "time", order.createdAt().toString(), "items", "" + order.items().size());
            for (int itemIndex = 0; itemIndex < order.items().size(); itemIndex++) {
                Order.Item item = order.items().get(itemIndex);
                put(properties, prefix + "i." + itemIndex + ".", "seller", item.seller(), "id", item.productId(), "name", item.name(), "price", item.unitPrice().toPlainString(), "quantity", "" + item.quantity(), "paid", item.paid().toPlainString());
            }
        }
        // 库存和订单一起保存，不能只写成功其中一部分。
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "store-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) { properties.store(writer, "Shopping inventory and orders"); }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temp); }
    }

    private static void put(Properties properties, String prefix, String... pairs) {
        for (int i = 0; i < pairs.length; i += 2) properties.setProperty(prefix + pairs[i], pairs[i + 1]);
    }
}
