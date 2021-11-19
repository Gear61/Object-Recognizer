package com.randomappsinc.objectrecognition;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.common.modeldownload.FirebaseLocalModel;
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager;
import com.google.firebase.ml.custom.FirebaseModelDataType;
import com.google.firebase.ml.custom.FirebaseModelInputOutputOptions;
import com.google.firebase.ml.custom.FirebaseModelInputs;
import com.google.firebase.ml.custom.FirebaseModelInterpreter;
import com.google.firebase.ml.custom.FirebaseModelOptions;
import com.squareup.picasso.Picasso;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements PhotoTakerManager.Listener {

    // Request codes
    private static final int IMAGE_FILE_REQUEST_CODE = 1;
    private static final int CAMERA_CODE = 2;

    private static final String LABEL_PATH = "labels.txt";
    private static final String LOCAL_MODEL_ASSET = "mobilenet_v1_1.0_224_quant.tflite";

    private static final int DIM_BATCH_SIZE = 1;
    private static final int DIM_PIXEL_SIZE = 3;
    private static final int DIM_IMG_SIZE_X = 224;
    private static final int DIM_IMG_SIZE_Y = 224;
    private static final int RESULTS_TO_SHOW = 3;

    @BindView(R.id.image_view) ImageView imageView;
    @BindView(R.id.no_image_text) View noImageText;
    @BindView(R.id.analysis_overlay) TextView analysisOverlay;

    private final PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    (o1, o2) -> (o1.getValue()).compareTo(o2.getValue()));
    private final int[] intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];

    private List<String> labelList;
    private FirebaseModelInterpreter interpreter;
    private FirebaseModelInputOutputOptions dataOptions;
    private PhotoTakerManager photoTakerManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        initModel();
        photoTakerManager = new PhotoTakerManager(this);
    }

    private void initModel() {
        labelList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(LABEL_PATH)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                labelList.add(line);
            }
        } catch (IOException e) {
            UIUtils.showLongToast(R.string.labels_fail, this);
            return;
        }

        int[] inputDims = {DIM_BATCH_SIZE, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y, DIM_PIXEL_SIZE};
        int[] outputDims = {DIM_BATCH_SIZE, labelList.size()};
        try {
            dataOptions =
                    new FirebaseModelInputOutputOptions.Builder()
                            .setInputFormat(0, FirebaseModelDataType.BYTE, inputDims)
                            .setOutputFormat(0, FirebaseModelDataType.BYTE, outputDims)
                            .build();
            FirebaseLocalModel localModel =
                    new FirebaseLocalModel.Builder("asset")
                            .setAssetFilePath(LOCAL_MODEL_ASSET).build();
            FirebaseModelManager manager = FirebaseModelManager.getInstance();
            manager.registerLocalModel(localModel);
            FirebaseModelOptions modelOptions =
                    new FirebaseModelOptions.Builder()
                            .setLocalModelName("asset")
                            .build();
            interpreter = FirebaseModelInterpreter.getInstance(modelOptions);
        } catch (FirebaseMLException e) {
            UIUtils.showLongToast(R.string.model_fail, this);
        }
    }

    @OnClick(R.id.take_with_camera)
    public void takeWithCamera() {
        if (PermissionUtils.isPermissionGranted(Manifest.permission.CAMERA, this)) {
            startCameraPage();
        } else {
            PermissionUtils.requestPermission(this, Manifest.permission.CAMERA, CAMERA_CODE);
        }
    }

    @OnClick(R.id.upload_from_file)
    public void uploadFromFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, IMAGE_FILE_REQUEST_CODE);
    }

    @OnClick(R.id.analyze)
    public void analyze() {
        if (interpreter == null) {
            UIUtils.showLongToast(R.string.model_fail, this);
            return;
        }

        BitmapDrawable bitmapDrawable = (BitmapDrawable) imageView.getDrawable();
        if (bitmapDrawable == null) {
            UIUtils.showLongToast(R.string.image_not_loaded, this);
            return;
        }

        ByteBuffer imgData = convertBitmapToByteBuffer(((BitmapDrawable) imageView.getDrawable()).getBitmap());
        try {
            FirebaseModelInputs inputs = new FirebaseModelInputs.Builder().add(imgData).build();
            interpreter
                    .run(inputs, dataOptions)
                    .addOnFailureListener(e ->
                            UIUtils.showLongToast(R.string.analysis_fail, MainActivity.this))
                    .continueWith(
                            task -> {
                                byte[][] labelProbArray = task.getResult().getOutput(0);
                                List<String> topLabels = getTopLabels(labelProbArray);
                                StringBuilder builder = new StringBuilder();
                                for (int i = topLabels.size() - 1; i >= 0; i--) {
                                    builder.append(topLabels.get(i));
                                    if (i > 0) {
                                        builder.append("\n");
                                    }
                                }
                                analysisOverlay.setText(builder);
                                analysisOverlay.setVisibility(View.VISIBLE);
                                return topLabels;
                            });
        } catch (FirebaseMLException e) {
            UIUtils.showLongToast(R.string.analysis_fail, this);
        }
    }

    private synchronized List<String> getTopLabels(byte[][] labelProbArray) {
        for (int i = 0; i < labelList.size(); ++i) {
            sortedLabels.add(new AbstractMap.SimpleEntry<>(
                    labelList.get(i), (labelProbArray[0][i] & 0xff) / 255.0f));
            if (sortedLabels.size() > RESULTS_TO_SHOW) {
                sortedLabels.poll();
            }
        }
        List<String> result = new ArrayList<>();
        final int size = sortedLabels.size();
        for (int i = 0; i < size; ++i) {
            Map.Entry<String, Float> label = sortedLabels.poll();
            result.add(label.getKey() + ": " + label.getValue());
        }
        return result;
    }

    private synchronized ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer imgData =
                ByteBuffer.allocateDirect(
                        DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
        imgData.order(ByteOrder.nativeOrder());
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y,
                true);
        imgData.rewind();
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.getWidth(), 0, 0,
                scaledBitmap.getWidth(), scaledBitmap.getHeight());
        // Convert the image to int points.
        int pixel = 0;
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                final int val = intValues[pixel++];
                imgData.put((byte) ((val >> 16) & 0xFF));
                imgData.put((byte) ((val >> 8) & 0xFF));
                imgData.put((byte) (val & 0xFF));
            }
        }
        return imgData;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        switch (requestCode) {
            case IMAGE_FILE_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK && resultData != null && resultData.getData() != null) {
                    noImageText.setVisibility(View.GONE);
                    analysisOverlay.setVisibility(View.GONE);
                    Uri uri = resultData.getData();

                    // Persist ability to read from this file
                    getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                    try {
                        float rotation = ImageUtils.getImageRotation(this, uri);
                        String uriString = uri.toString();
                        Picasso.get()
                                .load(uriString)
                                .rotate(rotation)
                                .fit()
                                .centerCrop()
                                .into(imageView);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case CAMERA_CODE:
                if (resultCode == RESULT_OK) {
                    analysisOverlay.setVisibility(View.GONE);
                    noImageText.setVisibility(View.GONE);
                    photoTakerManager.processTakenPhoto(this);
                } else if (resultCode == RESULT_CANCELED) {
                    photoTakerManager.deleteLastTakenPhoto();
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        if (requestCode != CAMERA_CODE
                || grantResults.length <= 0
                || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        // Camera permission granted
        startCameraPage();
    }

    private void startCameraPage() {
        Intent takePhotoIntent = photoTakerManager.getPhotoTakingIntent(this);
        if (takePhotoIntent == null) {
            UIUtils.showLongToast(R.string.take_photo_with_camera_failed, this);
        } else {
            startActivityForResult(takePhotoIntent, CAMERA_CODE);
        }
    }

    @Override
    public void onTakePhotoFailure() {
        UIUtils.showLongToast(R.string.take_photo_with_camera_failed, this);
    }

    @Override
    public void onTakePhotoSuccess(Uri takenPhotoUri, float rotation) {
        runOnUiThread(() -> Picasso.get()
                .load(takenPhotoUri)
                .rotate(rotation)
                .fit()
                .centerCrop()
                .into(imageView));
    }
}
