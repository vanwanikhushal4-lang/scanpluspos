import android.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
/** Temporary synthetic, in-memory crypto check. Never opens app storage or changes device settings. */
public final class VeloxPinCryptoProbe {
    private static byte[] derive(char[] pin,byte[] salt) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(pin,salt,120000,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    public static void main(String[] args) throws Exception {
        SecureRandom random=new SecureRandom(); char[] pin=new char[12];
        for (int i=0;i<pin.length;i++) pin[i]=(char)('0'+random.nextInt(10));
        byte[] salt=new byte[24]; random.nextBytes(salt);
        byte[] original=derive(pin,salt);
        String stored=Base64.encodeToString(original,Base64.NO_WRAP);
        String storedSalt=Base64.encodeToString(salt,Base64.NO_WRAP);
        Arrays.fill(original,(byte)0);
        byte[] expected=Base64.decode(stored,Base64.NO_WRAP);
        byte[] actual=derive(pin,Base64.decode(storedSalt,Base64.NO_WRAP));
        if (!MessageDigest.isEqual(actual,expected)) throw new AssertionError("PIN round trip failed");
        Arrays.fill(actual,(byte)0);
        pin[0]=(char)('0'+((pin[0]-'0'+1)%10));
        byte[] incorrect=derive(pin,Base64.decode(storedSalt,Base64.NO_WRAP));
        if (MessageDigest.isEqual(incorrect,expected)) throw new AssertionError("Wrong PIN accepted");
        Arrays.fill(pin,'\0'); Arrays.fill(incorrect,(byte)0); Arrays.fill(expected,(byte)0); Arrays.fill(salt,(byte)0);
        System.out.println("SYNTHETIC_PIN_SAVE_VERIFY_PASS");
        System.out.println("SYNTHETIC_WRONG_PIN_REJECT_PASS");
        System.out.println("No app credential or protection setting read or changed.");
    }
}
