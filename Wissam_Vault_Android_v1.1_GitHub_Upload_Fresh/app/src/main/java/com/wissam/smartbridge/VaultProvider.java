package com.wissam.smartbridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import org.json.JSONObject;
import java.io.File;
import java.io.FileNotFoundException;

public final class VaultProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private String id(Uri uri) {
        if (uri==null || uri.getPathSegments().size()!=2 || !"file".equals(uri.getPathSegments().get(0)))
            throw new SecurityException("Unknown file");
        return uri.getPathSegments().get(1);
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("Read only");
        String key=id(uri); JSONObject item=VaultFiles.find(getContext(),key);
        if(item==null) throw new FileNotFoundException("Not in vault");
        File f=VaultFiles.file(getContext(),key);
        if (!f.isFile()) throw new FileNotFoundException("File not found");
        return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) {
        JSONObject item=VaultFiles.find(getContext(),id(uri));
        return item==null ? null : VaultFiles.mime(item.optString("name"));
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        JSONObject item=VaultFiles.find(getContext(),id(uri));
        String[] cols=projection==null ? new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE} : projection;
        MatrixCursor cur=new MatrixCursor(cols);
        if(item!=null) {
            Object[] values=new Object[cols.length];
            for(int i=0;i<cols.length;i++) {
                if(OpenableColumns.DISPLAY_NAME.equals(cols[i])) values[i]=item.optString("name");
                else if(OpenableColumns.SIZE.equals(cols[i])) values[i]=item.optLong("bytes");
            }
            cur.addRow(values);
        }
        return cur;
    }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri,ContentValues values,String where,String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri,String where,String[] args) { throw new UnsupportedOperationException(); }
}
