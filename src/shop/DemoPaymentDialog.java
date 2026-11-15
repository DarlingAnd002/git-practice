package shop;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;

/** 扫码与银行卡共用的模拟支付窗口。 */
final class DemoPaymentDialog extends JDialog {
    private PaymentGateway.Result result = PaymentGateway.simulate(PaymentGateway.Status.CANCELLED);
    private final JTextField number = new JTextField(22);
    private final JTextField expiry = new JTextField(7);
    private final JTextField holder = new JTextField(22);
    private final JPasswordField cvv = new JPasswordField(5);

    static PaymentGateway.Result open(Window owner, BigDecimal amount) {
        DemoPaymentDialog dialog = new DemoPaymentDialog(owner, amount);dialog.setVisible(true);return dialog.result;
    }

    DemoPaymentDialog(Window owner, BigDecimal amount) {
        super(owner, "模拟支付（Demo payment）", ModalityType.APPLICATION_MODAL);setName("demo-payment");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(12, 16));content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));content.setBackground(Color.WHITE);
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 8));heading.setOpaque(false);
        heading.add(new JLabel("模拟支付 · 不真实扣款（Demo only · No real charge）"));
        JLabel total = new JLabel("应付（Amount）：¥" + amount.toPlainString());total.setFont(total.getFont().deriveFont(Font.BOLD, 22));heading.add(total);content.add(heading, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();tabs.setName("payment-tabs");
        JPanel qr = new JPanel(new BorderLayout(0, 12));qr.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));qr.setBackground(Color.WHITE);
        JComboBox<String> method = new JComboBox<>(new String[]{"微信（WeChat）", "支付宝（Alipay）"});method.setName("qr-method");qr.add(method, BorderLayout.NORTH);
        JLabel code = new JLabel(new DemoQrIcon(false));code.setHorizontalAlignment(SwingConstants.CENTER);code.setName("demo-qr");qr.add(code);
        method.addActionListener(event -> code.setIcon(new DemoQrIcon(method.getSelectedIndex() == 1)));
        qr.add(new JLabel("演示二维码仅包含演示文字，不能付款（Demo text only; not a payment link）"), BorderLayout.SOUTH);tabs.addTab("扫码支付（QR payment）", qr);
        JPanel card = new JPanel(new GridBagLayout());card.setBackground(Color.WHITE);
        number.setName("card-number");expiry.setName("card-expiry");holder.setName("card-holder");cvv.setName("card-cvv");cvv.setEchoChar('*');
        String[] labels = {"持卡人（Name）", "测试卡号（Test number）", "有效期（MM/YY）", "安全码（CVV）"};JComponent[] fields = {holder, number, expiry, cvv};
        for (int i = 0; i < fields.length; i++) {
            GridBagConstraints c = new GridBagConstraints();c.gridx = 0;c.gridy = i;c.anchor = GridBagConstraints.WEST;c.insets = new Insets(12, 12, 12, 18);card.add(new JLabel(labels[i]), c);
            c.gridx = 1;c.weightx = 1;c.fill = GridBagConstraints.HORIZONTAL;card.add(fields[i], c);
        }
        GridBagConstraints tip = new GridBagConstraints();tip.gridx = 0;tip.gridy = 4;tip.gridwidth = 2;tip.insets = new Insets(16, 12, 16, 12);
        card.add(new JLabel("<html>仅使用测试数据（Test data only）：<br>4111 1111 1111 1111 / 5555 5555 5555 4444<br>填写未过期 MM/YY 和任意 3 位数字 CVV。<br>(Use a future expiry and any 3-digit CVV. Nothing is saved.)</html>"), tip);tabs.addTab("银行卡（Card）", card);content.add(tabs);
        JPanel footer = new JPanel(new BorderLayout(0, 12));footer.setOpaque(false);JLabel error = new JLabel(" ");error.setName("payment-error");error.setForeground(new Color(180, 45, 35));footer.add(error, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));actions.setOpaque(false);
        JButton help = new JButton("？");help.addActionListener(event -> JOptionPane.showMessageDialog(this, "选择扫码或测试银行卡，再选择模拟成功、失败或取消。银行卡仅做格式、Luhn、有效期和测试卡号检查。\n(Choose QR or test card, then simulate success, failure or cancel. No funds are transferred.)", "支付帮助（Payment help）", JOptionPane.INFORMATION_MESSAGE));actions.add(help);
        JButton success = new JButton("模拟成功（Simulate success）");success.setName("payment-success");success.addActionListener(event -> {
            if (tabs.getSelectedIndex() == 1) {
                char[] securityCode = cvv.getPassword();String problem;
                try {problem = PaymentGateway.validateCard(number.getText(), expiry.getText().trim(), securityCode, YearMonth.now());}finally {Arrays.fill(securityCode, '\0');}
                if (holder.getText().isBlank()) problem = "请填写测试持卡人（Enter a test name）";
                if (!problem.isEmpty()) {error.setText(problem);return;}
            }
            result = PaymentGateway.simulate(PaymentGateway.Status.SUCCESS);dispose();
        });actions.add(success);
        JButton fail = new JButton("模拟失败（Simulate failure）");fail.setName("payment-failure");fail.addActionListener(event -> {result = PaymentGateway.simulate(PaymentGateway.Status.FAILED);dispose();});actions.add(fail);
        JButton cancel = new JButton("取消（Cancel）");cancel.setName("payment-cancel");cancel.addActionListener(event -> dispose());actions.add(cancel);footer.add(actions, BorderLayout.SOUTH);content.add(footer, BorderLayout.SOUTH);
        setContentPane(content);getRootPane().registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();setMinimumSize(new Dimension(790, 610));setSize(820, 650);setLocationRelativeTo(owner);
    }
    @Override public void dispose() {
        number.setText("");expiry.setText("");holder.setText("");cvv.setText("");super.dispose();
    }
}
