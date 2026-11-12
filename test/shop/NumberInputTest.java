package shop;

import javax.swing.*;
import java.awt.event.FocusEvent;

public final class NumberInputTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            NumberInput input = new NumberInput(1, 1, 99);
            JFormattedTextField field = ((JSpinner.DefaultEditor) input.getEditor()).getTextField();
            check(field.getText().equals("1"));
            focus(field, FocusEvent.FOCUS_GAINED);
            check(field.getText().isEmpty());
            field.replaceSelection("2");
            check(field.getText().equals("2") && input.number() == 2);
            focus(field, FocusEvent.FOCUS_LOST);
            check(input.number() == 2);
            focus(field, FocusEvent.FOCUS_GAINED);
            check(field.getText().isEmpty() && input.number() == 1);
            focus(field, FocusEvent.FOCUS_LOST);
            check(field.getText().equals("1") && input.number() == 1);
            field.setText("99"); check(input.number() == 99);
            field.setText("100"); reject(input);
            field.setText("0"); reject(input);
            field.setText("-1"); reject(input);
            field.setText("2.5"); reject(input);
            field.setText("abc"); reject(input);
            field.setText("999999999999999"); reject(input);
            field.setText("  "); check(input.number() == 1);
            input.setValue(3); check(input.number() == 3);
            NumberInput edit = new NumberInput(1, 0, 99);
            ((JSpinner.DefaultEditor) edit.getEditor()).getTextField().setText("0");
            check(edit.number() == 0);
            NumberInput stock = new NumberInput(50, 0, Catalog.MAX_STOCK);
            JFormattedTextField stockField = ((JSpinner.DefaultEditor) stock.getEditor()).getTextField();
            focus(stockField, FocusEvent.FOCUS_GAINED);
            check(stockField.getText().isEmpty() && stock.number() == 50);
            stockField.setText("2"); check(stock.number() == 2);
            focus(stockField, FocusEvent.FOCUS_LOST);
            field.getCaret().setBlinkRate(0);
            stockField.getCaret().setBlinkRate(0);
        });
        System.out.println("PASS: " + checks + " number-input checks");
    }
    private static void focus(JFormattedTextField field, int id) {
        FocusEvent event = new FocusEvent(field, id);
        for (var listener : field.getFocusListeners()) {
            if (id == FocusEvent.FOCUS_GAINED) listener.focusGained(event);
            else listener.focusLost(event);
        }
    }
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Number input check failed: " + checks);
    }
    private static void reject(NumberInput input) {
        checks++;
        try { input.number(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid input accepted");
    }
}
