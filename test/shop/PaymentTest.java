package shop;
import java.time.YearMonth;
public final class PaymentTest {
    private static int checks;
    private static void check(boolean value) {checks++;if (!value) throw new AssertionError("payment check " + checks);}
    private static boolean valid(String number, String expiry, String cvv) {return PaymentGateway.validateCard(number, expiry, cvv.toCharArray(), YearMonth.of(2026, 11)).isEmpty();}
    public static void main(String[] args) {
        check(valid("4111 1111 1111 1111", "11/26", "123"));check(valid("5555-5555-5555-4444", "12/30", "999"));
        check(!valid("4111111111111112", "12/30", "123"));check(!valid("4000000000000002", "12/30", "123"));check(!valid("4111111111111111", "10/26", "123"));
        check(!valid("4111111111111111", "13/30", "123"));check(!valid("4111111111111111", "1/30", "123"));check(!valid("4111111111111111", "12/30", "abc"));check(!valid("4111111111111111", "12/30", "12"));
        check(PaymentGateway.simulate(PaymentGateway.Status.CANCELLED).reference().isEmpty());check(PaymentGateway.simulate(PaymentGateway.Status.FAILED).reference().isEmpty());
        var success = PaymentGateway.simulate(PaymentGateway.Status.SUCCESS);check(success.reference().startsWith("DEMO-"));check(!success.reference().equals(PaymentGateway.simulate(PaymentGateway.Status.SUCCESS).reference()));
        System.out.println("PASS: " + checks + " demo payment checks");
    }
}
