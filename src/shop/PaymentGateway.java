package shop;

import java.time.YearMonth;
import java.util.UUID;

/** 课堂演示，不连接银行或收款平台。 */
final class PaymentGateway {
    enum Status {SUCCESS, FAILED, CANCELLED}
    record Result(Status status, String reference) { }

    static Result simulate(Status status) {
        return new Result(status, status == Status.SUCCESS ? "DEMO-" + UUID.randomUUID() : "");
    }

    static String validateCard(String number, String expiry, char[] cvv, YearMonth now) {
        String digits = number.replace(" ", "").replace("-", "");
        if (!digits.matches("[0-9]{16}")) return "请输入 16 位测试卡号（Enter a 16-digit test number）";
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(digits.length() - 1 - i) - '0';
            if (i % 2 == 1) {digit *= 2;if (digit > 9) digit -= 9;}
            sum += digit;
        }
        if (sum % 10 != 0) return "卡号校验失败（Invalid card checksum）";
        if (!digits.equals("4111111111111111") && !digits.equals("5555555555554444")) return "仅允许界面列出的测试卡号（Use the listed test cards only）";
        if (!expiry.matches("(0[1-9]|1[0-2])/[0-9]{2}")) return "有效期格式为 MM/YY（Use MM/YY）";
        YearMonth date = YearMonth.of(2000 + Integer.parseInt(expiry.substring(3)), Integer.parseInt(expiry.substring(0, 2)));
        if (date.isBefore(now)) return "测试卡已过期（Card expired）";
        if (cvv.length != 3) return "安全码为 3 位数字（Use a 3-digit security code）";
        for (char c : cvv) if (c < '0' || c > '9') return "安全码为 3 位数字（Use a 3-digit security code）";
        return "";
    }
}
