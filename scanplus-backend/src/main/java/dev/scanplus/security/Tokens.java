package dev.scanplus.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

final class Tokens {
    private static final SecureRandom RANDOM=new SecureRandom();
    static String create() { byte[] b=new byte[32]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    static boolean same(String a,String b) { return MessageDigest.isEqual(hash(a).getBytes(StandardCharsets.US_ASCII),hash(b).getBytes(StandardCharsets.US_ASCII)); }
}
