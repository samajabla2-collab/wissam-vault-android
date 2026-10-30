package com.wissam.smartbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** The one-time pairing token is protected by the Android Keystore and is not backed up. */
final class TokenStore {
    private static final String ALIAS = "wissam_bridge_pairing_v1";
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("wissam_bridge", Context.MODE_PRIVATE);
    }
    static String host(Context c) { return prefs(c).getString("host", ""); }
    static void host(Context c, String h) { prefs(c).edit().putString("host", h).apply(); }
    static String read(Context c) {
        String value = prefs(c).getString("token", "");
        if (value.isEmpty()) return "";
        try {
            String[] parts=value.split(":",2);
            byte[] iv=Base64.decode(parts[0],Base64.NO_WRAP);
            byte[] encrypted=Base64.decode(parts[1],Base64.NO_WRAP);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }
    static void write(Context c,String token) throws Exception {
        if (!token.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("Bad pairing token");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        String value=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+
            Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        prefs(c).edit().putString("token",value).commit();
    }
    static void forget(Context c) { prefs(c).edit().remove("token").apply(); }
    private static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        java.security.Key old=ks.getKey(ALIAS,null);
        if(old instanceof SecretKey) return (SecretKey)old;
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return gen.generateKey();
    }
}
