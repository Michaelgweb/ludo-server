package com.yourcompany.ludo.util;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** bKash / Nagad / Rocket / Upay-এর "টাকা পেয়েছেন" SMS পার্স করে */
public final class SmsParser {

    public record Parsed(String method, String trxId, BigDecimal amount, String senderNumber) {}

    private static final Pattern AMOUNT = Pattern.compile(
            "(?i)(?:tk\\.?|bdt|৳)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)");
    private static final Pattern TRX = Pattern.compile(
            "(?i)(?:trx\\s*id|txn\\s*id|transaction\\s*id|trxid|txnid)\\s*[:\\-]?\\s*([A-Za-z0-9]{6,30})");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?88)?(01[3-9]\\d{8})(?!\\d)");

    private SmsParser() {}

    public static Parsed parse(String sender, String message) {
        if (message == null || message.isBlank()) return null;

        String s = sender == null ? "" : sender.toLowerCase();
        String msg = message.toLowerCase();

        // শুধু "টাকা পেয়েছেন" টাইপ SMS (cash out / send money / bill ইত্যাদি বাদ)
        if (!(msg.contains("received") || msg.contains("cash in") || msg.contains("পেয়েছেন"))) return null;

        String method = detectMethod(s, msg);
        if (method == null) return null;

        Matcher a = AMOUNT.matcher(message);
        Matcher t = TRX.matcher(message);
        if (!a.find() || !t.find()) return null;

        BigDecimal amount;
        try {
            amount = new BigDecimal(a.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        if (amount.signum() <= 0) return null;

        Matcher p = PHONE.matcher(message);
        String from = p.find() ? p.group(1) : null;

        return new Parsed(method, t.group(1).toUpperCase(), amount, from);
    }

    private static String detectMethod(String sender, String msg) {
        if (sender.contains("bkash") || msg.contains("bkash")) return "BKASH";
        if (sender.contains("nagad") || msg.contains("nagad")) return "NAGAD";
        if (sender.contains("rocket") || sender.equals("16216") || msg.contains("rocket")) return "ROCKET";
        if (sender.contains("upay") || msg.contains("upay")) return "UPAY";
        return null;
    }
}
