package actor.starintel.zcode.remote;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Versioned authenticated envelope. Android supplies a non-exportable Keystore key. */
public final class VaultCipher {
    private static final byte[] MAGIC = {'Z', 'C', 'R', '1'};
    private static final byte[] AAD = "actor.starintel.zcode.remote/profiles/v1".getBytes(StandardCharsets.UTF_8);
    public static final int MAX_PLAINTEXT = 96 * 1024;
    private static final int IV_LENGTH = 12;
    private static final int HEADER_LENGTH = 4 + IV_LENGTH;
    private VaultCipher() {}

    public static byte[] encrypt(SecretKey key, byte[] plaintext) throws GeneralSecurityException {
        if (plaintext.length > MAX_PLAINTEXT) throw new GeneralSecurityException("Vault limit exceeded");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        cipher.updateAAD(AAD);
        byte[] iv = cipher.getIV();
        if (iv.length != IV_LENGTH) throw new GeneralSecurityException("Unsupported IV length");
        byte[] encrypted = cipher.doFinal(plaintext);
        byte[] envelope = new byte[HEADER_LENGTH + encrypted.length];
        System.arraycopy(MAGIC, 0, envelope, 0, MAGIC.length);
        System.arraycopy(iv, 0, envelope, MAGIC.length, iv.length);
        System.arraycopy(encrypted, 0, envelope, HEADER_LENGTH, encrypted.length);
        return envelope;
    }

    public static byte[] decrypt(SecretKey key, byte[] envelope) throws GeneralSecurityException {
        if (envelope.length < HEADER_LENGTH + 16 || envelope.length > MAX_PLAINTEXT + HEADER_LENGTH + 16
                || !Arrays.equals(MAGIC, Arrays.copyOf(envelope, MAGIC.length))) {
            throw new GeneralSecurityException("Invalid vault envelope");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(128, Arrays.copyOfRange(envelope, MAGIC.length, HEADER_LENGTH)));
        cipher.updateAAD(AAD);
        return cipher.doFinal(envelope, HEADER_LENGTH, envelope.length - HEADER_LENGTH);
    }
}
