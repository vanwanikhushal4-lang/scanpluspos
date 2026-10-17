package com.example.hardware;

/** Input validation only. Does not retain, change, log, or hash any credential. */
final class PinRules {
    private PinRules() { }
    static String error(char[] pin) {
        if (pin.length < 8) return "Use at least 8 digits for your PIN.";
        if (pin.length > 32) return "Use no more than 32 digits for your PIN.";
        for (char digit:pin) if (digit<'0'||digit>'9') return "Use digits 0–9 only.";
        return null;
    }
    static boolean matches(char[] first,char[] confirmation) {
        return java.util.Arrays.equals(first,confirmation);
    }
}
