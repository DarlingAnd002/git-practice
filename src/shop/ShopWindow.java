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
    private JPanel goodsList;
    private JPanel cartList;
    private JScrollPane goodsScroll;
    private JScrollPane cartScroll;
    private JCheckBox goodsAll;
    private JLabel goodsSelection;
    private final Map<String, Set<String>> goodsChecksByUser = new HashMap<>();
    private Set<String> goodsChecked = new LinkedHashSet<>();
    private final Map<String, JCheckBox> goodsBoxes = new LinkedHashMap<>();
    private List<Catalog.Listing> visibleGoods = List.of();
    private final Color accent = new Color(235, 91, 34);
    private final Color muted = new Color(125, 130, 138);
    private final Color page = new Color(246, 247, 249);
    private JTable orderTable;
    private List<Order> orderRows = List.of();
    private JTextField search;
    private JComboBox<String> goodsFilter;
    private JCheckBox allChecked;
    private final Map<String, Set<String>> checksByUser = new HashMap<>();
    private Set<String> checkedIds = new LinkedHashSet<>();
    private final Map<String, JButton> actionButtons = new HashMap<>();
    private boolean refreshing;
    private JLabel summary;
    private final Map<String, String> quantityDrafts = new HashMap<>();
    private final Map<String, String> quantityErrors = new HashMap<>();

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
        JTabbedPane tabs = tabbedPane();
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
                newPassword.setText("");
                confirm.setText("");
                message("注册成功，请登录。");
            } finally {
                Arrays.fill(first, '\0');
                Arrays.fill(second, '\0');
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

    private void showWorkspace() {
        actionButtons.clear();quantityDrafts.clear();quantityErrors.clear();
        boolean seller = session.role() == Role.SELLER;
        goodsChecked = goodsChecksByUser.computeIfAbsent(session.username(), key -> new LinkedHashSet<>());
        setTitle((seller ? "销售者" : "消费者") + " - " + session.username());
        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBackground(page);
        content.setBorder(BorderFactory.createEmptyBorder(18, 24, 20, 24));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(text(session.username() + "   /   " + (seller ? "销售者" : "消费者"), 18, new Color(40, 43, 48)), BorderLayout.WEST);
        JPanel tools = flow();
        tools.add(button("刷新", this::refresh));
        tools.add(button("操作说明", this::help));
        tools.add(button("退出登录", () -> {session.logout();session = null;cart = null;showLogin();}));
        header.add(tools, BorderLayout.EAST);
        content.add(header, BorderLayout.NORTH);
        JTabbedPane tabs = tabbedPane();
        tabs.setFont(new Font(Font.DIALOG, Font.PLAIN, 16));
        goodsList = listPanel("goods-list");
        goodsScroll = scroll(goodsList);
        JPanel goods = new JPanel(new BorderLayout(0, 8));
        goods.setBackground(Color.WHITE);
        goods.add(goodsScroll);
        search = named(new JTextField(23), "search");
        goodsFilter = named(new JComboBox<>(seller ? new String[]{"全部", "在售", "已下架", "售罄"} : new String[]{"全部", "有货", "售罄"}), "goods-filter");
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setBackground(Color.WHITE);
        top.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 14));
        JPanel find = flow();find.add(text("搜索", 14, muted));find.add(search);find.add(goodsFilter);
        top.add(find, BorderLayout.NORTH);
        JPanel batch = flow();
        goodsAll = checkBox("全选当前列表");goodsAll.setOpaque(false);
        goodsAll.addActionListener(event -> {
            for (Catalog.Listing item : visibleGoods) {
                if (goodsAll.isSelected()) goodsChecked.add(item.product().id());else goodsChecked.remove(item.product().id());
            }
            for (var entry : goodsBoxes.entrySet()) entry.getValue().setSelected(goodsChecked.contains(entry.getKey()));
            updateButtons();
        });
        goodsSelection = text("", 13, muted);batch.add(goodsAll);batch.add(goodsSelection);
        if (seller) {
            batch.add(primary(button("添加商品", this::addProduct)));
            batch.add(button("上架勾选商品", () -> batchState(true)));
            batch.add(button("下架勾选商品", () -> batchState(false)));
        } else {
            cart = carts.computeIfAbsent(session.username(), key -> new Cart(catalog, session));
            checkedIds = checksByUser.computeIfAbsent(session.username(), key -> new LinkedHashSet<>());
            batch.add(primary(button("加入勾选商品", () -> {
                if (goodsChecked.isEmpty()) throw new IllegalArgumentException("先勾选商品。");
                NumberInput quantity = new NumberInput(1, 1, 99);
                editForm("批量加入购物车", form(new String[]{"每种商品数量（留空为 1）"}, quantity), new String[]{"加入", "取消"}, choice -> {
                    if (choice != 0) return true;
                    cart.addAll(goodsChecked, quantity.number());checkedIds.addAll(goodsChecked);refresh();return true;
                });
            })));
        }
        top.add(batch, BorderLayout.SOUTH);goods.add(top, BorderLayout.NORTH);
        tabs.addTab(seller ? "我的商品" : "商品", goods);
        if (!seller) {
            cartList = listPanel("cart-list");cartScroll = scroll(cartList);
            JPanel shopping = new JPanel(new BorderLayout());shopping.setBackground(Color.WHITE);shopping.add(cartScroll);
            JPanel bottom = new JPanel(new BorderLayout(12, 8));bottom.setBackground(Color.WHITE);
            bottom.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(233, 235, 238)), BorderFactory.createEmptyBorder(18, 14, 18, 14)));
            JPanel left = flow();allChecked = checkBox("全选");allChecked.setOpaque(false);
            allChecked.addActionListener(event -> {checkedIds.clear();if (allChecked.isSelected()) for (Cart.Line line : cart.lines()) checkedIds.add(line.product().id());refresh();});
            left.add(allChecked);left.add(button("删除勾选商品", () -> {
                if (checkedIds.isEmpty()) return;
                if (confirm("删除已勾选的 " + checkedIds.size() + " 种商品？")) {
                    for (String id : new ArrayList<>(checkedIds)) cart.update(id, 0);refresh();
                }
            }));
            bottom.add(left, BorderLayout.WEST);
            JPanel right = flow();summary = text("", 15, accent);right.add(summary);right.add(primary(button("结算勾选商品", this::checkout)));
            bottom.add(right, BorderLayout.EAST);shopping.add(bottom, BorderLayout.SOUTH);tabs.addTab("购物车", shopping);
        }
        orderTable = table("订单号", "时间", "买家", "商品件数", seller ? "本店实收" : "实付金额", "状态");
        orderTable.setName("orders");openOnDoubleClick(orderTable, this::orderDetails);
        JPanel orderPanel = new JPanel(new BorderLayout());orderPanel.setBackground(Color.WHITE);orderPanel.add(scroll(orderTable));
        JPanel hint = flow();hint.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));hint.add(text("双击订单查看成交明细", 13, muted));orderPanel.add(hint, BorderLayout.NORTH);
        tabs.addTab(seller ? "销售订单" : "购买记录", orderPanel);
        goodsFilter.addActionListener(event -> refresh());
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent event) {refresh();}
            public void removeUpdate(javax.swing.event.DocumentEvent event) {refresh();}
            public void changedUpdate(javax.swing.event.DocumentEvent event) {refresh();}
        });
        content.add(tabs);setContentPane(content);getRootPane().setDefaultButton(null);
        setMinimumSize(new Dimension(1040, 650));setSize(1240, 800);setLocationRelativeTo(null);
        refresh();revalidate();repaint();
    }

    private void refresh() {
        if (session == null || orderTable == null || refreshing) return;
        Point goodsPosition = goodsScroll.getViewport().getViewPosition();
        Point cartPosition = cartScroll == null ? new Point() : cartScroll.getViewport().getViewPosition();
        TableView ordersView = remember(orderTable);
        refreshing = true;
        try {
            String query = search.getText().trim().toLowerCase(Locale.ROOT);String filter = goodsFilter.getSelectedItem().toString();
            List<Catalog.Listing> source = session.role() == Role.SELLER ? catalog.mine(session) : catalog.listings();
            Set<String> present = new HashSet<>();for (Catalog.Listing item : source) present.add(item.product().id());goodsChecked.retainAll(present);
            List<Catalog.Listing> filtered = new ArrayList<>();
            for (Catalog.Listing item : source) {
                Product p = item.product();String description = (p.id() + " " + p.name() + " " + item.seller()).toLowerCase(Locale.ROOT);
                if (!description.contains(query)) continue;
                if (filter.equals("在售") && !item.active() || filter.equals("已下架") && item.active()) continue;
                if (filter.equals("有货") && item.stock() == 0 || filter.equals("售罄") && (!item.active() || item.stock() > 0)) continue;
                filtered.add(item);
            }
            visibleGoods = List.copyOf(filtered);goodsList.removeAll();goodsBoxes.clear();
            for (Catalog.Listing item : visibleGoods) goodsList.add(itemCard(item, null));
            if (visibleGoods.isEmpty()) goodsList.add(empty("没有符合条件的商品"));
            goodsList.revalidate();goodsList.repaint();
            if (session.role() == Role.CONSUMER) {
                cartList.removeAll();Set<String> ids = new HashSet<>();
                for (Cart.Line line : cart.lines()) {
                    ids.add(line.product().id());
                    Catalog.Listing item = new Catalog.Listing(line.product(), catalog.seller(line.product().id()), catalog.stock(line.product().id()), catalog.isListed(line.product().id()));
                    cartList.add(itemCard(item, line));
                }
                checkedIds.retainAll(ids);quantityDrafts.keySet().retainAll(ids);quantityErrors.keySet().retainAll(ids);if (cart.isEmpty()) cartList.add(empty("购物车为空，先去商品页挑选"));
                cartList.revalidate();cartList.repaint();
            }
            orderRows = session.role() == Role.SELLER ? catalog.sales(session) : catalog.purchases(session);
            DefaultTableModel model = (DefaultTableModel) orderTable.getModel();model.setRowCount(0);
            DateTimeFormatter time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));
            for (Order order : orderRows) model.addRow(new Object[]{order.id(), time.format(order.createdAt()), order.buyer(), order.items().stream().mapToInt(Order.Item::quantity).sum(), order.total(), "已完成"});
            restore(orderTable, ordersView);
        } finally {refreshing = false;}
        updateSummary();updateButtons();
        restoreScroll(goodsScroll, goodsPosition);
        if (session.role() == Role.CONSUMER) restoreScroll(cartScroll, cartPosition);
    }

    private JPanel itemCard(Catalog.Listing item, Cart.Line line) {
        boolean shopping = line != null;Product product = item.product();String id = product.id();
        JPanel card = new JPanel(new GridBagLayout());card.setName((shopping ? "cart-card-" : "goods-card-") + id);
        card.setBackground(Color.WHITE);card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(238, 239, 241)), BorderFactory.createEmptyBorder(20, 12, 20, 18)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 166));card.setPreferredSize(new Dimension(1050, 156));card.setMinimumSize(new Dimension(920, 156));
        JCheckBox box = named(checkBox(""), (shopping ? "cart-check-" : "goods-check-") + id);
        box.setOpaque(false);Set<String> chosen = shopping ? checkedIds : goodsChecked;box.setSelected(chosen.contains(id));
        box.addActionListener(event -> {if (box.isSelected()) chosen.add(id);else chosen.remove(id);updateSummary();updateButtons();});
        if (!shopping) goodsBoxes.put(id, box);
        addCell(card, box, 0, 0, 34);
        JLabel image = text("商品", 15, new Color(160, 166, 174));image.setHorizontalAlignment(SwingConstants.CENTER);image.setOpaque(true);image.setBackground(new Color(246, 247, 249));image.setPreferredSize(new Dimension(82, 82));
        addCell(card, image, 1, 0, 94);
        JPanel info = new JPanel();info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));info.setOpaque(false);
        info.add(text(product.name(), 17, new Color(37, 40, 45)));info.add(Box.createVerticalStrut(10));
        info.add(text("编号 " + id + ("    销售者 " + item.seller()), 13, muted));info.add(Box.createVerticalStrut(7));
        String state = status(id, shopping ? line.quantity() : 1);
        info.add(text("库存 " + item.stock() + "    " + state, 13, state.equals("在售") ? muted : new Color(190, 85, 53)));
        addCell(card, info, 2, 1, 260);
        JPanel cost = new JPanel();cost.setLayout(new BoxLayout(cost, BoxLayout.Y_AXIS));cost.setOpaque(false);
        JLabel price = text("¥" + product.price(), 19, accent);price.setFont(price.getFont().deriveFont(Font.BOLD));cost.add(price);
        if (shopping) {cost.add(Box.createVerticalStrut(7));cost.add(text("小计 ¥" + line.subtotal(), 12, muted));}
        addCell(card, cost, 3, 0, 140);
        if (shopping) {
            addCell(card, quantityControl(line), 4, 0, 180);
            JButton remove = button("删除", () -> {if (confirm("从购物车删除 " + product.name() + "？")) {cart.update(id, 0);refresh();}});
            remove.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));remove.setForeground(muted);addCell(card, remove, 5, 0, 62);
        } else addCell(card, text("双击操作", 12, new Color(165, 169, 176)), 4, 0, 105);
        MouseAdapter clicks = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                if (event.getClickCount() == 1) box.doClick();
                else if (event.getClickCount() == 2) execute(() -> {
                    if (shopping) editCartItem(id);else if (session.role() == Role.SELLER) editProduct(id);else viewProduct(id);
                });
            }
        };
        cardClicks(card, clicks);return card;
    }

    private JPanel quantityControl(Cart.Line line) {
        String id = line.product().id();int current = line.quantity();
        JPanel panel = new JPanel(new BorderLayout(0, 5));panel.setOpaque(false);
        JPanel stepper = new JPanel(new BorderLayout());stepper.setBackground(Color.WHITE);stepper.setBorder(BorderFactory.createLineBorder(new Color(218, 221, 225)));
        if (!catalog.isListed(id) || catalog.stock(id) == 0) {quantityDrafts.remove(id);quantityErrors.remove(id);}
        JTextField value = named(new JTextField(quantityDrafts.getOrDefault(id, String.valueOf(current)), 4), "cart-quantity-" + id);value.setToolTipText("点击手动输入，回车或移开焦点生效");value.setHorizontalAlignment(SwingConstants.CENTER);value.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        JLabel error = text(quantityErrors.getOrDefault(id, " "), 11, new Color(180, 45, 35));error.setName("quantity-error-" + id);error.setToolTipText(quantityErrors.get(id));
        JButton minus = new JButton("−");JButton plus = new JButton("+");
        for (JButton button : new JButton[]{minus, plus}) {button.setUI(new javax.swing.plaf.basic.BasicButtonUI());button.setBackground(Color.WHITE);button.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));button.setFocusPainted(false);}
        boolean available = catalog.isListed(id) && catalog.stock(id) > 0;
        value.setEnabled(available);minus.setEnabled(available && current > 1);plus.setEnabled(available && current < Math.min(99, catalog.stock(id)));
        Runnable commit = () -> {
            if (refreshing || !value.isDisplayable()) return;
            String input = value.getText().trim();
            if (input.isEmpty()) {value.setText(String.valueOf(current));quantityDrafts.remove(id);quantityErrors.remove(id);error.setText(" ");updateSummary();return;}
            try {
                if (!input.matches("[0-9]+")) throw new IllegalArgumentException("请输入整数");
                int count = quantityValue(value, current);
                if (count == current) {quantityDrafts.remove(id);quantityErrors.remove(id);error.setText(" ");value.setForeground(new Color(40, 43, 48));updateSummary();return;}
                cart.update(id, count);quantityDrafts.remove(id);quantityErrors.remove(id);refresh();
            } catch (IllegalArgumentException e) {quantityDrafts.put(id, value.getText());quantityErrors.put(id, e.getMessage());updateSummary();error.setText(e.getMessage());value.setForeground(new Color(180, 45, 35));error.setToolTipText(e.getMessage());}
        };
        value.addActionListener(event -> commit.run());
        value.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) {value.setText("");value.setForeground(new Color(40, 43, 48));}
            @Override public void focusLost(FocusEvent event) {
                if (event.getOppositeComponent() != minus && event.getOppositeComponent() != plus) commit.run();
            }
        });
        minus.addActionListener(event -> execute(() -> {cart.update(id, quantityValue(value, current) - 1);refresh();}));
        plus.addActionListener(event -> execute(() -> {cart.update(id, quantityValue(value, current) + 1);refresh();}));
        stepper.add(minus, BorderLayout.WEST);stepper.add(value);stepper.add(plus, BorderLayout.EAST);
        panel.add(stepper, BorderLayout.NORTH);panel.add(error, BorderLayout.SOUTH);return panel;
    }

    private int quantityValue(JTextField field, int fallback) {
        String input = field.getText().trim();if (input.isEmpty()) return fallback;
        if (!input.matches("[0-9]+")) throw new IllegalArgumentException("请输入整数");
        try {return Integer.parseInt(input);}catch (NumberFormatException e) {throw new IllegalArgumentException("数量过大");}
    }

    private void cardClicks(Component component, MouseAdapter listener) {
        if (component instanceof AbstractButton || component instanceof javax.swing.text.JTextComponent) return;
        component.addMouseListener(listener);if (component instanceof Container container) for (Component child : container.getComponents()) cardClicks(child, listener);
    }

    private void addCell(JPanel panel, Component child, int column, double weight, int width) {
        GridBagConstraints c = new GridBagConstraints();c.gridx = column;c.weightx = weight;c.fill = GridBagConstraints.HORIZONTAL;c.anchor = GridBagConstraints.WEST;c.insets = new Insets(0, 6, 0, 6);
        if (width > 0) child.setMinimumSize(new Dimension(width, child.getPreferredSize().height));panel.add(child, c);
    }

    private JPanel flow() {JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));panel.setOpaque(false);return panel;}
    private JPanel listPanel(String name) {JPanel panel = named(new JPanel(), name);panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));panel.setBackground(Color.WHITE);return panel;}
    private JScrollPane scroll(Component component) {JScrollPane scroll = new JScrollPane(component);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.getViewport().setBackground(Color.WHITE);scroll.getVerticalScrollBar().setUnitIncrement(24);return scroll;}
    private JLabel text(String value, int size, Color color) {JLabel label = new JLabel(value);label.putClientProperty("html.disable", true);label.setFont(new Font(Font.DIALOG, Font.PLAIN, size));label.setForeground(color);return label;}
    private JPanel empty(String message) {JPanel panel = flow();panel.setBorder(BorderFactory.createEmptyBorder(48, 30, 48, 30));panel.add(text(message, 16, muted));return panel;}

    private void batchState(boolean active) {
        if (goodsChecked.isEmpty()) return;
        StringBuilder list = new StringBuilder();
        for (String id : goodsChecked) list.append(id).append(" ").append(catalog.find(id).name()).append("\n");
        if (confirm("将以下 " + goodsChecked.size() + " 种商品" + (active ? "上架" : "下架") + "？\n" + list)) {
            catalog.setActive(session, goodsChecked, active);refresh();
        }
    }

    private void updateSummary() {
        if (refreshing || session == null || session.role() != Role.CONSUMER) return;
        BigDecimal total = new BigDecimal("0.00");int types = 0;int quantity = 0;int unavailable = 0;
        for (Cart.Line line : cart.lines()) if (checkedIds.contains(line.product().id())) {
            types++;quantity += line.quantity();total = total.add(line.subtotal());
            if (!catalog.isListed(line.product().id()) || catalog.stock(line.product().id()) < line.quantity()) unavailable++;
        }
        summary.setText("已选 " + quantity + " 件    合计 ¥" + total + (unavailable == 0 ? "" : "（含不可购买项）"));
        allChecked.setSelected(!cart.isEmpty() && types == cart.lines().size());updateButtons();
    }

    private void updateButtons() {
        if (refreshing || session == null || goodsSelection == null) return;
        goodsSelection.setText("已选 " + goodsChecked.size() + " 种");
        goodsAll.setSelected(!visibleGoods.isEmpty() && visibleGoods.stream().allMatch(item -> goodsChecked.contains(item.product().id())));
        boolean hasActive = false;boolean hasInactive = false;
        for (String id : goodsChecked) {if (catalog.isListed(id)) hasActive = true;else hasInactive = true;}
        batchButton("上架勾选商品", hasInactive);batchButton("下架勾选商品", hasActive);batchButton("加入勾选商品", !goodsChecked.isEmpty());
        JButton remove = actionButtons.get("删除勾选商品");if (remove != null) remove.setEnabled(!checkedIds.isEmpty());
        JButton checkout = actionButtons.get("结算勾选商品");if (checkout != null) {checkout.setEnabled(!checkedIds.isEmpty() && checkedIds.stream().noneMatch(quantityErrors::containsKey));checkout.setToolTipText(checkedIds.stream().anyMatch(quantityErrors::containsKey) ? "先修正已勾选商品的数量" : null);}
    }
    private void batchButton(String name, boolean visible) {JButton button = actionButtons.get(name);if (button != null) {button.setVisible(visible);button.setEnabled(visible);}}

    private record TableView(List<String> ids, List<? extends RowSorter.SortKey> sort, Point scroll) { }
    private TableView remember(JTable table) {
        List<String> ids = new ArrayList<>();for (int row : table.getSelectedRows()) ids.add(table.getValueAt(row, 0).toString());
        Point point = table.getParent() instanceof JViewport viewport ? viewport.getViewPosition() : new Point();
        return new TableView(ids, new ArrayList<>(table.getRowSorter().getSortKeys()), point);
    }
    private void restore(JTable table, TableView view) {
        table.getRowSorter().setSortKeys(view.sort());table.clearSelection();
        for (int row = 0; row < table.getRowCount(); row++) if (view.ids().contains(table.getValueAt(row, 0).toString())) table.addRowSelectionInterval(row, row);
    }
    private void restoreScroll(JScrollPane pane, Point point) {
        SwingUtilities.invokeLater(() -> {if (pane.isShowing()) {JViewport view = pane.getViewport();view.setViewPosition(new Point(0, Math.max(0, Math.min(point.y, view.getViewSize().height - view.getExtentSize().height))));}});
    }

    private void orderDetails() {
        String id = selectedId(orderTable);
        Order order = orderRows.stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
        JTable detail = table("编号", "名称", "销售者", "成交单价", "数量", "实付");
        DefaultTableModel model = (DefaultTableModel) detail.getModel();
        for (Order.Item item : order.items()) model.addRow(new Object[]{item.productId(), item.name(), item.seller(), item.unitPrice(), item.quantity(), item.paid()});
        JScrollPane scroll = new JScrollPane(detail);
        scroll.setPreferredSize(new Dimension(750, 280));
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JLabel("订单 " + order.id() + "  买家：" + order.buyer() + "  合计：" + order.total()), BorderLayout.NORTH);
        panel.add(scroll);
        JOptionPane.showMessageDialog(this, panel, "订单详情", JOptionPane.PLAIN_MESSAGE);
    }

    private String status(String id, int quantity) {
        if (!catalog.isListed(id)) return "已下架";
        if (catalog.stock(id) == 0) return "Sold out";
        if (catalog.stock(id) < quantity) return "库存不足";
        return "在售";
    }

    private void addProduct() {
        JTextField name = new JTextField(22);
        JTextField price = new JTextField(22);
        NumberInput stock = new NumberInput(0, 0, Catalog.MAX_STOCK);
        JPanel panel = form(new String[]{"名称", "单价", "初始库存"}, name, price, stock);
        editForm("添加商品", panel, new String[]{"添加", "取消"}, choice -> {
            if (choice != 0) return true;
            Product product = catalog.add(session, validName(name), validPrice(price), stock.number());
            refresh();
            message("已添加，商品编号：" + product.id());
            return true;
        });
    }

    private void editProduct(String id) {
        Product product = catalog.find(id);
        JTextField name = new JTextField(product.name(), 22);
        JTextField price = new JTextField(product.price().toPlainString(), 22);
        NumberInput stock = new NumberInput(catalog.stock(id), 0, Catalog.MAX_STOCK);
        JPanel panel = form(new String[]{"编号", "名称", "单价", "库存"}, new JLabel(id), name, price, stock);
        boolean active = catalog.isListed(id);
        String[] options = active ? new String[]{"保存修改", "下架商品", "取消"} : new String[]{"保存修改", "重新上架", "取消"};
        editForm("编辑商品", panel, options, choice -> {
            if (choice == 0) {
                catalog.updateProduct(session, id, validName(name), validPrice(price), stock.number());
                refresh();
                return true;
            }
            if (choice != 1) return true;
            if (active && confirm("确认下架 " + id + "？")) {
                catalog.takeDown(session, id);
                refresh();
                return true;
            }
            if (!active && confirm("保存当前名称、价格和库存，并重新上架 " + id + "？")) {
                catalog.relist(session, id, validName(name), validPrice(price), stock.number());
                refresh();
                return true;
            }
            return false;
        });
    }

    private void viewProduct(String id) {
        Product product = catalog.find(id);
        String info = id + "  " + product.name() + "  单价：" + product.price()
                + "  库存：" + catalog.stock(id) + "  " + status(id, 1);
        if (!catalog.isListed(id) || catalog.stock(id) == 0) {
            message(info);
            return;
        }
        NumberInput quantity = new NumberInput(1, 1, 99);
        JPanel panel = form(new String[]{"商品", "购买数量（留空为 1）"}, new JLabel(info), quantity);
        String[] options = {"加入购物车", "立即购买", "取消"};
        editForm("商品详情", panel, options, choice -> {
            if (choice == 0) {
                cart.add(id, quantity.number());
                checkedIds.add(id);
                refresh();
                return true;
            }
            if (choice == 1) {
                Cart purchase = new Cart(catalog, session);
                purchase.add(id, quantity.number());
                return checkout(purchase);
            }
            return true;
        });
    }

    private void editCartItem(String id) {
        Cart.Line selected = null;
        for (Cart.Line line : cart.lines()) {
            if (line.product().id().equals(id)) selected = line;
        }
        if (selected == null) throw new IllegalArgumentException("商品不在购物车中。");
        String state = status(id, selected.quantity());
        if (!catalog.isListed(id) || catalog.stock(id) == 0) {
            if (confirm(selected.product().name() + "  " + state + "，从购物车删除？")) {
                cart.update(id, 0);
                refresh();
            }
            return;
        }
        NumberInput quantity = new NumberInput(selected.quantity(), 0, 99);
        JPanel panel = form(new String[]{"商品", "单价", "当前库存", "购物车数量（0 为删除）"},
                new JLabel(selected.product().name()), new JLabel(selected.product().price().toPlainString()),
                new JLabel(catalog.stock(id) + "  " + state), quantity);
        String[] options = {"保存数量", "删除", "购买该商品", "取消"};
        int originalQuantity = selected.quantity();
        editForm("购物车商品", panel, options, choice -> {
            if (choice == 0) {
                cart.update(id, quantity.number());
                refresh();
                return true;
            }
            if (choice == 1) {
                if (!confirm("从购物车删除 " + id + "？")) return false;
                cart.update(id, 0);
                refresh();
                return true;
            }
            if (choice == 2) {
                int count = quantity.number();
                if (count < 1 || count > originalQuantity) throw new IllegalArgumentException("购买数量须为 1–" + originalQuantity + "，增加数量请先保存修改。");
                Cart purchase = new Cart(catalog, session);
                purchase.add(id, count);
                if (!checkout(purchase)) return false;
                cart.removePurchased(id, count);
                refresh();
            }
            return true;
        });
    }

    private void checkout() {
        if (checkedIds.isEmpty()) throw new IllegalArgumentException("请勾选要结算的商品。");
        if (checkedIds.stream().anyMatch(quantityErrors::containsKey)) throw new IllegalArgumentException("请先修正勾选商品的数量。");
        List<String> ids = new ArrayList<>(checkedIds);
        List<String> unavailable = new ArrayList<>();
        for (String id : ids) if (!catalog.isListed(id) || catalog.stock(id) == 0) unavailable.add(id);
        if (!unavailable.isEmpty()) {
            StringBuilder text = new StringBuilder("以下勾选商品不可购买：\n");
            for (String id : unavailable) text.append(id).append(" ").append(catalog.find(id).name()).append("  ").append(status(id, 1)).append("\n");
            if (!confirm(text + "删除这些商品并继续？")) return;
            for (String id : unavailable) { cart.update(id, 0); checkedIds.remove(id); ids.remove(id); }
            refresh();
            if (ids.isEmpty()) { message("没有剩余的勾选商品。"); return; }
        }
        checkout(cart.selected(session, ids), true);
    }

    private boolean checkout(Cart purchase) {
        return checkout(purchase, false);
    }

    private boolean checkout(Cart purchase, boolean fromCart) {
        if (purchase.isEmpty()) throw new IllegalArgumentException("购物车为空。");
        // 先收集售完和下架项，用户同意后才删除，不能遍历时直接改购物车。
        List<Cart.Line> unavailable = new ArrayList<>();
        StringBuilder list = new StringBuilder("以下商品已售完或下架：\n");
        for (Cart.Line line : purchase.lines()) {
            String id = line.product().id();
            if (!catalog.isListed(id) || catalog.stock(id) == 0) {
                unavailable.add(line);
                list.append(id).append(" ").append(line.product().name()).append(" ×").append(line.quantity()).append("  ").append(status(id, line.quantity())).append("\n");
            }
        }
        if (!unavailable.isEmpty()) {
            if (!confirm(list + "\n删除以上商品并继续结账？")) return false;
            for (Cart.Line line : unavailable) purchase.update(line.product().id(), 0);
            refresh();
            if (purchase.isEmpty()) {
                message("商品已删除，购物车为空。");
                return false;
            }
        }
        // 删除无效项后重新报价；有货但数量不足的商品仍需用户调整。
        Checkout.Receipt quote = new Checkout().quote(purchase);
        StringBuilder text = new StringBuilder();
        for (Cart.Line line : quote.lines()) text.append(line.product().name()).append(" ×").append(line.quantity()).append(" = ").append(line.subtotal()).append("\n");
        if (!confirm(text + "\n应付：" + quote.total() + " 元，确认购买？")) return false;
        // 最终提交还会检查库存和报价，保存成功才扣库存并清空购物车。
        List<String> ids = purchase.lines().stream().map(line -> line.product().id()).toList();
        Order order = fromCart ? catalog.purchaseSelected(session, cart, ids, quote) : catalog.purchase(session, purchase, quote);
        refresh();
        message("购买成功，订单号：" + order.id());
        return true;
    }

    private void help() {
        String text = session.role() == Role.SELLER
                ? "双击商品条目：修改名称、单价和库存，或上架、下架。\n左侧复选框：直接勾选多件商品，勾选后可批量上架或下架。\n添加商品：只填名称、价格和库存，编号自动生成。\n销售订单：双击查看成交明细，旧订单不受后续修改影响。"
                : "商品左侧复选框：直接勾选多件商品，点击加入勾选商品批量加购。\n双击商品：查看详情、单独加购或立即购买。\n购物车数量：可用 − / + 调整，也可以点击中间数字手动输入，按回车或移开焦点生效。\n购物车删除：右侧删除移除单件，底部删除勾选商品移除多件。\n结算：只购买已勾选的商品，售完或下架项会先询问是否删除。\n购买记录：双击订单查看明细。";
        message(text + "\n搜索框支持名称、编号和销售者，右侧可筛选状态。\n刷新保留勾选和滚动位置，退出登录保留本次运行中的购物车。");
    }

    // 单击交给表格处理选中；双击才打开对应商品，右键不触发。
    private void openOnDoubleClick(JTable table, Action action) {
        table.setToolTipText("双击查看订单明细");
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 2) return;
                int row = table.rowAtPoint(event.getPoint());
                if (row < 0) return;
                table.setRowSelectionInterval(row, row);
                execute(action);
            }
        });
    }

    private void execute(Action action) {
        try { action.run(); }
        catch (IllegalArgumentException | IOException | UncheckedIOException | java.text.ParseException e) {
            JOptionPane.showMessageDialog(this, e instanceof java.text.ParseException ? "请输入有效的整数。" : e.getMessage(), "操作失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    // 按钮共用错误提示，取消操作由各自方法直接返回。
    private JButton button(String text, Action action) {
        JButton button = new JButton(text);
        styleButton(button);
        if (text.equals("登录") || text.equals("注册")) primary(button);
        actionButtons.put(text, button);
        button.addActionListener(event -> execute(action));
        return button;
    }

    private void styleButton(JButton button) {
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());button.setOpaque(true);button.setBackground(Color.WHITE);
        button.setForeground(new Color(79, 84, 92));button.setFocusPainted(false);button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(223, 226, 231)), BorderFactory.createEmptyBorder(8, 14, 8, 14)));
    }
    private JButton primary(JButton button) {
        button.setBackground(accent);button.setForeground(Color.WHITE);button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));return button;
    }

    private JCheckBox checkBox(String label) {
        JCheckBox box = new JCheckBox(label);box.setOpaque(false);box.setFocusPainted(false);
        box.setIcon(checkIcon(false));box.setSelectedIcon(checkIcon(true));return box;
    }
    private Icon checkIcon(boolean selected) {
        return new Icon() {
            public int getIconWidth() {return 20;}
            public int getIconHeight() {return 20;}
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(selected ? accent : Color.WHITE);g.fillRoundRect(x + 1, y + 1, 18, 18, 4, 4);
                g.setColor(selected ? accent : new Color(202, 206, 212));g.drawRoundRect(x + 1, y + 1, 18, 18, 4, 4);
                if (selected) {g.setColor(Color.WHITE);g.setStroke(new BasicStroke(2));g.drawLine(x + 5, y + 10, x + 9, y + 14);g.drawLine(x + 9, y + 14, x + 15, y + 6);}g.dispose();
            }
        };
    }
    private JTabbedPane tabbedPane() {
        JTabbedPane tabs = new JTabbedPane();tabs.setOpaque(false);
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected Insets getTabInsets(int placement, int index) {return new Insets(12, 20, 12, 20);}
            @Override protected void paintTabBorder(Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) {
                if (selected) {g.setColor(accent);g.fillRect(x + 8, y + h - 3, w - 16, 3);}
            }
            @Override protected void paintText(Graphics g, int placement, Font font, FontMetrics metrics, int index, String value, Rectangle bounds, boolean selected) {
                g.setFont(selected ? font.deriveFont(Font.BOLD) : font);g.setColor(selected ? accent : muted);
                javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(g, value, -1, bounds.x, bounds.y + metrics.getAscent());
            }
            @Override protected void paintContentBorder(Graphics g, int placement, int selectedIndex) { }
            @Override protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rects, int index, Rectangle icon, Rectangle text, boolean selected) { }
            @Override protected void paintTabBackground(Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) {
                g.setColor(Color.WHITE);g.fillRect(x, y, w, h);if (selected) {g.setColor(accent);g.fillRect(x + 8, y + h - 3, w - 16, 3);}
            }
        });return tabs;
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

    private String validName(JTextField field) {
        String value = field.getText().trim();
        if (value.isEmpty() || value.length() > 60 || value.chars().anyMatch(Character::isISOControl)) {
            field.setBorder(BorderFactory.createLineBorder(Color.RED));
            field.requestFocusInWindow();
            throw new IllegalArgumentException("名称不能为空，最多 60 字，不能含控制字符。");
        }
        field.setBorder(UIManager.getBorder("TextField.border"));
        return value;
    }

    private BigDecimal validPrice(JTextField field) {
        try {
            BigDecimal value = parsePrice(field.getText());
            field.setBorder(UIManager.getBorder("TextField.border"));
            return value;
        } catch (IllegalArgumentException e) {
            field.setBorder(BorderFactory.createLineBorder(Color.RED));
            field.requestFocusInWindow();
            field.selectAll();
            throw e;
        }
    }

    // 保存时先校验，出错只更新提示，不关闭窗口或丢弃输入。
    private void editForm(String title, JPanel fields, String[] options, FormAction action) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(fields);
        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        JLabel error = named(new JLabel(" "), "form-error");
        error.setForeground(new Color(180, 30, 30));
        bottom.add(error, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        for (int i = 0; i < options.length; i++) {
            int choice = i;
            JButton button = new JButton(options[i]);
            styleButton(button);
            if (i == 0) primary(button);
            button.addActionListener(event -> {
                try {
                    error.setText(" ");
                    if (action.apply(choice)) dialog.dispose();
                } catch (IllegalArgumentException | IOException | UncheckedIOException | java.text.ParseException e) {
                    error.setText(e.getMessage());
                    dialog.pack();
                }
            });
            actions.add(button);
            if (i == 0) dialog.getRootPane().setDefaultButton(button);
        }
        bottom.add(actions, BorderLayout.SOUTH);
        content.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(Math.max(420, dialog.getWidth()), dialog.getHeight()));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    @FunctionalInterface private interface FormAction {
        boolean apply(int choice) throws IOException, java.text.ParseException;
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
        if (table.getSelectedRowCount() != 1) throw new IllegalArgumentException("此操作只能处理一件商品，请只选中一行，或双击要操作的商品。");
        // 排序后屏幕行号不等于数据行号，需要转换后再取编号。
        return table.getModel().getValueAt(table.convertRowIndexToModel(row), 0).toString();
    }

    private JTable table(String... columns) { return table(false, columns); }

    private JTable table(boolean checkboxes, String... columns) {
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return checkboxes && column == 0; }
            @Override public Class<?> getColumnClass(int column) {
                if (checkboxes && column == 0) return Boolean.class;
                return getRowCount() == 0 || getValueAt(0, column) == null ? Object.class : getValueAt(0, column).getClass();
            }
        };
        JTable table = new JTable(model) {
            @Override public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component cell = super.prepareRenderer(renderer, row, column);
                cell.setBackground(isRowSelected(row) ? new Color(255, 241, 232) : row % 2 == 0 ? Color.WHITE : new Color(250, 251, 252));
                cell.setForeground(new Color(55, 60, 68));
                if (cell instanceof JLabel label) {
                    label.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
                    label.setHorizontalAlignment(getValueAt(row, column) instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);
                    if ("已完成".equals(getValueAt(row, column))) label.setForeground(new Color(52, 139, 91));
                }
                return cell;
            }
        };
        table.setRowHeight(48);table.setShowGrid(false);table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setPreferredSize(new Dimension(0, 42));table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(new Font(Font.DIALOG, Font.BOLD, 14));
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);table.setAutoCreateRowSorter(true);table.setFillsViewportHeight(true);
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
