package com.randomappsinc.objectrecognition;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.List;

class PhotoTakerManager {

    public interface Listener {
        void onTakePhotoFailure();

        void onTakePhotoSuccess(Uri takenPhotoUri, float rotation);
    }

    private final Listener listener;
    private final Handler backgroundHandler;
    private Uri currentPhotoUri;

    PhotoTakerManager(Listener listener) {
        this.listener = listener;
        HandlerThread handlerThread = new HandlerThread("Camera Photos Processor");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
    }

    @Nullable
    Intent getPhotoTakingIntent(Context context) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File currentPhotoFile = FileUtils.createImageFile(context);
        if (currentPhotoFile != null) {
            currentPhotoUri = FileProvider.getUriForFile(
                    context,
                    Constants.FILE_PROVIDER_AUTHORITY,
                    currentPhotoFile);

            // Grant access to content URI so camera app doesn't crash
            List<ResolveInfo> resolvedIntentActivities = context.getPackageManager()
                    .queryIntentActivities(takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY);

            for (ResolveInfo resolvedIntentInfo : resolvedIntentActivities) {
                String packageName = resolvedIntentInfo.activityInfo.packageName;
                context.grantUriPermission(packageName, currentPhotoUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
            return takePictureIntent;
        } else {
            return null;
        }
    }

    void processTakenPhoto(final Context context) {
        backgroundHandler.post(() -> {
            context.revokeUriPermission(
                    currentPhotoUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                float rotation = ImageUtils.getImageRotation(context, currentPhotoUri);
                listener.onTakePhotoSuccess(currentPhotoUri, rotation);
            } catch (IOException exception) {
                listener.onTakePhotoFailure();
            }
        });
    }

    void deleteLastTakenPhoto() {
        FileUtils.deleteCameraImageWithUri(currentPhotoUri);
    }
}
