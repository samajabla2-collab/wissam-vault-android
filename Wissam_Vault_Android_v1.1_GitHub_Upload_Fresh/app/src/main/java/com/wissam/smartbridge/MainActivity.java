package com.wissam.smartbridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PORT=18764;
    private static final int PICK_UPLOAD=100, EXPORT_FILE=101;
    private static final long MAX_FILE=1024L*1024*1024;
    private static final int NAVY=Color.rgb(16,42,68), TEAL=Color.rgb(8,124,131);
    private static final int BG=Color.rgb(244,248,252), GREY=Color.rgb(94,111,134);
    private static final String RECEIPT="received";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Uri> awaitingShare=new ArrayList<>();
    private String host="",token="",pendingExportId=null;
    private EditText ipInput,pinInput;
    private TextView stateLabel,progressLabel,filesCount;
    private ProgressBar progress;
    private LinearLayout incomingHolder,vaultHolder,textHolder;
    private EditText textInput;
    private volatile long lastProgressUi=0;

    @Override public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        host=TokenStore.host(this);token=TokenStore.read(this);
        constructUi();
        renderVault();
        receiveShareIntent(getIntent());
        if(!host.isEmpty() && !token.isEmpty()) refreshRemote();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);setIntent(intent);receiveShareIntent(intent);
    }
    @Override protected void onDestroy() { worker.shutdown();super.onDestroy(); }
    private int dp(float value) { return (int)(getResources().getDisplayMetrics().density*value+0.5f); }
    private GradientDrawable bg(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private LinearLayout vertical() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return v; }
    private LinearLayout horizontal() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return v; }
    private LinearLayout.LayoutParams padParams(int mt) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(mt);return p;
    }
    private TextView label(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.RIGHT);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);t.setPadding(0,dp(3),0,dp(3));return t;
    }
    private Button button(String value, boolean primary, Runnable action) {
        Button b=new Button(this);b.setText(value);b.setTextColor(primary?Color.WHITE:NAVY);b.setTextSize(13);
        b.setAllCaps(false);b.setMinHeight(dp(48));b.setPadding(dp(12),dp(5),dp(12),dp(5));
        GradientDrawable d=bg(primary?TEAL:Color.rgb(235,243,249),13);
        if(!primary)d.setStroke(dp(1),Color.rgb(211,224,235));b.setBackground(d);
        b.setOnClickListener(v->action.run());return b;
    }
    private void addBtn(LinearLayout bar,String text,boolean primary,Runnable action) {
        Button b=button(text,primary,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);
        p.setMargins(0,dp(4),dp(7),dp(4));bar.addView(b,p);
    }
    private LinearLayout card(LinearLayout root,String title,String subtitle) {
        LinearLayout c=vertical();c.setPadding(dp(17),dp(16),dp(17),dp(16));
        GradientDrawable shape=bg(Color.WHITE,20);shape.setStroke(dp(1),Color.rgb(226,235,242));
        c.setBackground(shape);c.setElevation(dp(2));root.addView(c,padParams(14));
        c.addView(label(title,19,NAVY,true));
        if(subtitle!=null)c.addView(label(subtitle,13,GREY,false));
        return c;
    }
    private void constructUi() {
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout root=vertical();root.setPadding(dp(15),dp(21),dp(15),dp(27));scroll.addView(root);
        setContentView(scroll);
        LinearLayout header=vertical();header.setPadding(dp(16),dp(17),dp(16),dp(17));header.setBackground(bg(NAVY,22));
        root.addView(header,padParams(0));
        header.addView(label("📲  مخزن وسام",27,Color.WHITE,true));
        header.addView(label("Wissam Smart Bridge  •  ملفاتك بين الجوال والكمبيوتر",13,0xffd7e8f0,false));
        header.addView(label("ملفات الجوال تبقى محفوظة هنا حتى دون تشغيل الكمبيوتر",13,0xff95e2dd,false));

        LinearLayout connection=card(root,"🔗 الربط مع الكمبيوتر","رمز الاقتران مطلوب مرة واحدة فقط على هذا التطبيق.");
        ipInput=new EditText(this);ipInput.setText(host);ipInput.setHint("مثال: 192.168.1.10");
        ipInput.setSingleLine(true);ipInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        ipInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        connection.addView(ipInput,padParams(6));
        pinInput=new EditText(this);pinInput.setHint("رمز الربط الأول (8 أرقام)");pinInput.setSingleLine(true);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        if(!token.isEmpty())pinInput.setVisibility(View.GONE);
        connection.addView(pinInput,padParams(4));
        LinearLayout tools=horizontal();tools.setPadding(0,dp(6),0,0);
        addBtn(tools,"ربط / تحقق",true,this::connectOrPair);
        addBtn(tools,"تحديث الوارد",false,this::refreshRemote);
        connection.addView(tools);
        stateLabel=label(token.isEmpty()?"غير مقترن بعد":"الجوال مقترن مسبقاً ✓",13,token.isEmpty()?GREY:TEAL,false);
        connection.addView(stateLabel,padParams(8));
        LinearLayout options=horizontal();
        addBtn(options,"تغيير الجوال الموثوق",false,()-> new AlertDialog.Builder(this)
            .setTitle("إعادة اقتران التطبيق")
            .setMessage("سيُحذف رمز الثقة من هذا التطبيق فقط. يمكنك أيضاً إلغاء الثقة من برنامج الكمبيوتر.")
            .setNegativeButton("إلغاء",null)
            .setPositiveButton("متابعة",(d,w)->{ TokenStore.forget(this);token="";pinInput.setVisibility(View.VISIBLE);state("أدخل رمز الاقتران الجديد.",false);})
            .show());
        connection.addView(options);

        LinearLayout upload=card(root,"📤 من الجوال إلى الكمبيوتر","اختر ملفاً أو استخدم «مشاركة» من ChatGPT أو مدير الملفات.");
        LinearLayout uploadActions=horizontal();addBtn(uploadActions,"اختيار ملفات وإرسالها",true,this::chooseUpload);upload.addView(uploadActions);
        progressLabel=label("لا توجد عملية نقل جارية",13,GREY,false);upload.addView(progressLabel,padParams(8));
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgress(0);
        if(android.os.Build.VERSION.SDK_INT>=21)progress.setProgressTintList(android.content.res.ColorStateList.valueOf(TEAL));
        upload.addView(progress,padParams(8));

        LinearLayout snippets=card(root,"📋 حافظة وسام — نسخ مباشر", "إرسال نصوص وأكواد بضغطة، ونسخ أي نص وارد فوراً.");
        textInput=new EditText(this);textInput.setHint("اكتب أو ألصق النص هنا...");
        textInput.setTextSize(15);textInput.setMinLines(3);textInput.setMaxLines(8);
        textInput.setGravity(Gravity.TOP | Gravity.RIGHT);textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        snippets.addView(textInput,padParams(7));
        LinearLayout snippetActions=horizontal();
        addBtn(snippetActions,"إرسال النص للكمبيوتر",true,this::sendSnippet);
        addBtn(snippetActions,"إرسال الحافظة فوراً",false,this::sendClipboardDirect);
        snippets.addView(snippetActions,padParams(8));
        LinearLayout clipActions=horizontal();
        addBtn(clipActions,"لصق الحافظة",false,this::pasteClipboard);
        addBtn(clipActions,"نسخ مباشر",false,()->copyDirect(textInput.getText().toString()));
        snippets.addView(clipActions,padParams(5));
        LinearLayout snippetTools=horizontal();
        addBtn(snippetTools,"تحديث النصوص",false,this::refreshTexts);
        addBtn(snippetTools,"تنظيف سجل النصوص",false,this::clearTexts);
        snippets.addView(snippetTools,padParams(6));
        textHolder=vertical();snippets.addView(textHolder,padParams(10));
        renderTexts();

        LinearLayout outbox=card(root,"📥 من الكمبيوتر إلى مخزن الجوال","اختر ملفاً أرسله الكمبيوتر، وسيُحفظ داخل التطبيق مع تأكيد استلام حقيقي.");
        incomingHolder=vertical();outbox.addView(incomingHolder,padParams(7));
        incomingHolder.addView(label("اضغط تحديث الوارد لعرض ملفات الكمبيوتر.",13,GREY,false));

        LinearLayout vault=card(root,"🗂️ المخزن المحلي","يفتح بدون Wi-Fi • حذف الملف هنا لا يحذف نسخة الكمبيوتر.");
        filesCount=label("",14,TEAL,true);vault.addView(filesCount,padParams(6));
        LinearLayout vaultActions=horizontal();
        addBtn(vaultActions,"تحديث المخزن",false,this::renderVault);
        addBtn(vaultActions,"تنظيف الكل",false,this::confirmClearVault);vault.addView(vaultActions);
        vaultHolder=vertical();vault.addView(vaultHolder,padParams(8));
        root.addView(label("🔒 النقل داخل شبكة Wi-Fi المنزلية فقط. استخدم شبكة موثوقة: بروتوكول الكمبيوتر الحالي HTTP غير مشفّر.",12,GREY,false),padParams(18));
        root.addView(label("برمجة وتطوير: وسام سليم الحجة",12,GREY,false),padParams(4));
    }
    private void state(String msg,boolean okay) {
        runOnUiThread(()-> {stateLabel.setText(msg);stateLabel.setTextColor(okay?TEAL:0xffb13b3b);});
    }
    private void status(String message,int pct) {
        long now=System.currentTimeMillis();
        if(pct>=0 && pct<100 && now-lastProgressUi<140)return;
        lastProgressUi=now;
        runOnUiThread(()-> {progressLabel.setText(message);if(pct>=0)progress.setProgress(Math.max(0,Math.min(100,pct)));});
    }
    private void toast(String message) { runOnUiThread(()->Toast.makeText(this,message,Toast.LENGTH_LONG).show()); }
    private static String size(long bytes) {
        if(bytes>=1024L*1024*1024)return String.format(Locale.US,"%.2f GB",bytes/(1024.0*1024*1024));
        if(bytes>=1024L*1024)return String.format(Locale.US,"%.1f MB",bytes/(1024.0*1024));
        return String.format(Locale.US,"%.0f KB",bytes/1024.0);
    }
    private String validIp() {
        String value=ipInput.getText().toString().trim();
        String[] parts=value.split("\\.");
        if(parts.length!=4)throw new IllegalArgumentException("اكتب IP الكمبيوتر، مثل 192.168.1.10");
        int[] octets=new int[4];
        for(int i=0;i<4;i++) {
            if(!parts[i].matches("[0-9]{1,3}"))throw new IllegalArgumentException("عنوان الكمبيوتر غير صحيح");
            octets[i]=Integer.parseInt(parts[i]);
            if(octets[i]>255)throw new IllegalArgumentException("عنوان IP غير صحيح");
        }
        boolean privateNet=octets[0]==10 || (octets[0]==172 && octets[1]>=16 && octets[1]<=31)
            || (octets[0]==192 && octets[1]==168);
        if(!privateNet)throw new IllegalArgumentException("مسموح بعناوين الشبكة المنزلية الخاصة فقط.");
        return value;
    }
    private HttpURLConnection request(String method,String path,boolean authenticated) throws Exception {
        String targetHost=host;
        if(targetHost.isEmpty())throw new IOException("اكتب عنوان IP للكمبيوتر أولاً.");
        HttpURLConnection connection=(HttpURLConnection)new URL("http://"+targetHost+":"+PORT+path).openConnection();
        connection.setRequestMethod(method);connection.setConnectTimeout(6500);
        connection.setReadTimeout(120000);connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);connection.setRequestProperty("Cache-Control","no-store");
        connection.setRequestProperty("User-Agent","WissamVault/1.1 Android");
        if(authenticated && !token.isEmpty())connection.setRequestProperty("Cookie","wb_device="+token);
        return connection;
    }
    private static byte[] readBody(HttpURLConnection conn) throws Exception {
        InputStream stream=conn.getResponseCode()>=400?conn.getErrorStream():conn.getInputStream();
        if(stream==null)return new byte[0];
        try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192];int n;long count=0;
            while((n=in.read(b))!=-1) {count+=n;if(count>2*1024*1024)throw new IOException("Oversized response");out.write(b,0,n);}
            return out.toByteArray();
        }
    }
    private static String bodyText(HttpURLConnection c) throws Exception {
        return new String(readBody(c),StandardCharsets.UTF_8);
    }
    private void connectOrPair() {
        try{host=validIp();}catch(Exception ex){state(ex.getMessage(),false);return;}
        TokenStore.host(this,host);
        String pin=pinInput.getText().toString().trim();
        if(token.isEmpty() && !pin.matches("[0-9]{8}")) {state("أدخل رمز الاقتران المكون من 8 أرقام من برنامج الكمبيوتر.",false);return;}
        status("جارٍ فحص الاتصال...",0);
        worker.execute(()->{
            try {
                if(token.isEmpty()){
                    byte[] encoded=("pin="+URLEncoder.encode(pin,"UTF-8")).getBytes(StandardCharsets.UTF_8);
                    HttpURLConnection c=request("POST","/login",false);
                    c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
                    c.setFixedLengthStreamingMode(encoded.length);
                    try(OutputStream out=c.getOutputStream()){out.write(encoded);}
                    int status=c.getResponseCode();
                    String cookies=c.getHeaderField("Set-Cookie");
                    c.disconnect();
                    if(status!=302 || cookies==null)throw new IOException("الرمز غير صحيح أو تعذّر حفظ الاقتران على الكمبيوتر.");
                    java.util.regex.Matcher m=java.util.regex.Pattern.compile("wb_device=([0-9a-fA-F]{64})").matcher(cookies);
                    if(!m.find())throw new IOException("لم يصل رمز الجهاز الموثوق من الكمبيوتر.");
                    String saved=m.group(1);
                    TokenStore.write(this,saved);token=saved;
                }
                JSONObject data=fetchList();
                fetchTexts();
                state("✅ متصل وموثوق — " + host + ":" + PORT,true);
                runOnUiThread(()-> {pinInput.setVisibility(View.GONE);pinInput.setText("");renderOutbox(data.optJSONArray("outgoing"));});
                status("جاهز للنقل بالاتجاهين",100);
            }catch(Exception ex){
                state("تعذّر الاتصال: "+ex.getMessage(),false);
                status("افتح جسر وسام على الكمبيوتر، وتأكد من شبكة Wi-Fi والعنوان.",0);
            }
        });
    }
    private JSONObject fetchList() throws Exception {
        HttpURLConnection c=request("GET","/api/list",true);
        try {int code=c.getResponseCode();String data=bodyText(c);
            if(code==401)throw new IOException("انتهت صلاحية الاقتران أو أُلغيت ثقة الجوال من الكمبيوتر.");
            if(code!=200)throw new IOException("الجسر رد برمز "+code);
            return new JSONObject(data);
        } finally { c.disconnect(); }
    }
    private void refreshRemote() {
        try{host=validIp();TokenStore.host(this,host);}catch(Exception ex){state(ex.getMessage(),false);return;}
        if(token.isEmpty()) {state("اربط التطبيق أول مرة أولاً.",false);return;}
        worker.execute(()->{
            try {
                JSONObject data=fetchList();
                fetchTexts();
                runOnUiThread(()->renderOutbox(data.optJSONArray("outgoing")));
                state("✅ متصل بالكمبيوتر — آخر تحديث ناجح",true);
            } catch(Exception ex) {state("تعذّر تحديث الوارد: "+ex.getMessage(),false);}
        });
    }
    private String currentClipboard() {
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard==null || !clipboard.hasPrimaryClip())return "";
        ClipData clip=clipboard.getPrimaryClip();
        if(clip==null || clip.getItemCount()==0)return "";
        CharSequence value=clip.getItemAt(0).coerceToText(this);
        return value==null?"":value.toString();
    }
    private void pasteClipboard() {
        String value=currentClipboard();
        if(value.isEmpty()){toast("لا يوجد نص في الحافظة");return;}
        textInput.setText(value);textInput.setSelection(textInput.length());
        toast("تم لصق النص، اضغط إرسال إذا أردت نقله");
    }
    private void sendClipboardDirect() {
        String value=currentClipboard();
        if(value.isEmpty()){toast("لا يوجد نص في الحافظة");return;}
        sendTextValue(value,false);
    }
    private void copyDirect(String text) {
        if(text==null || text.isEmpty()){toast("لا يوجد نص للنسخ");return;}
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard==null){toast("الحافظة غير متاحة");return;}
        clipboard.setPrimaryClip(ClipData.newPlainText("Wissam",text));
        toast("✅ تم النسخ مباشرة — يمكنك اللصق الآن");
    }
    private void sendSnippet() { sendTextValue(textInput.getText().toString(),true); }
    private void sendTextValue(String content,boolean clearEditor) {
        if(content.trim().isEmpty()){toast("اكتب أو ألصق نصاً أولاً");return;}
        if(content.getBytes(StandardCharsets.UTF_8).length>16384){toast("الحد الأقصى للنص 16KB");return;}
        if(token.isEmpty()){state("اربط التطبيق بالكمبيوتر أولاً",false);return;}
        worker.execute(()->{
            HttpURLConnection c=null;
            try{
                byte[] data=content.getBytes(StandardCharsets.UTF_8);
                c=request("POST","/api/text/send",true);
                c.setDoOutput(true);c.setRequestProperty("Content-Type","text/plain; charset=utf-8");
                c.setFixedLengthStreamingMode(data.length);
                try(OutputStream stream=c.getOutputStream()){stream.write(data);}
                if(c.getResponseCode()!=200)throw new IOException("الكمبيوتر لم يؤكد استلام النص ("+c.getResponseCode()+")");
                JSONObject receipt=new JSONObject(bodyText(c));
                if(!receipt.optBoolean("received"))throw new IOException("لم يصل إيصال استلام النص");
                TextVault.add(this,receipt.getString("id"),content,"phone_to_pc");
                runOnUiThread(()->{if(clearEditor)textInput.setText("");renderTexts();});
                toast("✅ تم استلام النص على الكمبيوتر");
            }catch(Exception ex){toast("تعذّر إرسال النص: "+ex.getMessage());}
            finally{if(c!=null)c.disconnect();}
        });
    }
    private void fetchTexts() throws Exception {
        HttpURLConnection c=request("GET","/api/text/list",true);
        try{
            if(c.getResponseCode()!=200)throw new IOException("تعذّر تحديث النصوص ("+c.getResponseCode()+")");
            JSONArray items=new JSONObject(bodyText(c)).optJSONArray("items");
            if(items==null)return;
            for(int i=0;i<items.length();i++){
                JSONObject msg=items.optJSONObject(i);if(msg==null || !"pc_to_phone".equals(msg.optString("direction")))continue;
                String id=msg.optString("id"),text=msg.optString("text");
                // Never acknowledge before local storage succeeds.
                TextVault.add(this,id,text,"pc_to_phone");
                HttpURLConnection ack=null;
                try{ack=request("POST","/api/text/ack?id="+id,true);
                    ack.setDoOutput(true);ack.setFixedLengthStreamingMode(0);
                    try(OutputStream stream=ack.getOutputStream()){}
                    if(ack.getResponseCode()!=200)throw new IOException("ACK failed");
                }catch(Exception ignored){}finally{if(ack!=null)ack.disconnect();}
            }
            runOnUiThread(this::renderTexts);
        }finally{c.disconnect();}
    }
    private void refreshTexts(){
        if(token.isEmpty()){state("اربط التطبيق أولاً",false);return;}
        worker.execute(()->{try{fetchTexts();toast("✅ تم تحديث النصوص");}
            catch(Exception ex){state("تعذّر تحديث النصوص: "+ex.getMessage(),false);}});
    }
    private void renderTexts() {
        if(textHolder==null)return;
        textHolder.removeAllViews();
        JSONArray items=TextVault.list(this);
        if(items.length()==0){textHolder.addView(label("لا توجد نصوص محفوظة حتى الآن.",13,GREY,false));return;}
        for(int i=0;i<items.length();i++){
            JSONObject msg=items.optJSONObject(i);if(msg==null)continue;
            String content=msg.optString("text");
            boolean incoming="pc_to_phone".equals(msg.optString("direction"));
            LinearLayout section=vertical();section.setPadding(dp(10),dp(10),dp(10),dp(10));section.setBackground(bg(BG,12));
            textHolder.addView(section,padParams(7));
            section.addView(label(incoming?"📥 وصل من الكمبيوتر":"📤 أُرسل للكمبيوتر",12,TEAL,true));
            TextView full=label(content,14,NAVY,false);full.setTextIsSelectable(true);
            section.addView(full);
            LinearLayout controls=horizontal();
            addBtn(controls,"📋 نسخ مباشر",true,()->copyDirect(content));
            addBtn(controls,"إعادة استخدام",false,()->{textInput.setText(content);textInput.setSelection(textInput.length());});
            section.addView(controls);
        }
    }
    private void clearTexts(){
        new AlertDialog.Builder(this).setTitle("حذف سجل النصوص من الجوال؟")
            .setMessage("يحذف النسخ المحلية للنصوص فقط. النصوص على الكمبيوتر لا تُحذف.")
            .setNegativeButton("إلغاء",null)
            .setPositiveButton("حذف",(d,w)->{try{TextVault.clear(this);renderTexts();toast("تم تنظيف سجل الجوال");}
                catch(Exception ex){toast(ex.getMessage());}}).show();
    }

    private void renderOutbox(JSONArray files) {
        incomingHolder.removeAllViews();
        if(files==null || files.length()==0) {
            incomingHolder.addView(label("لا توجد ملفات جديدة جاهزة من الكمبيوتر.",13,GREY,false));return;
        }
        for(int i=0;i<files.length();i++){
            JSONObject f=files.optJSONObject(i);if(f==null)continue;
            String id=f.optString("id"),name=VaultFiles.cleanName(f.optString("name"));
            long bytes=f.optLong("size",-1);
            LinearLayout line=vertical();line.setPadding(dp(10),dp(10),dp(10),dp(10));line.setBackground(bg(BG,12));
            incomingHolder.addView(line,padParams(9));
            line.addView(label("📦 "+name,14,NAVY,true));line.addView(label(size(bytes),12,GREY,false));
            LinearLayout actions=horizontal();addBtn(actions,"⬇️ حفظ في المخزن",true,()->download(id,name,bytes));line.addView(actions);
        }
    }
    private void chooseUpload() {
        if(token.isEmpty()){state("اربط الجوال أولاً.",false);return;}
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        startActivityForResult(i,PICK_UPLOAD);
    }
    private String pickedName(Uri uri) {
        try (Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(c!=null && c.moveToFirst())return VaultFiles.cleanName(c.getString(0));
        }catch(Exception ignored){}
        return VaultFiles.cleanName(uri.getLastPathSegment());
    }
    private void enqueueUploads(List<Uri> files) {
        if(files.isEmpty())return;
        if(token.isEmpty()){state("اربط التطبيق بالكمبيوتر قبل الإرسال.",false);return;}
        worker.execute(()->{
            int done=0;
            for(Uri u:files) {
                try{uploadUri(u);done++;}catch(Exception ex){status("❌ فشل إرسال ملف: "+ex.getMessage(),0);}
            }
            if(done==files.size())status("✅ تم حفظ "+done+" ملف/ملفات على الكمبيوتر",100);
            else status("تم إرسال "+done+" من "+files.size()+"؛ راجع الملفات التي لم تصل.",0);
        });
    }
    private void uploadUri(Uri uri) throws Exception {
        String name=pickedName(uri);
        File temp=File.createTempFile("outgoing_",".tmp",getCacheDir());
        try {
            long bytes=0;
            status("تجهيز "+name+" للإرسال...",0);
            try(InputStream source=getContentResolver().openInputStream(uri);
                OutputStream out=new FileOutputStream(temp)) {
                if(source==null)throw new IOException("تعذر قراءة الملف");
                byte[] b=new byte[65536];int n;
                while((n=source.read(b))!=-1){bytes+=n;if(bytes>MAX_FILE)throw new IOException("حجم الملف يتجاوز 1GB");out.write(b,0,n);}
            }
            uploadFile(temp,name,bytes);
        } finally {temp.delete();}
    }
    private void uploadFile(File src,String name,long bytes) throws Exception {
        if(bytes<0 || bytes>MAX_FILE)throw new IOException("ملف أكبر من الحد المسموح");
        String requestId=VaultFiles.newId().replace("-", "");
        HttpURLConnection c=request("POST","/upload?name="+URLEncoder.encode(name,"UTF-8")+"&id="+requestId,true);
        c.setDoOutput(true);c.setRequestProperty("Content-Type","application/octet-stream");
        c.setFixedLengthStreamingMode(bytes);
        long sent=0;
        try {
            try(InputStream in=new FileInputStream(src);OutputStream out=c.getOutputStream()) {
                byte[] buffer=new byte[65536];int n;
                while((n=in.read(buffer))!=-1) {
                    out.write(buffer,0,n);sent+=n;
                    status("⬆️ "+name+" — "+size(sent)+" / "+size(bytes),bytes>0?(int)(100*sent/bytes):100);
                }
                out.flush();
            }
            if(c.getResponseCode()!=200)throw new IOException("الخادم رفض الاستلام: "+c.getResponseCode());
            JSONObject receipt=new JSONObject(bodyText(c));
            if(!receipt.optBoolean(RECEIPT,false) || receipt.optLong("bytes",-1)!=bytes)
                throw new IOException("لم يصل تأكيد حفظ صحيح من الكمبيوتر");
            status("✅ تم استلام وحفظ الملف على الكمبيوتر: "+name,100);
        } finally {c.disconnect();}
    }
    private void download(String remoteId,String name,long expected) {
        if(remoteId==null || !remoteId.matches("[0-9a-fA-F]{8,80}")) {
            status("معرّف الملف غير صحيح",0);return;
        }
        if(expected<0 || expected>MAX_FILE){status("حجم الملف غير مسموح",0);return;}
        worker.execute(()->{
            String localId=VaultFiles.newId();
            File target=VaultFiles.file(this,localId), temp=new File(VaultFiles.folder(this),localId+".partial");
            HttpURLConnection c=null;long read=0;
            try {
                status("⬇️ بدء نقل "+name,0);
                c=request("GET","/download/"+remoteId,true);
                if(c.getResponseCode()!=200)throw new IOException("الكمبيوتر لم يُرسل الملف ("+c.getResponseCode()+")");
                long responseSize=c.getContentLengthLong();
                if(responseSize>=0 && responseSize!=expected)throw new IOException("تغيّر حجم الملف أثناء النقل");
                try(InputStream stream=c.getInputStream();FileOutputStream out=new FileOutputStream(temp)) {
                    byte[] b=new byte[65536];int n;
                    while((n=stream.read(b))!=-1) {
                        read+=n;if(read>expected)throw new IOException("حجم مختلف عن الحجم المعلن");
                        out.write(b,0,n);
                        status("⬇️ "+name+" — "+size(read)+" / "+size(expected),expected>0?(int)(100*read/expected):100);
                    }
                    out.getFD().sync();
                }
                if(read!=expected)throw new IOException("انقطع النقل قبل اكتماله");
                if(!temp.renameTo(target))throw new IOException("تعذّر نقل الملف إلى المخزن");
                try{VaultFiles.add(this,localId,name,read);}
                catch(Exception indexError){target.delete();throw indexError;}
                // Acknowledgement is sent only after all bytes are on disk and indexed.
                boolean receiptSent = true;
                try {HttpURLConnection ack=request("POST","/api/ack?id="+remoteId+"&saved=1",true);
                    ack.setDoOutput(true);ack.setFixedLengthStreamingMode(0);
                    try{ack.getOutputStream().close();if(ack.getResponseCode()!=200)throw new IOException("ACK refused");}
                    finally{ack.disconnect();}
                } catch(Exception ex) {receiptSent=false;}
                runOnUiThread(this::renderVault);
                status(receiptSent ? "✅ تم حفظ الملف وإرسال إيصال الاستلام: "+name :
                    "✅ تم حفظ "+name+" في المخزن، ولكن تعذر إرسال إيصال الاستلام للكمبيوتر.",100);
            } catch(Exception ex) {
                temp.delete();status("❌ لم يكتمل استقبال "+name+": "+ex.getMessage(),0);
            } finally {if(c!=null)c.disconnect();}
        });
    }
    private void renderVault() {
        if(vaultHolder==null)return;
        vaultHolder.removeAllViews();
        JSONArray items=VaultFiles.list(this);
        long total=0;
        for(int i=0;i<items.length();i++) {
            JSONObject o=items.optJSONObject(i);if(o!=null)total+=o.optLong("bytes");
        }
        filesCount.setText(items.length()+" ملفات محفوظة • "+size(total));
        if(items.length()==0) {
            vaultHolder.addView(label("المخزن فارغ حالياً. الملفات المستلمة ستظهر هنا.",13,GREY,false));return;
        }
        for(int i=items.length()-1;i>=0;i--) {
            JSONObject o=items.optJSONObject(i);if(o==null)continue;
            String id=o.optString("id"),name=o.optString("name");
            long bytes=o.optLong("bytes",0),when=o.optLong("time",0);
            LinearLayout file=vertical();file.setPadding(dp(9),dp(10),dp(9),dp(11));file.setBackground(bg(BG,12));
            vaultHolder.addView(file,padParams(9));
            file.addView(label("📄 "+name,14,NAVY,true));
            file.addView(label(size(bytes)+" • "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(when)),12,GREY,false));
            LinearLayout controls=horizontal();
            addBtn(controls,"فتح",true,()->openFile(id,name));
            addBtn(controls,"حفظ نسخة",false,()->exportFile(id,name));
            file.addView(controls);
            LinearLayout more=horizontal();
            addBtn(more,"إرسال للكمبيوتر",false,()->sendSaved(id,name,bytes));
            addBtn(more,"حذف",false,()->confirmDelete(id,name));
            file.addView(more);
        }
    }
    private void openFile(String id,String name) {
        try {
            if(!VaultFiles.file(this,id).isFile())throw new IOException("الملف لم يعد موجوداً");
            Intent i=new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(VaultFiles.uri(id),VaultFiles.mime(name));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i,"فتح الملف بواسطة"));
        }catch(ActivityNotFoundException ex){toast("لا يوجد تطبيق لفتح هذا النوع من الملفات.");}
        catch(Exception ex){toast(ex.getMessage());}
    }
    private void exportFile(String id,String name) {
        pendingExportId=id;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(VaultFiles.mime(name));
        i.putExtra(Intent.EXTRA_TITLE,name);
        try{startActivityForResult(i,EXPORT_FILE);}catch(Exception ex){toast("تعذّر فتح اختيار مكان الحفظ");}
    }
    private void sendSaved(String id,String name,long bytes) {
        if(token.isEmpty()){state("اربط التطبيق قبل إرسال الملفات.",false);return;}
        worker.execute(()->{
            try{File source=VaultFiles.file(this,id);if(!source.isFile())throw new IOException("الملف غير موجود");
                uploadFile(source,name,source.length());}
            catch(Exception ex){status("❌ تعذّر إرسال الملف: "+ex.getMessage(),0);}
        });
    }
    private void confirmDelete(String id,String name) {
        new AlertDialog.Builder(this).setTitle("حذف الملف من الجوال؟")
            .setMessage(name+"\nسيُحذف من مخزن الجوال فقط، وليس من الكمبيوتر.")
            .setNegativeButton("إلغاء",null)
            .setPositiveButton("حذف",(d,w)->{
                try{VaultFiles.remove(this,id);renderVault();toast("حُذف من مخزن الجوال");}
                catch(Exception ex){toast("فشل الحذف: "+ex.getMessage());}
            }).show();
    }
    private void confirmClearVault() {
        new AlertDialog.Builder(this).setTitle("تنظيف مخزن وسام؟")
            .setMessage("سيتم حذف جميع الملفات المحفوظة في التطبيق على الجوال فقط. لا يمكن التراجع عن هذا الحذف.")
            .setNegativeButton("إلغاء",null)
            .setPositiveButton("تنظيف الكل",(d,w)->{
                try{int n=VaultFiles.clear(this);renderVault();toast("تم تنظيف "+n+" ملف/ملفات");}
                catch(Exception ex){toast("بعض الملفات تعذّر حذفها: "+ex.getMessage());renderVault();}
            }).show();
    }
    private void receiveShareIntent(Intent intent) {
        if(intent==null)return;
        String action=intent.getAction();
        if(Intent.ACTION_SEND.equals(action)){
            Uri u=intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if(u!=null)awaitingShare.add(u);
            else if(intent.hasExtra(Intent.EXTRA_TEXT)){
                CharSequence shared=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                if(shared!=null && textInput!=null){textInput.setText(shared);textInput.setSelection(textInput.length());
                    toast("تم إدراج النص من المشاركة — اضغط إرسال للكمبيوتر");}
            }
        } else if(Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> files=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if(files!=null)awaitingShare.addAll(files);
        }
        if(!awaitingShare.isEmpty()){
            final ArrayList<Uri> selected=new ArrayList<>(awaitingShare);
            awaitingShare.clear();
            new AlertDialog.Builder(this).setTitle("إرسال إلى الكمبيوتر")
                .setMessage("تم اختيار "+selected.size()+" ملف/ملفات من تطبيق آخر. هل تريد إرسالها عبر Wi-Fi إلى الكمبيوتر الآن؟")
                .setNegativeButton("إلغاء",null)
                .setPositiveButton("إرسال",(d,w)->enqueueUploads(selected)).show();
        }
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK || data==null)return;
        if(requestCode==PICK_UPLOAD){
            List<Uri> selected=new ArrayList<>();
            ClipData clips=data.getClipData();
            if(clips!=null)for(int i=0;i<clips.getItemCount();i++)selected.add(clips.getItemAt(i).getUri());
            else if(data.getData()!=null)selected.add(data.getData());
            enqueueUploads(selected);
        }else if(requestCode==EXPORT_FILE && pendingExportId!=null){
            String id=pendingExportId;pendingExportId=null;Uri destination=data.getData();
            if(destination==null)return;
            worker.execute(()->{
                try(InputStream src=new FileInputStream(VaultFiles.file(this,id));
                    OutputStream out=getContentResolver().openOutputStream(destination,"w")) {
                    if(out==null)throw new IOException("تعذّر فتح مكان حفظ الملف");
                    byte[] b=new byte[65536];int n;
                    while((n=src.read(b))!=-1)out.write(b,0,n);
                    out.flush();toast("✅ تم حفظ نسخة في المجلد الذي اخترته");
                }catch(Exception ex){toast("تعذّر التصدير: "+ex.getMessage());}
            });
        }
    }
}
