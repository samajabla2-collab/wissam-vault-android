package com.wissam.smartbridge;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Small local notebook for manually shared snippets. Private application storage, not encryption. */
final class TextVault {
    private static final int LIMIT=80;
    private static File file(Context ctx) { return new File(ctx.getFilesDir(),"wissam_texts.json"); }
    static synchronized JSONArray list(Context ctx) {
        AtomicFile atomic=new AtomicFile(file(ctx));
        if(!file(ctx).isFile())return new JSONArray();
        try(FileInputStream in=atomic.openRead(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192];int n;long bytes=0;
            while((n=in.read(b))!=-1){bytes+=n;if(bytes>2*1024*1024)return new JSONArray();out.write(b,0,n);}
            return new JSONArray(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }catch(Exception ignored){return new JSONArray();}
    }
    /** Return false for an existing message ID, true only after durable local storage. */
    static synchronized boolean add(Context ctx,String id,String text,String direction) throws Exception {
        if(id==null || !id.matches("[0-9a-fA-F]{32}"))throw new IOException("معرف النص غير صالح");
        if(text==null || text.trim().isEmpty() || text.getBytes(StandardCharsets.UTF_8).length>16384)
            throw new IOException("النص فارغ أو يتجاوز 16KB");
        Set<String> hidden=ctx.getSharedPreferences("wissam_text_hide",Context.MODE_PRIVATE)
            .getStringSet("hidden",java.util.Collections.emptySet());
        if(hidden.contains(id))return false;
        JSONArray old=list(ctx);
        for(int i=0;i<old.length();i++){
            JSONObject item=old.optJSONObject(i);
            if(item!=null && id.equals(item.optString("id")))return false;
        }
        JSONArray next=new JSONArray();
        next.put(new JSONObject().put("id",id).put("text",text).put("direction",direction)
            .put("time",System.currentTimeMillis()));
        for(int i=0;i<old.length() && next.length()<LIMIT;i++) {
            JSONObject item=old.optJSONObject(i);if(item!=null)next.put(item);
        }
        AtomicFile atomic=new AtomicFile(file(ctx));
        FileOutputStream stream=null;
        try {
            stream=atomic.startWrite();
            stream.write(next.toString().getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(stream);
        }catch(Exception ex) {
            if(stream!=null)atomic.failWrite(stream);
            throw ex;
        }
        return true;
    }
    static synchronized void clear(Context ctx) throws IOException {
        // Suppress re-importing old messages from the Windows history after a manual cleanup.
        SharedPreferences prefs=ctx.getSharedPreferences("wissam_text_hide",Context.MODE_PRIVATE);
        Set<String> hidden=new HashSet<>(prefs.getStringSet("hidden",java.util.Collections.emptySet()));
        JSONArray items=list(ctx);
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);
            if(item!=null)hidden.add(item.optString("id"));
        }
        if(!prefs.edit().putStringSet("hidden",hidden).commit())throw new IOException("تعذّر حفظ قائمة النصوص المحذوفة");
        new AtomicFile(file(ctx)).delete();
    }
}
