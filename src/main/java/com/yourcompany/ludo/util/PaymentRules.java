package com.yourcompany.ludo.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

public final class PaymentRules {

    public static final Set<String> METHODS = Set.of("BKASH", "NAGAD", "ROCKET", "UPAY");

    private PaymentRules() {}

    public static String normalizeMethod(String method) {
        String m = method == null ? "" : method.trim().toUpperCase().replaceAll("[\\s_-]", "");
        if (!METHODS.contains(m)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "মেথড সঠিক নয় (bKash, Nagad, Rocket, Upay)");
        }
        return m;
    }

    /** +8801XXXXXXXXX / 8801XXXXXXXXX / 01XXXXXXXXX -> 01XXXXXXXXX */
    public static String normalizeNumber(String number) {
        String n = number == null ? "" : number.replaceAll("[^0-9]", "");
        if (n.startsWith("880")) n = n.substring(2);
        if (!n.matches("01[3-9]\\d{8}")) {
            // Rocket নম্বরের শেষে একটা বাড়তি ডিজিট থাকে (১২ ডিজিট), সেটাও গ্রহণ করি
            if (!n.matches("01[3-9]\\d{9}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "নম্বর সঠিক নয়");
            }
        }
        return n;
    }
}
