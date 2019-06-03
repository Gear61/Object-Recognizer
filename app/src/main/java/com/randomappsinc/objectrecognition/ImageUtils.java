package com.randomappsinc.objectrecognition;

import android.content.Context;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.io.InputStream;

class ImageUtils {

    static float getImageRotation(Context context, Uri takenPhotoUri) throws IOException {
        InputStream input = context.getContentResolver().openInputStream(takenPhotoUri);
        ExifInterface exifInterface = new ExifInterface(input);

        int orientation = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }
}
