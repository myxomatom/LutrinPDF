// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Content provider of the instrumented test APK: serves the test PDF files of {@code core:testing}
 * ({@code TestFiles.Pdf}) at {@code content://lutrin.androidtest.documents/<asset path>}, as a file
 * manager would serve a document picked with « Ouvrir avec » or « Partager ».
 *
 * <p>Written in Java on purpose. A provider declared in the test manifest runs in the process of
 * the test APK, which does not contain the Kotlin standard library (the Android Gradle Plugin
 * leaves out the dependencies shared with the tested app). A Kotlin provider crashes there with
 * {@code NoClassDefFoundError: kotlin.jvm.internal.Intrinsics}. Java needs no such runtime.
 */
public final class TestDocumentProvider extends ContentProvider {
    /** Authority declared in {@code src/androidTest/AndroidManifest.xml}. */
    public static final String AUTHORITY = "lutrin.androidtest.documents";

    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final int COPY_BUFFER_SIZE = 8192;

    /**
     * Builds the URI of a test asset.
     *
     * @param asset asset path, such as {@code TestFiles.Pdf.SIMPLE}
     * @return the content URI served by this provider
     */
    public static Uri uriOf(String asset) {
        return Uri.parse("content://" + AUTHORITY + "/" + asset);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        String path = uri.getPath();
        return path != null && path.endsWith(".pdf") ? PDF_MIME_TYPE : null;
    }

    /** Returns a real file descriptor, seekable like the ones of the system providers. */
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context context = getContext();
        String path = uri.getPath();
        if (context == null || path == null) {
            throw new FileNotFoundException("No context or path");
        }
        String asset = path.startsWith("/") ? path.substring(1) : path;
        File file = new File(context.getCacheDir(), asset.substring(asset.lastIndexOf('/') + 1));
        try {
            if (!file.exists()) {
                copyAsset(context, asset, file);
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Cannot read " + asset + ": " + e.getMessage());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private static void copyAsset(Context context, String asset, File target) throws IOException {
        try (InputStream input = context.getAssets().open(asset);
                OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
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
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
