package com.magpie.android;

import android.content.ContentProvider;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * 给系统安装器提供 APK 的 content:// 读取通道。
 *
 * <p>这个壳不引 AndroidX，所以没有 androidx 的 FileProvider；这里手写一个
 * 最小版：按 URI 第一段分发——{@code apk/&lt;名&gt;} 读 {@code cacheDir/apk/}，
 * {@code download/&lt;名&gt;} 读应用私有外部目录 {@code Download/}。最终路径做
 * canonical 前缀校验，禁止越出授权目录。Android 7+ 对 file:// 抛
 * FileUriExposedException，装 APK 必须走 content:// + 临时授权。
 */
public final class ApkFileProvider extends ContentProvider {

    /** Manifest 里配置的 authority，保持一致。 */
    public static final String AUTHORITY = "com.magpie.android.fileprovider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        // URI 形如 apk/<名> 或 download/<名>，按第一段分发到对应目录。
        // 注意：lastPathSegment 只给最后一段（name.zip），拿不到 "download/" 前缀，
        // 所以必须用 getPathSegments() 按段取。
        List<String> segs = uri.getPathSegments();
        String kind = (segs != null && segs.size() >= 1) ? segs.get(0) : null;
        String name = (segs != null && segs.size() >= 2) ? segs.get(1) : null;
        if (kind == null || name == null || name.isEmpty()
                || ".".equals(name) || "..".equals(name)) {
            throw new FileNotFoundException("bad uri: " + uri);
        }
        File dir;
        if ("download".equals(kind)) {
            // 诊断包：读应用私有外部目录 Download
            File ext = getContext().getExternalFilesDir(null);
            if (ext == null) {
                throw new FileNotFoundException("external files dir unavailable: " + uri);
            }
            dir = new File(ext, "Download");
        } else if ("apk".equals(kind)) {
            // 壳自更新 APK：读 cacheDir/apk
            dir = new File(getContext().getCacheDir(), "apk");
        } else {
            throw new FileNotFoundException("unknown path kind: " + uri);
        }
        File f = new File(dir, name);
        // 防穿越：最终文件 canonical 路径必须在授权目录内
        try {
            String base = dir.getCanonicalPath();
            String target = f.getCanonicalPath();
            if (!target.equals(base) && !target.startsWith(base + File.separator)) {
                throw new FileNotFoundException("outside base dir: " + uri);
            }
        } catch (IOException e) {
            throw new FileNotFoundException("path check failed: " + uri);
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        List<String> segs = uri.getPathSegments();
        String name = (segs != null && segs.size() >= 2)
                ? segs.get(1) : uri.getLastPathSegment();
        if (name != null && name.endsWith(".zip")) {
            return "application/zip";
        }
        return "application/vnd.android.package-archive";
    }

    /**
     * 生成诊断包的分享 URI（FileProvider 授权给邮箱 App 读）。
     *
     * @param f 在 getExternalFilesDir/Download 下的 zip
     */
    static android.net.Uri shareUri(Context ctx, File f) {
        return android.net.Uri.parse("content://" + AUTHORITY + "/download/" + f.getName());
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
