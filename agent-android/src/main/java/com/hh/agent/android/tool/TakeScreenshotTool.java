package com.hh.agent.android.tool;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import com.hh.agent.library.ToolExecutor;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * TakeScreenshot tool implementation.
 * Takes a screenshot of the current screen and saves it to gallery.
 */
public class TakeScreenshotTool implements ToolExecutor {

    private final Activity activity;

    public TakeScreenshotTool(Activity activity) {
        this.activity = activity;
    }

    @Override
    public String getName() {
        return "take_screenshot";
    }

    @Override
    public String execute(JSONObject args) {
        try {
            // Get the root view
            View rootView = activity.getWindow().getDecorView();
            rootView.setDrawingCacheEnabled(true);
            rootView.buildDrawingCache();

            Bitmap bitmap = Bitmap.createBitmap(rootView.getDrawingCache());
            rootView.setDrawingCacheEnabled(false);

            if (bitmap == null) {
                return "{\"success\": false, \"error\": \"capture_failed\"}";
            }

            String savedPath = saveBitmapToGallery(bitmap);

            if (savedPath == null) {
                return "{\"success\": false, \"error\": \"save_failed\"}";
            }

            return "{\"success\": true, \"path\": \"" + savedPath + "\"}";
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"execution_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    private String saveBitmapToGallery(Bitmap bitmap) {
        String filename = "screenshot_" + System.currentTimeMillis() + ".png";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for Android 10+
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MobileAgent");

            Uri uri = activity.getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (uri == null) {
                return null;
            }

            try {
                OutputStream out = activity.getContentResolver().openOutputStream(uri);
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.close();
                }
                return uri.toString();
            } catch (Exception e) {
                return null;
            }
        } else {
            // Legacy approach for older Android versions
            File picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
            File mobileAgentDir = new File(picturesDir, "MobileAgent");
            if (!mobileAgentDir.exists()) {
                mobileAgentDir.mkdirs();
            }

            File imageFile = new File(mobileAgentDir, filename);
            try {
                FileOutputStream out = new FileOutputStream(imageFile);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.close();

                // Notify gallery
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScanIntent.setData(Uri.fromFile(imageFile));
                activity.sendBroadcast(mediaScanIntent);

                return imageFile.getAbsolutePath();
            } catch (Exception e) {
                return null;
            }
        }
    }

    @Override
    public String getDescription() {
        return "截取当前屏幕";
    }

    @Override
    public String getArgsDescription() {
        return "无参数";
    }

    @Override
    public String getArgsSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }
}
