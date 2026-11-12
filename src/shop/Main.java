package shop;

import javax.swing.*;
import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) {
        // Swing 组件在事件线程中创建。
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                AuthService auth = new AuthService(Path.of("data", "users.properties"));
                Catalog catalog = new Catalog(Path.of("data", "store.properties"));
                new ShopWindow(auth, catalog).setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "无法启动：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
