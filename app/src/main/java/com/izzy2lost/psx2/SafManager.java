package com.izzy2lost.psx2;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Minimal helper around Android SAF for a user-selected data root directory.
 * Stores a persisted tree URI in SharedPreferences and provides helpers to
 * create/list/read/write files under subdirectories (e.g., covers, resources).
 */
public final class SafManager {
    private static final String PREFS = "app_prefs";
    private static final String KEY_DATA_ROOT = "data_root_tree_uri";
    private static final String TAG = "SafManager";

    private SafManager() {}

    public static Uri getDataRootUri(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String s = prefs.getString(KEY_DATA_ROOT, null);
        return (s != null && !s.isEmpty()) ? Uri.parse(s) : null;
    }

    public static void setDataRootUri(Context ctx, Uri treeUri) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_DATA_ROOT, treeUri != null ? treeUri.toString() : null).apply();
    }

    private static boolean hasPersistedPermission(Context ctx, Uri uri) {
        if (uri == null) return false;
        for (UriPermission perm : ctx.getContentResolver().getPersistedUriPermissions()) {
            if (uri.equals(perm.getUri()) && perm.isReadPermission()) {
                return true;
            }
        }
        return false;
    }

    public static DocumentFile getDataRoot(Context ctx) {
        Uri u = getDataRootUri(ctx);
        if (u == null) return null;
        if (!hasPersistedPermission(ctx, u)) return null;
        return DocumentFile.fromTreeUri(ctx, u);
    }

    public static DocumentFile getOrCreateDir(Context ctx, String... segments) {
        DocumentFile root = getDataRoot(ctx);
        if (root == null) return null;
        DocumentFile cur = root;
        for (String seg : segments) {
            if (seg == null || seg.isEmpty()) continue;
            try {
                DocumentFile next = cur.findFile(seg);
                if (next == null) {
                    if (!cur.canWrite()) {
                        // Cannot create without permission; fail gracefully.
                        return null;
                    }
                    next = cur.createDirectory(seg);
                }
                if (next == null) return null;
                cur = next;
            } catch (SecurityException | IllegalStateException e) {
                Log.w(TAG, "Unable to access SAF directory segment '" + seg + "'", e);
                return null;
            }
        }
        return cur;
    }

    public static DocumentFile getChild(Context ctx, String[] dirSegments, String filename) {
        DocumentFile dir = getOrCreateDir(ctx, dirSegments);
        if (dir == null) return null;
        try {
            return dir.findFile(filename);
        } catch (Exception e) {
            Log.w(TAG, "Unable to access SAF file '" + filename + "'", e);
            return null;
        }
    }

    public static DocumentFile createChild(Context ctx, String[] dirSegments, String filename, String mime) {
        DocumentFile dir = getOrCreateDir(ctx, dirSegments);
        if (dir == null) return null;
        DocumentFile f = dir.findFile(filename);
        if (f != null && f.isFile()) return f;
        return dir.createFile(mime != null ? mime : "application/octet-stream", filename);
    }

    public static boolean writeBytes(Context ctx, Uri target, byte[] data) {
        if (target == null || data == null) return false;
        try (OutputStream os = ctx.getContentResolver().openOutputStream(target, "w")) {
            if (os == null) return false;
            os.write(data);
            os.flush();
            return true;
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean copyFromStream(Context ctx, InputStream in, Uri target) {
        if (in == null || target == null) return false;
        try (OutputStream os = ctx.getContentResolver().openOutputStream(target, "w")) {
            if (os == null) return false;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
            os.flush();
            return true;
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean exists(Context ctx, Uri uri) {
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            return is != null;
        } catch (Exception ignored) {}
        return false;
    }

    public static Intent buildOpenTreeIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        return intent;
    }
}

