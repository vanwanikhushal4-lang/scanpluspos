package com.example.hardware;
public final class PinRulesTest {
    private static int checks;
    private static void require(boolean value,String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        char[] minimum="70819364".toCharArray();
        char[] original=minimum.clone();
        require(PinRules.error(minimum)==null,"Eight digits must be accepted");
        require(PinRules.error("7081936".toCharArray())!=null,"Seven digits must be rejected explicitly");
        require(PinRules.error(new char[0])!=null,"Empty input must show a validation error");
        require(PinRules.error("01234567890123456789012345678901".toCharArray())==null,"32 digits must be accepted");
        require(PinRules.error("012345678901234567890123456789012".toCharArray())!=null,"33 digits must be rejected");
        require(PinRules.error("7081936a".toCharArray())!=null,"Letters must be rejected");
        require(PinRules.error("70819364 ".toCharArray())!=null,"Whitespace must not silently alter a PIN");
        require(PinRules.matches(minimum,minimum.clone()),"Matching entries must be accepted");
        require(!PinRules.matches(minimum,"70819365".toCharArray()),"Different confirmation must fail");
        require(!PinRules.matches(minimum,"7081936".toCharArray()),"Short confirmation must fail");
        require(java.util.Arrays.equals(minimum,original),"Validation must not erase the entry on failure");
        System.out.println("PIN validation checks passed: "+checks);
    }
}
