package shop;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/** 登录、注册和两种角色的操作窗口。 */
public final class ShopWindow extends JFrame {
    private final AuthService auth;
    private final Catalog catalog;
    private AuthService.Session session;
    // 退出登录时保留购物车，换账号后按用户名取回。
    private final Map<String, Cart> carts = new HashMap<>();
    private Cart cart;
    private JTable products;
    private JTable cartTable;
    private JTextArea orders;
    private JLabel summary;

    public ShopWindow(AuthService auth, Catalog catalog) {
        this.auth = auth;
        this.catalog = catalog;
        Font font = new Font(Font.DIALOG, Font.PLAIN, 14);
        for (String key : new String[]{"Label.font", "Button.font", "TextField.font", "PasswordField.font",
                "ComboBox.font", "TabbedPane.font", "Table.font", "TableHeader.font", "Spinner.font"}) {
            UIManager.put(key, font);
        }
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                if (session != null) session.logout();
            }
        });
        showLogin();
        setLocationRelativeTo(null);
    }

    // 登录和注册共用一个窗口，切换页签即可。
    private void showLogin() {
        setTitle("登录");
        JTabbedPane tabs = new JTabbedPane();
        JTextField username = named(new JTextField(22), "login-user");
        JPasswordField password = passwordField("login-password");
        JPanel login = new JPanel(new BorderLayout(12, 18));
        login.setBorder(BorderFactory.createEmptyBorder(28, 20, 20, 20));
        login.add(form(new String[]{"账号", "密码"}, username, password), BorderLayout.NORTH);
        JButton loginButton = button("登录", () -> {
            char[] value = password.getPassword();
            try {
                // 登录只校验账号密码，角色从账号记录读取。
                session = auth.login(username.getText().trim(), value);
                showWorkspace();
            } finally {
                Arrays.fill(value, '\0');
                password.setText("");
            }
        });
        loginButton.setName("login-submit");
        login.add(loginButton, BorderLayout.SOUTH);
        tabs.addTab("登录", login);

        JTextField newUser = named(new JTextField(22), "register-user");
        JPasswordField newPassword = passwordField("register-password");
        JPasswordField confirm = passwordField("register-confirm");
        JComboBox<String> role = named(new JComboBox<>(new String[]{"消费者", "销售者"}), "register-role");
        JPanel register = new JPanel(new BorderLayout(8, 8));
        register.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        register.add(form(new String[]{"账号", "密码", "确认密码", "角色"}, newUser, newPassword, confirm, role), BorderLayout.NORTH);
        JPanel registerActions = new JPanel(new GridLayout(0, 1, 4, 8));
        registerActions.add(new JLabel("账号 3–24 位字母/数字/下划线；密码 8–128 位"));
        registerActions.add(button("注册", () -> {
            char[] first = newPassword.getPassword();
            char[] second = confirm.getPassword();
            try {
                if (!Arrays.equals(first, second)) throw new IllegalArgumentException("两次密码不一致。");
                auth.register(newUser.getText().trim(), first, role.getSelectedIndex() == 1 ? Role.SELLER : Role.CONSUMER);
                username.setText(newUser.getText().trim());
                tabs.setSelectedIndex(0);
                message("注册成功，请登录。");
            } finally {
                Arrays.fill(first, '\0');
                Arrays.fill(second, '\0');
                newPassword.setText("");
                confirm.setText("");
            }
        }));
        register.add(registerActions, BorderLayout.SOUTH);
        tabs.addTab("注册", register);
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        content.add(tabs);
        setContentPane(content);
        getRootPane().setDefaultButton(loginButton);
        setMinimumSize(new Dimension(540, 430));
        setSize(580, 460);
        revalidate();
        repaint();
        SwingUtilities.invokeLater(username::requestFocusInWindow);
    }

    // 共用商品和订单控件，按角色配置可见按钮。
    private void showWorkspace() {
        boolean seller = session.role() == Role.SELLER;
        setTitle((seller ? "销售者" : "消费者") + " - " + session.username());
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        header.add(new JLabel("账号：" + session.username() + "    角色：" + (seller ? "销售者" : "消费者")));
        header.add(button("刷新", this::refresh));
        header.add(button("操作说明", this::help));
        header.add(button("退出登录", () -> {
            session.logout();
            session = null;
            cart = null;
            showLogin();
        }));
        content.add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        products = table("编号", "名称", "单价", "销售者", "库存", "状态");
        products.setName("products");
        JPanel goods = new JPanel(new BorderLayout(8, 8));
        goods.add(new JScrollPane(products));
        JPanel goodsActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        if (seller) {
            goodsActions.add(button("添加商品", this::addProduct));
            goodsActions.add(button("修改价格", this::changePrice));
            goodsActions.add(button("设置库存", this::changeStock));
            goodsActions.add(button("下架商品", this::takeDown));
        } else {
            cart = carts.get(session.username());
            if (cart == null) {
                cart = new Cart(catalog, session);
                carts.put(session.username(), cart);
            }
            goodsActions.add(button("加入购物车", () -> {
                String id = selectedId(products);
                Integer quantity = askQuantity("加入购物车", 1, 1, 99);
                if (quantity == null) return;
                cart.add(id, quantity);
                refresh();
                message("已加入购物车。");
            }));
        }
        goods.add(goodsActions, BorderLayout.SOUTH);
        tabs.addTab(seller ? "我的商品" : "商品", goods);

        if (!seller) {
            cartTable = table("编号", "名称", "单价", "数量", "小计", "库存", "状态");
            cartTable.setName("cart");
            JPanel shopping = new JPanel(new BorderLayout(8, 8));
            shopping.add(new JScrollPane(cartTable));
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
            actions.add(button("修改数量", () -> {
                String id = selectedId(cartTable);
                Integer quantity = askQuantity("设置新的总数量（0 为删除）", 1, 0, 99);
                if (quantity == null) return;
                cart.update(id, quantity);
                refresh();
            }));
            actions.add(button("删除选中商品", () -> {
                String id = selectedId(cartTable);
                if (confirm("从购物车删除 " + id + "？")) {
                    cart.update(id, 0);
                    refresh();
                }
            }));
            actions.add(button("结账", this::checkout));
            summary = new JLabel();
            JPanel footer = new JPanel(new BorderLayout());
            footer.add(summary, BorderLayout.NORTH);
            footer.add(actions, BorderLayout.SOUTH);
            shopping.add(footer, BorderLayout.SOUTH);
            tabs.addTab("购物车", shopping);
        }
        orders = new JTextArea();
        orders.setEditable(false);
        orders.setName("orders");
        orders.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        orders.setMargin(new Insets(12, 12, 12, 12));
        tabs.addTab(seller ? "销售订单" : "购买记录", new JScrollPane(orders));
        content.add(tabs);
        setContentPane(content);
        getRootPane().setDefaultButton(null);
        setMinimumSize(new Dimension(900, 600));
        setSize(1080, 720);
        setLocationRelativeTo(null);
        refresh();
        revalidate();
        repaint();
    }

    // 列表重新读取当前数据，购物车不缓存库存状态。
    private void refresh() {
        if (session == null) return;
        session.require(session.role());
        DefaultTableModel model = (DefaultTableModel) products.getModel();
        model.setRowCount(0);
        List<Catalog.Listing> goods = session.role() == Role.SELLER ? catalog.mine(session) : catalog.listings();
        for (Catalog.Listing listing : goods) {
            Product product = listing.product();
            model.addRow(new Object[]{product.id(), product.name(), product.price(), listing.seller(), listing.stock(), status(product.id(), 1)});
        }
        if (session.role() == Role.CONSUMER) {
            DefaultTableModel cartModel = (DefaultTableModel) cartTable.getModel();
            cartModel.setRowCount(0);
            for (Cart.Line line : cart.lines()) {
                Product product = line.product();
                cartModel.addRow(new Object[]{product.id(), product.name(), product.price(), line.quantity(), line.subtotal(), catalog.stock(product.id()), status(product.id(), line.quantity())});
            }
            summary.setText("购物车小计：" + cart.subtotal() + (cart.stockProblems().isEmpty() ? "" : "（包含不可购买商品）"));
        }
        // 销售者只拿到自己商品的订单行，消费者拿到自己的整笔订单。
        List<Order> history = session.role() == Role.SELLER ? catalog.sales(session) : catalog.purchases(session);
        StringBuilder text = new StringBuilder();
        DateTimeFormatter time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));
        for (Order order : history) {
            text.append("订单：").append(order.id()).append("\n时间：").append(time.format(order.createdAt()));
            text.append("  买家：").append(order.buyer()).append("\n");
            for (Order.Item item : order.items()) {
                text.append(item.productId()).append("  ").append(item.name()).append("  销售者：").append(item.seller());
                text.append("  单价：").append(item.unitPrice()).append(" × ").append(item.quantity()).append("  实付：").append(item.paid()).append("\n");
            }
            text.append(session.role() == Role.SELLER ? "本店实收：" : "订单实付：").append(order.total()).append("\n\n");
        }
        orders.setText(history.isEmpty() ? "暂无订单记录。" : text.toString());
        orders.setCaretPosition(0);
    }

    private String status(String id, int quantity) {
        if (!catalog.isListed(id)) return "已下架";
        if (catalog.stock(id) == 0) return "Sold out";
        if (catalog.stock(id) < quantity) return "库存不足";
        return "在售";
    }

    private void addProduct() throws java.text.ParseException {
        JTextField name = new JTextField(22);
        JTextField price = new JTextField(22);
        JSpinner stock = new JSpinner(new SpinnerNumberModel(0, 0, Catalog.MAX_STOCK, 1));
        if (!showForm("添加商品", form(new String[]{"名称", "单价", "初始库存"}, name, price, stock))) return;
        stock.commitEdit();
        Product product = catalog.add(session, name.getText().trim(), parsePrice(price.getText()), (Integer) stock.getValue());
        refresh();
        message("已添加，商品编号：" + product.id());
    }

    private void changePrice() {
        String id = selectedId(products);
        String value = JOptionPane.showInputDialog(this, "输入新单价", catalog.find(id).price());
        if (value == null) return;
        catalog.changePrice(session, id, parsePrice(value));
        refresh();
    }

    private void changeStock() throws java.text.ParseException {
        String id = selectedId(products);
        Integer stock = askQuantity("设置当前总库存", catalog.stock(id), 0, Catalog.MAX_STOCK);
        if (stock == null) return;
        catalog.changeStock(session, id, stock);
        refresh();
    }

    private void takeDown() {
        String id = selectedId(products);
        if (confirm("确认下架 " + id + "？已有订单将保留。")) {
            catalog.takeDown(session, id);
            refresh();
        }
    }

    private void checkout() {
        if (cart.isEmpty()) throw new IllegalArgumentException("购物车为空。");
        // 先收集售完和下架项，用户同意后才删除，不能遍历时直接改购物车。
        List<Cart.Line> unavailable = new ArrayList<>();
        StringBuilder list = new StringBuilder("以下商品已售完或下架：\n");
        for (Cart.Line line : cart.lines()) {
            String id = line.product().id();
            if (!catalog.isListed(id) || catalog.stock(id) == 0) {
                unavailable.add(line);
                list.append(id).append(" ").append(line.product().name()).append(" ×").append(line.quantity()).append("  ").append(status(id, line.quantity())).append("\n");
            }
        }
        if (!unavailable.isEmpty()) {
            if (!confirm(list + "\n删除以上商品并继续结账？")) return;
            for (Cart.Line line : unavailable) cart.update(line.product().id(), 0);
            refresh();
            if (cart.isEmpty()) {
                message("商品已删除，购物车为空。");
                return;
            }
        }
        // 删除无效项后重新报价；有货但数量不足的商品仍需用户调整。
        Checkout.Receipt quote = new Checkout().quote(cart);
        StringBuilder text = new StringBuilder();
        for (Cart.Line line : quote.lines()) text.append(line.product().name()).append(" ×").append(line.quantity()).append(" = ").append(line.subtotal()).append("\n");
        if (!confirm(text + "\n应付：" + quote.total() + " 元，确认购买？")) return;
        // 最终提交还会检查库存和报价，保存成功才扣库存并清空购物车。
        Order order = catalog.purchase(session, cart, quote);
        refresh();
        message("购买成功，订单号：" + order.id());
    }

    private void help() {
        String text;
        if (session.role() == Role.SELLER) {
            text = "我的商品：只显示自己发布的商品，选中一行后可以操作。\n"
                    + "添加商品：输入名称、价格、初始库存，编号自动分配。\n"
                    + "修改价格：修改选中商品单价，不影响历史订单。\n"
                    + "设置库存：设置新的总库存，不是在原库存上增加。\n"
                    + "下架商品：确认后禁止购买，保留库存和历史记录。\n"
                    + "销售订单：只显示自己商品的明细和实收金额。";
        } else {
            text = "商品：选中商品，点击加入购物车并输入数量，不预占库存。\n"
                    + "购物车：显示最新库存，Sold out 和已下架商品不能购买。\n"
                    + "修改数量：设置新总数量，0 表示删除。\n"
                    + "删除选中商品：只删除购物车项，不删除商店商品。\n"
                    + "结账：先确认是否删除售完或下架项，再确认购买。拒绝则保留商品。\n"
                    + "购买记录：显示自己的已完成订单。";
        }
        message(text + "\n刷新：更新列表和订单。退出登录：保留本次运行中的购物车。");
    }

    // 按钮共用错误提示，取消操作由各自方法直接返回。
    private JButton button(String text, Action action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> {
            try { action.run(); }
            catch (IllegalArgumentException | IOException | UncheckedIOException | java.text.ParseException e) {
                JOptionPane.showMessageDialog(this, e instanceof java.text.ParseException ? "请输入有效的整数。" : e.getMessage(), "操作失败", JOptionPane.ERROR_MESSAGE);
            }
        });
        return button;
    }

    private JPanel form(String[] labels, JComponent... fields) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (int i = 0; i < labels.length; i++) {
            GridBagConstraints label = new GridBagConstraints();
            label.gridx = 0;
            label.gridy = i;
            label.anchor = GridBagConstraints.WEST;
            label.insets = new Insets(10, 4, 10, 16);
            panel.add(new JLabel(labels[i]), label);
            GridBagConstraints input = new GridBagConstraints();
            input.gridx = 1;
            input.gridy = i;
            input.weightx = 1;
            input.fill = GridBagConstraints.HORIZONTAL;
            input.ipady = 8;
            input.insets = new Insets(10, 4, 10, 4);
            panel.add(fields[i], input);
        }
        return panel;
    }

    private boolean showForm(String title, JPanel panel) {
        return JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private Integer askQuantity(String title, int value, int min, int max) throws java.text.ParseException {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        if (!showForm(title, form(new String[]{"数量"}, spinner))) return null;
        spinner.commitEdit();
        return (Integer) spinner.getValue();
    }

    private BigDecimal parsePrice(String text) {
        String value = text.trim();
        if (!value.matches("[0-9]{1,7}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("价格最多 7 位整数和 2 位小数。");
        BigDecimal price = new BigDecimal(value);
        if (price.signum() <= 0) throw new IllegalArgumentException("价格必须大于 0。");
        return price;
    }

    private String selectedId(JTable table) {
        int row = table.getSelectedRow();
        if (row < 0) throw new IllegalArgumentException("请先选中一件商品。");
        // 排序后屏幕行号不等于数据行号，需要转换后再取编号。
        return table.getModel().getValueAt(table.convertRowIndexToModel(row), 0).toString();
    }

    private JTable table(String... columns) {
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(model);
        table.setRowHeight(30);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        return table;
    }

    private JPasswordField passwordField(String name) {
        JPasswordField field = named(new JPasswordField(22), name);
        // 星号只遮住界面输入，文件仍按当前要求保存明文密码。
        field.setEchoChar('*');
        return field;
    }

    private <T extends JComponent> T named(T component, String name) {
        component.setName(name);
        return component;
    }

    private void message(String text) {
        JOptionPane.showMessageDialog(this, text, "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    private boolean confirm(String text) {
        return JOptionPane.showConfirmDialog(this, text, "确认", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    @FunctionalInterface private interface Action {
        void run() throws IOException, java.text.ParseException;
    }
}
