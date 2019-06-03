package com.randomappsinc.objectrecognition;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;

class FileUtils {

    static File createImageFile(Context context) {
        File imageFile;
        try {
            String imageFileName = String.valueOf(System.currentTimeMillis());
            File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            imageFile = File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException exception) {
            return null;
        }
        return imageFile;
    }

    static void deleteCameraImageWithUri(Uri uri) {
        String uriString = uri.toString();
        if (TextUtils.isEmpty(uriString)) {
            return;
        }

        String filePath = uriString.substring(uriString.lastIndexOf('/'));
        String completePath = Environment.getExternalStorageDirectory().getPath()
                + Constants.FILE_PROVIDER_PATH
                + filePath;
        File imageFile = new File(completePath);
        if (imageFile.exists()) {
            imageFile.delete();
        }
    }
}
