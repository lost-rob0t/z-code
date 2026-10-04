package actor.starintel.zcode.remote;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Random;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Same dependency-free suite runs under javac and the Android Gradle JUnit task. */
public final class CoreSpec {
    private static int assertions;
    private CoreSpec() {}
    private static void check(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("Check " + assertions + " failed");
    }
    private static void rejected(String value) {
        try { LinkPolicy.parse(value); throw new AssertionError("Accepted invalid URL"); }
        catch (IllegalArgumentException error) {
            check(!error.getMessage().contains("SENSITIVE"));
        }
    }
    public static int run() throws Exception {
        assertions = 0;
        LinkPolicy.Link relay = LinkPolicy.parse("https://zcode.z.ai/remote?token=SENSITIVE#SENSITIVE");
        check(relay.url.endsWith("?token=SENSITIVE#SENSITIVE"));
        check(relay.origin.equals("https://zcode.z.ai"));
        check(!relay.toString().contains("SENSITIVE"));
        check(LinkPolicy.sameOrigin(relay, "HTTPS://ZCODE.Z.AI:443/other?x=1"));
        check(!LinkPolicy.sameOrigin(relay, "https://zcode.z.ai.evil.example/"));
        check(!LinkPolicy.sameOrigin(relay, "https://evil.example/zcode.z.ai"));
        check(!LinkPolicy.sameOrigin(relay, "https://zcode.z.ai:8443/"));
        check(!LinkPolicy.sameOrigin(relay, "http://zcode.z.ai/"));
        check(!LinkPolicy.sameOrigin(null, "https://zcode.z.ai/"));
        check(LinkPolicy.parse("https://desktop.example.ts.net:8443/").origin.endsWith(":8443"));
        check(LinkPolicy.parse("https://[::1]:8443/").origin.equals("https://[::1]:8443"));
        check(LinkPolicy.parse(" https://127.0.0.1/ ").origin.equals("https://127.0.0.1"));
        for (String invalid : new String[]{null, "", " ", "http://host/", "javascript:alert(1)",
                "file:///etc/passwd", "content://secret", "intent://remote", "data:text/html,test",
                "//host/", "https:///", "https://user:SENSITIVE@host/", "https://SENSITIVE@host/",
                "https://host:0/", "https://host:65536/", "https://host:-1/", "https://host:/",
                "https://host\\@evil.example/", "https://host/a b", "https://host/\nSENSITIVE",
                "https://host/\tSENSITIVE", "https://host/\u007f", "https://éxample.com/",
                "https://host/%Q0", "https://[fe80::1%25wlan0]/", "https://ho%73t/",
                "https://host/" + "x".repeat(LinkPolicy.MAX_URL_LENGTH)}) rejected(invalid);
        Profile profile = new Profile("f7460118-740e-4f63-9fb5-164de3bb5fbd", "Laptop", Profile.Mode.DESKTOP_ATTACH, relay.url);
        check(!profile.toString().contains("SENSITIVE"));
        check(profile.modeLabel().equals("Desktop attach"));
        try { new Profile(profile.id, "\n", profile.mode, relay.url); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { check(true); }
        try { new Profile(profile.id, "x".repeat(65), profile.mode, relay.url); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { check(true); }
        SessionState session = new SessionState();
        check(session.phase() == SessionState.Phase.IDLE);
        long first = session.begin(); session.fail(first); session.ready(first);
        check(session.phase() == SessionState.Phase.FAILED);
        long second = session.begin(); session.fail(first); session.ready(first);
        check(session.phase() == SessionState.Phase.LOADING);
        session.ready(second); check(session.phase() == SessionState.Phase.PAGE_READY);
        session.fail(second); check(session.phase() == SessionState.Phase.FAILED);
        session.close(); session.ready(second); session.fail(second);
        check(session.phase() == SessionState.Phase.CLOSED);
        check(!session.accepts(second));
        long third = session.begin(); check(third > second); session.ready(third);
        check(session.phase() == SessionState.Phase.PAGE_READY);
        KeyGenerator generator = KeyGenerator.getInstance("AES"); generator.init(256);
        SecretKey key = generator.generateKey();
        byte[] plain = ("{\"url\":\"" + relay.url + "\"}").getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = VaultCipher.encrypt(key, plain);
        check(Arrays.equals(plain, VaultCipher.decrypt(key, encrypted)));
        check(!Arrays.equals(encrypted, VaultCipher.encrypt(key, plain)));
        check(!new String(encrypted, StandardCharsets.ISO_8859_1).contains("SENSITIVE"));
        for (int index : new int[]{0, 4, 16, encrypted.length - 1}) {
            byte[] tampered = encrypted.clone(); tampered[index] ^= 1;
            decryptRejected(key, tampered);
        }
        decryptRejected(generator.generateKey(), encrypted);
        decryptRejected(key, new byte[0]);
        decryptRejected(key, Arrays.copyOf(encrypted, 20));
        decryptRejected(key, new byte[VaultCipher.MAX_PLAINTEXT + 33]);
        byte[] maximum = new byte[VaultCipher.MAX_PLAINTEXT];
        check(Arrays.equals(maximum, VaultCipher.decrypt(key, VaultCipher.encrypt(key, maximum))));
        try { VaultCipher.encrypt(key, new byte[VaultCipher.MAX_PLAINTEXT + 1]); throw new AssertionError(); }
        catch (GeneralSecurityException expected) { check(true); }
        // Bounded deterministic fuzzing: no raw input can enter thrown error text.
        Random random = new Random(20261004L);
        for (int n = 0; n < 512; n++) {
            StringBuilder candidate = new StringBuilder("https://host/");
            for (int i = 0; i < 32; i++) candidate.append((char) random.nextInt(160));
            try {
                LinkPolicy.Link parsed = LinkPolicy.parse(candidate.toString());
                check(parsed.origin.equals("https://host"));
            } catch (IllegalArgumentException error) { check(!error.getMessage().contains(candidate)); }
        }
        return assertions;
    }
    private static void decryptRejected(SecretKey key, byte[] data) throws Exception {
        try { VaultCipher.decrypt(key, data); throw new AssertionError("Accepted invalid ciphertext"); }
        catch (GeneralSecurityException expected) { check(true); }
    }
    public static void main(String[] args) throws Exception { System.out.println("PASS: " + run() + " core checks"); }
}
