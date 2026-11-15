package shop;

import javax.swing.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/** 点进输入框就清空，留空时使用创建控件时的默认值。 */
final class NumberInput extends JSpinner {
    private final int defaultValue;
    private final int minimum;
    private final int maximum;
    private final JFormattedTextField field;

    NumberInput(int defaultValue, int minimum, int maximum) {
        super(new SpinnerNumberModel(defaultValue, minimum, maximum, 1));
        this.defaultValue = defaultValue;
        this.minimum = minimum;
        this.maximum = maximum;
        setEditor(new NumberEditor(this, "0"));
        field = ((DefaultEditor) getEditor()).getTextField();
        field.setFocusLostBehavior(JFormattedTextField.PERSIST);
        field.setToolTipText("留空时使用 " + defaultValue);
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) {
                field.setText("");
            }
            @Override public void focusLost(FocusEvent event) {
                if (field.getText().trim().isEmpty()) field.setValue(defaultValue);
            }
        });
    }

    int number() {
        String text = field.getText().trim();
        if (text.isEmpty()) return defaultValue;
        if (!text.matches("[0-9]+")) throw new IllegalArgumentException("请输入整数。");
        int value;
        try { value = Integer.parseInt(text); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("输入的数字过大。"); }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("数量须为 " + minimum + "–" + maximum + "。");
        }
        return value;
    }
}
