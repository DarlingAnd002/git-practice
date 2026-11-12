package shop;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.io.*;
import java.time.Instant;
import java.util.*;

/** 管理商品、库存和订单。 */
public final class Catalog {
    public static final int MAX_STOCK = 1_000_000;
    public record Listing(Product product, String seller, int stock, boolean active) {
        public Listing(Product product, String seller, int stock) {
            this(product, seller, stock, true);
        }
    }
    private Map<String, Listing> listings = new LinkedHashMap<>();
    private List<Order> orders = new ArrayList<>();
    private final StoreFile storage;
    public Catalog() {
        storage = null;
    }

    public Catalog(Path file) throws IOException {
        storage = new StoreFile(file);
        StoreFile.State state = storage.load();
        listings = new LinkedHashMap<>(state.listings());
        orders = new ArrayList<>(state.orders());
    }

    public synchronized List<Product> products() {
        List<Product> result = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.active()) result.add(listing.product());
        }
        return List.copyOf(result);
    }

    public synchronized List<Listing> listings() {
        List<Listing> result = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.active()) result.add(listing);
        }
        return List.copyOf(result);
    }

    public synchronized List<Listing> mine(AuthService.Session session) {
        require(session, Role.SELLER);
        List<Listing> result = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.seller().equals(session.username())) {
                result.add(listing);
            }
        }
        return List.copyOf(result);
    }

    public synchronized Product find(String id) {
        return listing(id).product();
    }

    public synchronized int stock(String id) {
        return listing(id).stock();
    }

    private Listing listing(String id) {
        Listing value = listings.get(id);
        if (value == null) throw new IllegalArgumentException("商品编号不存在。");
        return value;
    }

    public synchronized Product add(AuthService.Session session, String name, BigDecimal price, int stock) {
        require(session, Role.SELLER);
        // 从已有编号的最大值继续，下架商品仍保留编号。
        long next = 1;
        for (String id : listings.keySet()) {
            if (id.matches("P[0-9]{1,11}")) {
                next = Math.max(next, Long.parseLong(id.substring(1)) + 1);
            }
        }
        if (next > 99_999_999_999L) throw new IllegalArgumentException("商品编号已用完。");
        String id = String.format(Locale.ROOT, "P%03d", next);
        Product product = new Product(id, name, price);
        add(session, product, stock);
        return product;
    }

    public synchronized boolean isListed(String id) {
        return listing(id).active();
    }

    public synchronized void takeDown(AuthService.Session session, String id) {
        Listing old = owned(session, id);
        if (!old.active()) throw new IllegalArgumentException("商品已经下架。");
        // 下架只改在售状态，库存和历史记录不删除。
        replace(new Listing(old.product(), old.seller(), old.stock(), false));
    }

    public synchronized void add(AuthService.Session session, Product product) {
        add(session, product, 99);
    }

    public synchronized void add(AuthService.Session session, Product product, int stock) {
        require(session, Role.SELLER);
        validateStock(stock);
        if (product == null || !product.id().matches("[A-Z0-9]{3,12}") || product.name().length() > 60 || product.name().chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("编号须为 3–12 位大写字母或数字，名称最多 60 字且不含控制字符。");
        if (listings.containsKey(product.id())) throw new IllegalArgumentException("商品编号已存在。");
        Map<String, Listing> updated = new LinkedHashMap<>(listings);

        // 商品归当前登录的销售者所有。
        updated.put(product.id(), new Listing(product, session.username(), stock));
        commit(updated, orders);
    }

    public synchronized void changePrice(AuthService.Session session, String id, BigDecimal price) {
        Listing old = owned(session, id);
        replace(new Listing(new Product(id, old.product().name(), price), old.seller(), old.stock(), old.active()));
    }

    public synchronized void changeStock(AuthService.Session session, String id, int stock) {
        Listing old = owned(session, id);
        validateStock(stock);
        replace(new Listing(old.product(), old.seller(), stock, old.active()));
    }

    private Listing owned(AuthService.Session session, String id) {
        require(session, Role.SELLER);
        Listing value = listing(id);
        if (!value.seller().equals(session.username())) throw new IllegalArgumentException("只能管理自己的商品。");
        return value;
    }

    private void replace(Listing value) {
        Map<String, Listing> updated = new LinkedHashMap<>(listings);
        updated.put(value.product().id(), value);
        commit(updated, orders);
    }

    public static void validateStock(int stock) {
        if (stock < 0 || stock > MAX_STOCK) throw new IllegalArgumentException("库存须为 0–1000000 的整数。");
    }

    private static void require(AuthService.Session session, Role role) {
        if (session == null) throw new IllegalArgumentException("请先登录。");
        session.require(role);
    }

    public synchronized void checkStock(String id, int quantity) {
        if (!isListed(id)) throw new IllegalArgumentException(id + " 已下架");
        int stock = stock(id);
        if (stock == 0) throw new IllegalArgumentException(id + " Sold out");
        if (quantity > stock) throw new IllegalArgumentException(id + " 库存不足，当前库存：" + stock);
    }

    public synchronized List<Order> purchases(AuthService.Session session) {
        require(session, Role.CONSUMER);
        List<Order> result = new ArrayList<>();
        for (Order order : orders) {
            if (order.buyer().equals(session.username())) {
                result.add(order);
            }
        }
        return List.copyOf(result);
    }

    public synchronized List<Order> sales(AuthService.Session session) {
        require(session, Role.SELLER);
        List<Order> result = new ArrayList<>();
        for (Order order : orders) {
            Order sellerOrder = order.forSeller(session.username());
            if (!sellerOrder.items().isEmpty()) {
                result.add(sellerOrder);
            }
        }
        return List.copyOf(result);
    }

    public synchronized Order purchase(AuthService.Session session, Cart cart, Checkout.Receipt accepted) {
        require(session, Role.CONSUMER);
        if (!cart.belongsTo(this, session.username())) throw new IllegalArgumentException("不能结算他人的购物车。");
        Checkout.Receipt current = new Checkout().quote(cart);
        if (!current.equals(accepted)) throw new IllegalArgumentException("商品价格或购物车已变化，请重新确认报价。");
        Map<String, Listing> updated = new LinkedHashMap<>(listings);
        List<Order.Item> items = new ArrayList<>();
        for (Cart.Line line : current.lines()) {
            String id = line.product().id();
            checkStock(id, line.quantity());
            Listing old = listing(id);
            updated.put(id, new Listing(old.product(), old.seller(), old.stock() - line.quantity(), old.active()));

            items.add(new Order.Item(old.seller(), id, line.product().name(), line.product().price(), line.quantity(), line.subtotal()));
        }
        Order order = new Order(UUID.randomUUID().toString(), session.username(), Instant.now(), items);
        List<Order> updatedOrders = new ArrayList<>(orders);
        updatedOrders.add(order);
        commit(updated, updatedOrders);
        cart.clear();
        return order;
    }

    private void commit(Map<String, Listing> updated, List<Order> updatedOrders) {
        // 先保存文件；保存失败就不改内存里的库存和订单。
        if (storage != null) {
            try { storage.save(new StoreFile.State(updated, updatedOrders)); }
            catch (IOException e) { throw new UncheckedIOException("保存失败，库存、订单和购物车保持原样。", e); }
        }
        listings = new LinkedHashMap<>(updated);
        orders = new ArrayList<>(updatedOrders);
    }
}
