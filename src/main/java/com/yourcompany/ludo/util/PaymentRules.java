package com.yourcompany.ludo.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.regex.Pattern;

public final class PaymentRules {

    private PaymentRules() {}

    // নতুন মেথড দরকার হলে এখানে যোগ করুন
    private static final Set<String> METHODS = Set.of("BKASH", "NAGAD", "ROCKET");
    private static final Pattern NUMBER = Pattern.compile("^01[3-9]\\d{8}$");

    public static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Method required");
        }
        String v = method.trim().toUpperCase();
        if (!METHODS.contains(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported method: " + method);
        }
        return v;
    }

    public static String normalizeNumber(String number) {
        if (number == null || !NUMBER.matcher(number.trim()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid mobile number");
        }
        return number.trim();
    }
}
