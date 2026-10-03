package com.yourcompany.ludo.util;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * bKash / Nagad / Rocket "টাকা পেয়েছি" SMS parser.
 * ⚠️ আপনার আসল SMS ফরম্যাট দিয়ে টেস্ট করে regex ঠিক করে নিন।
 */
public final class SmsParser {

    public record Parsed(String method, BigDecimal amount, String trxId, String senderNumber) {}

    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final Pattern AMOUNT = Pattern.compile("(?:Tk|BDT|৳)\\.?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", CI);
    private static final Pattern TRX = Pattern.compile(
            "(?:Trx\\s?ID|Txn\\s?ID|Transaction\\s?ID)\\s*[:\\-]?\\s*([A-Za-z0-9]{6,30})", CI);
    private static final Pattern FROM = Pattern.compile(
            "(?:from|sender)\\s*[:\\-]?\\s*(?:A/C\\s*[:\\-]?\\s*)?(\\+?(?:88)?01[3-9][0-9]{8})", CI);

    private SmsParser() {}

    public static Parsed parse(String sender, String message) {
        if (message == null || message.isBlank()) return null;
        String low = message.toLowerCase();

        // শুধু ইনকামিং টাকা
        boolean incoming = low.contains("received") || low.contains("cash in") || low.contains("cash-in");
        boolean outgoing = low.contains("you have sent") || low.contains("send money")
                || low.contains("payment to") || low.contains("cash out");
        if (!incoming || outgoing) return null;

        String method = detectMethod(sender, low);
        if (method == null) return null;

        Matcher a = AMOUNT.matcher(message);
        Matcher t = TRX.matcher(message);
        if (!a.find() || !t.find()) return null;

        BigDecimal amount;
        try {
            amount = new BigDecimal(a.group(1).replace(",", "")).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }

        String from = null;
        Matcher f = FROM.matcher(message);
        if (f.find()) from = f.group(1).replaceFirst("^\\+?88", "");

        return new Parsed(method, amount, t.group(1).toUpperCase(), from);
    }

    private static String detectMethod(String sender, String lowMsg) {
        String s = sender == null ? "" : sender.toLowerCase();
        if (s.contains("bkash")) return "bkash";
        if (s.contains("nagad")) return "nagad";
        if (s.contains("rocket") || s.contains("16216")) return "rocket";
        if (lowMsg.contains("bkash")) return "bkash";
        if (lowMsg.contains("nagad")) return "nagad";
        if (lowMsg.contains("rocket")) return "rocket";
        return null;
    }
}
