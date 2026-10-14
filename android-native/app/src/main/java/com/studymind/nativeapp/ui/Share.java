package com.studymind.nativeapp.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Writes an export to the app cache and opens the system share sheet — the phone equivalent of the web downloads. */
public final class Share {
    private Share() {}

    public static boolean textFile(Context c, String fileName, String mime, String content, String chooserTitle) {
        try {
            File dir = new File(c.getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) return false;
            File f = new File(dir, fileName);
            try (FileOutputStream out = new FileOutputStream(f)) { out.write(content.getBytes(StandardCharsets.UTF_8)); }
            Uri uri = FileProvider.getUriForFile(c, "com.studymind.nativeapp.files", f);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mime);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, fileName);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            c.startActivity(Intent.createChooser(send, chooserTitle));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Same file-name rule as the web exportUtils (keeps Vietnamese letters) */
    public static String safeName(String s, int max) {
        String n = s.replaceAll("[^a-zA-Z0-9_\\u00C0-\\u024F\\u1EA0-\\u1EF9]", "_");
        return n.length() > max ? n.substring(0, max) : n;
    }
}
