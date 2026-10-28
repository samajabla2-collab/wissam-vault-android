package com.wissam.smartbridge;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class VaultFiles {
    private VaultFiles() {}
    static File folder(Context c) {
        File dir = new File(c.getFilesDir(), "received");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create vault");
        return dir;
    }
    static File file(Context c, String id) {
        if (id == null || !id.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid id");
        return new File(folder(c), id + ".bin");
    }
    static synchronized JSONArray list(Context c) {
        File index = new File(c.getFilesDir(), "vault-index.json");
        if (!index.exists()) return new JSONArray();
        try (FileInputStream in = new FileInputStream(index)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            return new JSONArray(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception ex) { return new JSONArray(); }
    }
    private static void store(Context c, JSONArray items) throws IOException {
        File output = new File(c.getFilesDir(), "vault-index.json");
        File temp = new File(c.getFilesDir(), "vault-index.json.tmp");
        try (FileOutputStream stream = new FileOutputStream(temp)) {
            stream.write(items.toString().getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        }
        if (!temp.renameTo(output)) {
            // renameTo may fail when the target already exists on some file systems.
            if (output.exists() && !output.delete()) throw new IOException("Unable to update index");
            if (!temp.renameTo(output)) throw new IOException("Unable to save index");
        }
    }
    static synchronized void add(Context c, String id, String name, long bytes) throws Exception {
        JSONArray arr = list(c);
        JSONObject item = new JSONObject();
        item.put("id", id); item.put("name", cleanName(name));
        item.put("bytes", bytes); item.put("time", System.currentTimeMillis());
        arr.put(item); store(c, arr);
    }
    static synchronized boolean remove(Context c, String id) throws IOException {
        JSONArray all = list(c), remain = new JSONArray();
        boolean found = false;
        for (int i=0;i<all.length();i++) {
            JSONObject o = all.optJSONObject(i);
            if (o == null) continue;
            if (id.equals(o.optString("id"))) found = true;
            else remain.put(o);
        }
        if (!found) return false;
        File f = file(c,id);
        if (f.exists() && !f.delete()) throw new IOException("Could not delete file");
        store(c,remain); return true;
    }
    static synchronized int clear(Context c) throws IOException {
        JSONArray arr=list(c); int deleted=0;
        for(int i=0;i<arr.length();i++) {
            JSONObject o=arr.optJSONObject(i); if(o==null) continue;
            String id=o.optString("id");
            if (!id.matches("[0-9a-fA-F-]{36}")) continue;
            File f=file(c,id);
            if(!f.exists() || f.delete()) deleted++;
        }
        JSONArray remains=new JSONArray();
        for(int i=0;i<arr.length();i++) {
            JSONObject o=arr.optJSONObject(i); if(o==null) continue;
            String id=o.optString("id");
            if (id.matches("[0-9a-fA-F-]{36}") && file(c,id).exists()) remains.put(o);
        }
        store(c,remains); return deleted;
    }
    static JSONObject find(Context c, String id) {
        JSONArray all=list(c);
        for (int i=0;i<all.length();i++) {
            JSONObject o=all.optJSONObject(i);
            if(o!=null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }
    static String cleanName(String name) {
        if (name==null) return "file";
        name=name.replace('\\','/'); int slash=name.lastIndexOf('/');
        if (slash>=0) name=name.substring(slash+1);
        name=name.replaceAll("[\\x00-\\x1f\\x7f]", "_").trim();
        if(name.length()>160) name=name.substring(name.length()-160);
        return name.length()==0 ? "file" : name;
    }
    static String mime(String name) {
        int i=name.lastIndexOf('.');
        if(i<0) return "application/octet-stream";
        String type=MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(i+1).toLowerCase(java.util.Locale.US));
        return type==null ? "application/octet-stream":type;
    }
    static Uri uri(String id) {
        return new Uri.Builder().scheme("content").authority("com.wissam.smartbridge.vaultfiles")
                .appendPath("file").appendPath(id).build();
    }
    static String newId() { return UUID.randomUUID().toString(); }
}
