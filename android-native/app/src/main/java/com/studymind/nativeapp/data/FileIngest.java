package com.studymind.nativeapp.data;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Client-side ingestion: text is extracted on the phone whenever possible (DOCX/PPTX/TXT); images and PDFs
 * go to Supabase Storage as files (≤ 50MB) and are read by the "studymind-api" Edge Function.
 */
public final class FileIngest {
    private FileIngest() {}

    /** Photos are re-encoded below this so Gemini vision gets a sharp yet small image */
    public static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
    public static final int MAX_TEXT_SOURCE_BYTES = 50 * 1024 * 1024;
    private static final List<String> TEXT_EXT = Arrays.asList("txt", "md", "csv", "json");
    private static final List<String> IMAGE_EXT = Arrays.asList("png", "jpg", "jpeg", "webp", "gif", "bmp");

    /** What gets sent: either extracted text, or a binary file */
    public static class Prepared {
        public String fileName;
        public String text;
        public byte[] binary;
        public String mime;
    }

    public static Prepared prepare(ContentResolver cr, Uri uri) throws IOException {
        String name = displayName(cr, uri);
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        String mime = cr.getType(uri);
        if (ext.equals("doc") || ext.equals("ppt")) {
            throw new ApiException(400, "Định dạng ." + ext + " (Office cũ) chưa được hỗ trợ. Hãy lưu lại thành ." + ext + "x hoặc PDF rồi thử lại.", null);
        }
        if (ext.equals("heic") || ext.equals("heif")) {
            throw new ApiException(400, "Ảnh HEIC chưa được hỗ trợ. Hãy chuyển sang JPG/PNG rồi thử lại.", null);
        }

        byte[] bytes = readAll(cr, uri);
        Prepared p = new Prepared();
        p.fileName = name;

        boolean image = IMAGE_EXT.contains(ext) || (mime != null && mime.startsWith("image/"));
        if (image) {
            p.binary = compressImage(bytes);
            p.mime = "image/jpeg";
            p.fileName = name.replaceAll("\\.[^.]+$", "") + ".jpg";
            return p;
        }
        if (TEXT_EXT.contains(ext)) {
            p.text = new String(bytes, StandardCharsets.UTF_8);
        } else if (ext.equals("docx")) {
            p.text = docxText(bytes);
        } else if (ext.equals("pptx")) {
            p.text = pptxText(bytes);
        } else if (ext.equals("pdf")) {
            // No PDF text extraction on-device: the server reads it (Gemini handles scanned pages too)
            p.binary = bytes;
            p.mime = "application/pdf";
            return p;
        } else {
            throw new ApiException(400, "Không hỗ trợ tệp \"" + name + "\". Hãy dùng PDF, DOCX, PPTX, TXT hoặc ảnh JPG/PNG/WEBP.", null);
        }
        if (p.text == null || p.text.trim().isEmpty()) throw new ApiException(400, "Không đọc được nội dung chữ trong tệp này.", null);
        return p;
    }

    public static String displayName(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (RuntimeException ignored) {}
        String last = uri.getLastPathSegment();
        return last != null ? last : "tai-lieu";
    }

    private static byte[] readAll(ContentResolver cr, Uri uri) throws IOException {
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IOException("Không mở được tệp.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_TEXT_SOURCE_BYTES) throw new ApiException(413, "Tệp quá lớn. Giới hạn là 50MB.", null);
            }
            return out.toByteArray();
        }
    }

    /** Downscale and re-encode so the image fits the upload limit while small print stays legible */
    private static byte[] compressImage(byte[] original) throws IOException {
        if (original.length <= 1.5 * 1024 * 1024) {
            Bitmap probe = BitmapFactory.decodeByteArray(original, 0, original.length);
            if (probe == null) throw new ApiException(400, "Không đọc được ảnh này. Hãy thử ảnh JPG hoặc PNG khác.", null);
        }
        Bitmap src = BitmapFactory.decodeByteArray(original, 0, original.length);
        if (src == null) throw new ApiException(400, "Không đọc được ảnh này. Hãy thử ảnh JPG hoặc PNG khác.", null);
        for (int maxEdge : new int[]{2400, 2000, 1600}) {
            float scale = Math.min(1f, maxEdge / (float) Math.max(src.getWidth(), src.getHeight()));
            Bitmap scaled = Bitmap.createBitmap(Math.round(src.getWidth() * scale), Math.round(src.getHeight() * scale), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(scaled);
            canvas.drawColor(Color.WHITE); // transparent PNG areas become white instead of black in JPEG
            canvas.drawBitmap(Bitmap.createScaledBitmap(src, scaled.getWidth(), scaled.getHeight(), true), 0, 0, null);
            for (int quality : new int[]{88, 80, 70}) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out);
                if (out.size() <= MAX_IMAGE_BYTES) return out.toByteArray();
            }
        }
        throw new ApiException(413, "Ảnh quá lớn, không thể nén xuống dưới 4MB. Hãy cắt bớt hoặc chụp lại với độ phân giải thấp hơn.", null);
    }

    private static String docxText(byte[] zip) throws IOException {
        String xml = zipEntries(zip, "word/document.xml").get("word/document.xml");
        if (xml == null) return "";
        // Each <w:p> is a paragraph made of <w:t> runs
        StringBuilder out = new StringBuilder();
        for (String para : xml.split("</w:p>")) {
            String line = runs(para, "w:t").trim();
            if (!line.isEmpty()) out.append(line).append('\n');
        }
        return out.toString().trim();
    }

    private static String pptxText(byte[] zip) throws IOException {
        TreeMap<Integer, String> slides = new TreeMap<>();
        Pattern slide = Pattern.compile("^ppt/slides/slide(\\d+)\\.xml$");
        for (java.util.Map.Entry<String, String> e : zipEntries(zip, null).entrySet()) {
            Matcher m = slide.matcher(e.getKey());
            if (m.matches()) slides.put(Integer.parseInt(m.group(1)), e.getValue());
        }
        StringBuilder out = new StringBuilder();
        int idx = 1;
        for (String xml : slides.values()) {
            List<String> paras = new ArrayList<>();
            for (String para : xml.split("</a:p>")) {
                String line = runs(para, "a:t").trim();
                if (!line.isEmpty()) paras.add(line);
            }
            if (!paras.isEmpty()) out.append("Slide ").append(idx).append(":\n").append(String.join("\n", paras)).append("\n\n");
            idx++;
        }
        return out.toString().trim();
    }

    private static String runs(String xml, String tag) {
        Matcher m = Pattern.compile("<" + tag + "(?:\\s[^>]*)?>([^<]*)</" + tag + ">").matcher(xml);
        StringBuilder sb = new StringBuilder();
        while (m.find()) sb.append(m.group(1));
        return sb.toString().replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    /** XML entries of an Office zip (only `only` when given) */
    private static java.util.Map<String, String> zipEntries(byte[] zip, String only) throws IOException {
        java.util.Map<String, String> out = new java.util.HashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory() || !e.getName().endsWith(".xml") || (only != null && !only.equals(e.getName()))) continue;
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[16 * 1024];
                int n;
                while ((n = zin.read(b)) > 0) buf.write(b, 0, n);
                out.put(e.getName(), buf.toString("UTF-8"));
            }
        }
        return out;
    }
}
