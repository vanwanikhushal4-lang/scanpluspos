package com.example.hardware;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.os.SystemClock;
import android.provider.Settings;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Salted PIN verifier in private, non-backed-up app storage. Never stores a PIN. */
final class AdminPin {
    private static final int ITERATIONS = 120000;
    private final SharedPreferences prefs;
    private final int bootCount;
    AdminPin(Context context) { this(context, "administrator"); }
    AdminPin(Context context, String storage) {
        prefs = context.getSharedPreferences(storage, Context.MODE_PRIVATE);
        bootCount = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, 0);
    }
    boolean configured() { return prefs.contains("verifier"); }
    private long delay(int failures) { return failures < 5 ? 0 : Math.min(300000L, 30000L * (1L << Math.min(4, failures - 5))); }
    synchronized long waitMillis() {
        long now=SystemClock.elapsedRealtime(); int failures=prefs.getInt("failures",0);
        if (failures>=5 && (prefs.getInt("retry_boot",bootCount)!=bootCount || now<prefs.getLong("retry_recorded",0))) {
            prefs.edit().putInt("retry_boot",bootCount).putLong("retry_recorded",now).putLong("retry_after",now+delay(failures)).commit();
        }
        return Math.max(0,prefs.getLong("retry_after",0)-now);
    }
    private byte[] derive(char[] pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, ITERATIONS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    void create(char[] pin) throws Exception {
        try {
            String error=PinRules.error(pin);
            if (error!=null) throw new IllegalArgumentException(error);
            byte[] salt = new byte[24]; new SecureRandom().nextBytes(salt);
            byte[] verifier = derive(pin, salt);
            if (!prefs.edit().putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("verifier", Base64.encodeToString(verifier, Base64.NO_WRAP))
                .putInt("failures", 0).putLong("retry_after", 0).commit()) throw new IllegalStateException("PIN could not be saved.");
            Arrays.fill(verifier, (byte) 0);
        } finally { Arrays.fill(pin, '\0'); }
    }
    synchronized boolean verify(char[] pin) throws Exception {
        try {
            if (!configured() || waitMillis() > 0) return false;
            byte[] salt = Base64.decode(prefs.getString("salt", ""), Base64.NO_WRAP);
            byte[] actual = derive(pin, salt);
            byte[] expected = Base64.decode(prefs.getString("verifier", ""), Base64.NO_WRAP);
            boolean same = MessageDigest.isEqual(actual, expected);
            Arrays.fill(actual, (byte) 0); Arrays.fill(expected, (byte) 0);
            int failures = same ? 0 : prefs.getInt("failures", 0) + 1;
            long now=SystemClock.elapsedRealtime();
            prefs.edit().putInt("failures", failures).putInt("retry_boot",bootCount).putLong("retry_recorded",now)
                .putLong("retry_after",same?0:now+delay(failures)).commit();
            return same;
        } finally { Arrays.fill(pin, '\0'); }
    }
}
