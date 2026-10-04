package actor.starintel.zcode.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Bounded single-owner mailbox: all vault IO and read/modify/write happen on one worker. */
public final class VaultActor implements AutoCloseable {
    public static final int MAX_PROFILES = 8;
    private static final String ALIAS = "zcode.remote.profiles.v1";
    private final AtomicFile file;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), new ThreadPoolExecutor.AbortPolicy());
    private volatile boolean closed;
    public interface Result { void receive(List<Profile> profiles, boolean failed); }

    public VaultActor(Context context) {
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "profiles.vault"));
    }
    public void load(Result result) { submit(this::read, result); }
    public void add(Profile profile, Result result) {
        submit(() -> {
            List<Profile> profiles = read();
            if (profiles.size() >= MAX_PROFILES) throw new IllegalStateException("Profile limit reached");
            profiles.add(profile);
            write(profiles);
            return profiles;
        }, result);
    }
    public void remove(String id, Result result) {
        submit(() -> {
            List<Profile> profiles = read();
            profiles.removeIf(profile -> profile.id.equals(id));
            write(profiles);
            return profiles;
        }, result);
    }
    private void submit(Callable<List<Profile>> operation, Result result) {
        if (closed) return;
        try {
            worker.execute(() -> {
                try { deliver(result, operation.call(), false); }
                catch (Exception error) { deliver(result, new ArrayList<>(), true); }
            });
        } catch (RejectedExecutionException error) { deliver(result, new ArrayList<>(), true); }
    }
    private void deliver(Result result, List<Profile> profiles, boolean failed) {
        main.post(() -> { if (!closed) result.receive(profiles, failed); });
    }
    private SecretKey key(boolean create) throws Exception {
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        if (keys.containsAlias(ALIAS)) return (SecretKey) keys.getKey(ALIAS, null);
        if (!create) throw new GeneralSecurityException("Vault key unavailable");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    private List<Profile> read() throws Exception {
        List<Profile> profiles = new ArrayList<>();
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return profiles;
        byte[] encrypted;
        try (FileInputStream stream = file.openRead()) {
            long size = stream.getChannel().size();
            if (size < 32 || size > VaultCipher.MAX_PLAINTEXT + 32) throw new GeneralSecurityException("Vault size invalid");
            encrypted = new byte[(int) size];
            int offset = 0;
            while (offset < encrypted.length) {
                int count = stream.read(encrypted, offset, encrypted.length - offset);
                if (count <= 0) throw new GeneralSecurityException("Vault truncated");
                offset += count;
            }
        }
        byte[] plaintext = VaultCipher.decrypt(key(false), encrypted);
        try {
            JSONArray data = new JSONArray(new String(plaintext, StandardCharsets.UTF_8));
            if (data.length() > MAX_PROFILES) throw new GeneralSecurityException("Vault profile limit exceeded");
            for (int i = 0; i < data.length(); i++) {
                JSONObject row = data.getJSONObject(i);
                profiles.add(new Profile(row.getString("id"), row.getString("name"),
                        Profile.Mode.valueOf(row.getString("mode")), row.getString("url")));
            }
        } finally { Arrays.fill(plaintext, (byte) 0); }
        return profiles;
    }
    private void write(List<Profile> profiles) throws Exception {
        JSONArray data = new JSONArray();
        for (Profile profile : profiles) {
            data.put(new JSONObject().put("id", profile.id).put("name", profile.name)
                    .put("mode", profile.mode.name()).put("url", profile.link.url));
        }
        byte[] plaintext = data.toString().getBytes(StandardCharsets.UTF_8);
        byte[] encrypted;
        try { encrypted = VaultCipher.encrypt(key(true), plaintext); }
        finally { Arrays.fill(plaintext, (byte) 0); }
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            stream.write(encrypted);
            file.finishWrite(stream);
        } catch (Exception error) {
            if (stream != null) file.failWrite(stream);
            throw error;
        }
    }
    @Override public void close() { closed = true; worker.shutdown(); }
}
