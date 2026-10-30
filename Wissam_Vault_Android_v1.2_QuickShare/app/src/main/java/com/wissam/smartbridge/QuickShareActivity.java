package com.wissam.smartbridge;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explicit ACTION_SEND/ACTION_SEND_MULTIPLE share target. No filesystem watcher and
 * no attempt to identify which application originated a download. Only content
 * intentionally shared here by the user is ever transferred.
 */
public final class QuickShareActivity extends Activity {
    private static final int PORT=18764;
    private static final long MAX_BYTES=1024L*1024*1024;
    private static final int MAX_MESSAGE_BYTES=16384;
    private final ArrayList<Uri> files=new ArrayList<>();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private TextView state,details;
    private ProgressBar progress;
    private LinearLayout area;
    private String sharedText=null,host="",token="";
    private boolean active=false;

    private int dp(float n) {return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private TextView text(String s,int size,int color,boolean bold) {
        TextView v=new TextView(this);v.setText(s);v.setTextColor(color);v.setTextSize(size);
        v.setGravity(Gravity.RIGHT); v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setPadding(0,dp(5),0,dp(5));return v;
    }
    private Button button(String s,Runnable click) {
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);
        b.setBackground(shape(0xff087c83,13));b.setMinHeight(dp(50));b.setOnClickListener(v->click.run());return b;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xff102a44);
        getWindow().setNavigationBarColor(0xff102a44);
        host=TokenStore.host(this);token=TokenStore.read(this);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);scroll.setBackgroundColor(0xfff4f8fc);
        area=new LinearLayout(this);area.setOrientation(LinearLayout.VERTICAL);area.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        area.setPadding(dp(20),dp(24),dp(20),dp(20));scroll.addView(area);setContentView(scroll);
        TextView title=text("↗ مشاركة مباشرة إلى جسر وسام",24,0xff102a44,true);area.addView(title);
        area.addView(text("يُرسل فقط ما اخترت مشاركته. لا نراقب تنزيلات الجوال ولا ملفات التطبيقات الأخرى.",14,0xff5e6f86,false));
        state=text("جارٍ تجهيز المشاركة...",17,0xff102a44,true);area.addView(state);
        details=text("",13,0xff5e6f86,false);area.addView(details);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(9));p.topMargin=dp(14);area.addView(progress,p);
        parseShare(getIntent());
        if(files.isEmpty() && (sharedText==null || sharedText.trim().isEmpty())) {
            showError("لم يصل ملف أو نص من قائمة المشاركة. افتح ChatGPT واختر مشاركة ملف أو نص.");return;
        }
        if(token.isEmpty() || host.isEmpty())showPairForm();else startSending();
    }
    private void parseShare(Intent intent) {
        if(intent==null)return;
        String a=intent.getAction();
        if(Intent.ACTION_SEND.equals(a) || Intent.ACTION_SEND_MULTIPLE.equals(a)) {
            if(Intent.ACTION_SEND.equals(a)) {
                Uri u=intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if(u!=null)files.add(u);
            } else {
                ArrayList<Uri> list=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if(list!=null)files.addAll(list);
            }
            ClipData clips=intent.getClipData();
            if(files.isEmpty() && clips!=null)for(int i=0;i<clips.getItemCount();i++){
                Uri u=clips.getItemAt(i).getUri();if(u!=null)files.add(u);
            }
            if(files.isEmpty()) {
                CharSequence t=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                if(t!=null)sharedText=t.toString();
            }
        }
    }
    private void update(String title,String subtitle,int percent) {
        ui.post(()->{state.setText(title);details.setText(subtitle);progress.setProgress(Math.max(0,Math.min(100,percent)));});
    }
    private void showPairForm() {
        state.setText("اقتران أول مرة");details.setText("أدخل عنوان الكمبيوتر ورمز الاقتران من نافذة جسر وسام؛ لن تحتاج إليهما في المشاركات التالية.");
        EditText ip=new EditText(this);ip.setSingleLine(true);ip.setText(host);
        ip.setHint("عنوان الكمبيوتر، مثل 192.168.1.105");ip.setTextDirection(View.TEXT_DIRECTION_LTR);
        ip.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);area.addView(ip);
        EditText pin=new EditText(this);pin.setSingleLine(true);pin.setHint("رمز الاقتران: 8 أرقام");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);area.addView(pin);
        Button pair=button("اقتران وإرسال مباشرة",()->{
            String ipValue=ip.getText().toString().trim();String pinValue=pin.getText().toString().trim();
            if(!privateIp(ipValue) || !pinValue.matches("[0-9]{8}")) {
                update("تحقق من العنوان والرمز","اكتب عنوان Wi-Fi المحلي و8 أرقام من الكمبيوتر.",0);return;
            }
            ip.setEnabled(false);pin.setEnabled(false);
            new Thread(()->{
                try {
                    HttpURLConnection c=request(ipValue,"POST","/login",null);
                    c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
                    byte[] body=("pin="+URLEncoder.encode(pinValue,"UTF-8")).getBytes(StandardCharsets.UTF_8);
                    c.setFixedLengthStreamingMode(body.length);
                    try(OutputStream out=c.getOutputStream()){out.write(body);}
                    int status=c.getResponseCode();String cookie=c.getHeaderField("Set-Cookie");c.disconnect();
                    Matcher m=Pattern.compile("wb_device=([0-9a-fA-F]{64})").matcher(cookie==null?"":cookie);
                    if(status!=302 || !m.find())throw new IOException("الاقتران فشل؛ تأكد من الرمز أو افتح جسر وسام على الكمبيوتر");
                    String t=m.group(1);TokenStore.write(this,t);TokenStore.host(this,ipValue);
                    token=t;host=ipValue;
                    ui.post(()->{area.removeView(ip);area.removeView(pin);area.removeView(pair);});
                    startSending();
                } catch(Exception ex) {update("تعذّر الاقتران",ex.getMessage(),0);ui.post(()->{ip.setEnabled(true);pin.setEnabled(true);});}
            },"WissamPair").start();
        });area.addView(pair);
    }
    private boolean privateIp(String value) {
        String[] p=value.split("\\.");if(p.length!=4)return false;int[] o=new int[4];
        for(int i=0;i<4;i++){
            if(!p[i].matches("[0-9]{1,3}"))return false;
            o[i]=Integer.parseInt(p[i]);if(o[i]>255)return false;
        }
        return o[0]==10 || (o[0]==172 && o[1]>=16 && o[1]<=31) || (o[0]==192 && o[1]==168);
    }
    private HttpURLConnection request(String ip,String method,String path,String bearer) throws IOException {
        if(!privateIp(ip))throw new IOException("عنوان شبكة الكمبيوتر غير محلي");
        HttpURLConnection c=(HttpURLConnection)new URL("http://"+ip+":"+PORT+path).openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(8000);c.setReadTimeout(120000);
        c.setInstanceFollowRedirects(false);c.setUseCaches(false);
        if(bearer!=null)c.setRequestProperty("Cookie","wb_device="+bearer);
        return c;
    }
    private HttpURLConnection request(String method,String path) throws IOException{return request(host,method,path,token);}
    private static String response(HttpURLConnection c) throws IOException {
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[2048];int n,total=0;
            while((n=in.read(b))!=-1){total+=n;if(total>65536)throw new IOException("رد كبير جداً");out.write(b,0,n);}
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    private String displayName(Uri uri) {
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(c!=null && c.moveToFirst())return VaultFiles.cleanName(c.getString(0));
        }catch(Exception ignored){}
        String value=uri.getLastPathSegment();return VaultFiles.cleanName(value==null?"shared-file":value);
    }
    private void startSending(){
        if(active)return;active=true;
        new Thread(()->{
            try {
                int count=files.size();
                if(count==0){ sendSharedText(sharedText);done("تم استلام النص على الكمبيوتر");return; }
                String project="";
                for(int i=0;i<count;i++) {
                    String name=displayName(files.get(i));
                    project=sendSharedFile(files.get(i),name,i,count);
                }
                done("تم استلام "+count+" ملف/ملفات على الكمبيوتر"+(project.isEmpty()?"":" • المشروع: "+project));
            }catch(Exception ex){active=false;showError("فشل النقل: "+ex.getMessage());}
        },"WissamQuickShare").start();
    }
    private void done(String message) {
        update("✅ "+message,"النسخة الأصلية بقيت على الجوال. يمكنك العودة إلى ChatGPT.",100);
        ui.postDelayed(()->{if(!isFinishing())finish();},1800);
    }
    private void showError(String message) {
        update("تعذّر الإرسال",message,0);
        ui.post(()->{
            Button retry=button("إعادة المحاولة",()->{
                active=false;startSending();
            });area.addView(retry);
            Button open=button("فتح مخزن وسام وإعدادات الاتصال",()->{
                startActivity(new Intent(this,MainActivity.class));
            });area.addView(open);
        });
    }
    private void sendSharedText(String s) throws Exception {
        if(s==null || s.trim().isEmpty())throw new IOException("النص المشترك فارغ");
        byte[] data=s.getBytes(StandardCharsets.UTF_8);
        if(data.length>MAX_MESSAGE_BYTES) {
            // The Windows messages endpoint is limited to 16KB. Long code/text
            // is sent losslessly as a .txt file, not truncated.
            String name="ChatGPT_text_"+System.currentTimeMillis()+".txt";
            sendBytesAsFile(data,name,0,1);return;
        }
        HttpURLConnection c=request("POST","/api/text/send");c.setDoOutput(true);
        c.setRequestProperty("Content-Type","text/plain; charset=utf-8");
        c.setFixedLengthStreamingMode(data.length);
        try {
            update("إرسال النص...",data.length+" بايت",25);
            try(OutputStream out=c.getOutputStream()){out.write(data);}
            if(c.getResponseCode()!=200)throw new IOException("فشل استلام النص ("+c.getResponseCode()+")");
            JSONObject j=new JSONObject(response(c));
            if(!j.optBoolean("received",false))throw new IOException("لم يصل تأكيد استلام النص");
            TextVault.add(this,j.getString("id"),s,"phone_to_pc");
        }finally{c.disconnect();}
    }
    private void sendBytesAsFile(byte[] bytes,String name,int idx,int total) throws Exception {
        try(InputStream in=new ByteArrayInputStream(bytes)){sendStream(in,bytes.length,name,idx,total);}
    }
    private String sendSharedFile(Uri uri,String name,int index,int total) throws Exception {
        // Stage shared content so a correct Content-Length is always available.
        // A failed/partial copy cannot be mistaken for a received file.
        File stage=File.createTempFile("wissam_share_",".part",getCacheDir());
        try {
            long n=0;
            update("تجهيز الملف: "+name,"قراءة الملف من ChatGPT أو تطبيق الملفات",0);
            try(InputStream src=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(stage)){
                if(src==null)throw new IOException("لم يمنح التطبيق إذن قراءة الملف؛ نزّله ثم شاركه من ملفاتي");
                byte[] buf=new byte[65536];int k;
                while((k=src.read(buf))!=-1){
                    n+=k;if(n>MAX_BYTES)throw new IOException("حجم الملف يزيد عن 1GB");out.write(buf,0,k);
                }
            }
            try(InputStream in=new FileInputStream(stage)){return sendStream(in,n,name,index,total);}
        }finally{stage.delete();}
    }
    private String sendStream(InputStream in,long length,String name,int index,int total) throws Exception {
        String id=VaultFiles.newId().replace("-","");
        HttpURLConnection c=request("POST","/upload?name="+URLEncoder.encode(name,"UTF-8")+"&id="+id);
        c.setDoOutput(true);c.setRequestProperty("Content-Type","application/octet-stream");
        c.setFixedLengthStreamingMode(length);
        try {
            long sent=0;
            try(OutputStream out=c.getOutputStream()) {
                byte[] buf=new byte[65536];int k;
                while((k=in.read(buf))!=-1){
                    out.write(buf,0,k);sent+=k;
                    int pct=length==0?99:(int)((sent*100)/length);
                    int overall=(index*100+pct)/total;
                    update("⬆️ "+name,String.format(Locale.US,"%.1f / %.1f MB • %d%%",sent/1048576.0,length/1048576.0,pct),Math.min(99,overall));
                }
                out.flush();
            }
            if(c.getResponseCode()!=200)throw new IOException("الكمبيوتر لم يؤكد الحفظ ("+c.getResponseCode()+")");
            JSONObject j=new JSONObject(response(c));
            if(!j.optBoolean("received",false) || j.optLong("bytes",-1)!=length)
                throw new IOException("الاستلام غير مكتمل؛ لم يصل إيصال مطابق للحجم");
            return j.optString("project","");
        }finally{c.disconnect();}
    }
}
