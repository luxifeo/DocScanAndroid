package org.pytorch.helloworld.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.pytorch.helloworld.processing.ProcessImage;
import org.pytorch.helloworld.R;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int SHOW_PREVIEW = 1;
    private static final int RESULT_LOAD_IMAGE = 2;
    private Preview preview;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private int  pointCount = 0;
    private ExecutorService cameraExecutor;
    private PreviewView previewView;
    private ImageView imageView;
    private String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
    private int REQUEST_CODE_PERMISSIONS = 10;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        if (!OpenCVLoader.initDebug())
            Log.wtf("OpenCv", "Unable to load OpenCV");
        else
            Log.wtf("OpenCv", "OpenCV loaded");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.viewFinder);
        imageView = findViewById(R.id.imageView);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        Button camera_capture_button = findViewById(R.id.camera_capture_button);
        camera_capture_button.setOnClickListener(v -> takePhoto());

        Button btnLoadGallery = findViewById(R.id.load_gallery_butoon);
        btnLoadGallery.setOnClickListener(v -> {
            if (allPermissionsGranted()) {
                startImageGalleryActivity();
            } else {
                ActivityCompat.requestPermissions(MainActivity.this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            }
        });
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void startImageGalleryActivity() {
        Intent i = new Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, RESULT_LOAD_IMAGE);
    }


    private void takePhoto() {
//        if (pointCount != 4) {
//            Toast.makeText(getApplicationContext(), "Paper not found" + String.valueOf(pointCount), Toast.LENGTH_SHORT).show();
//            return;
//        }
        File photoFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "" + System.currentTimeMillis() + "X.jpg");
        Uri fileUri = Uri.fromFile(photoFile);
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        imageCapture.takePicture(outputFileOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                // Jump to second activity
                // This stupid code run in background
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Saved " + photoFile.toString(), Toast.LENGTH_SHORT).show());
                launchSecondActivity(fileUri);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Cannot Save", Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();
            assert selectedImage != null;
            launchSecondActivity(selectedImage);
        }
    }

    private void launchSecondActivity(Uri uri) {
        Intent intent = new Intent(this, PreviewActivity.class);
        intent.putExtra("URI", uri.toString());
        startActivityForResult(intent, SHOW_PREVIEW);
    }

    private void startCamera() {

        cameraProviderFuture.addListener(() -> {
            preview = new Preview.Builder().build();
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
                Log.wtf("ERROR", e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Paint paint = getPaint();
        Paint textPaint = getTextPaint();
        preview = new Preview.Builder()
                .build();
        preview.setSurfaceProvider(previewView.createSurfaceProvider());
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, image -> {
            int rotation = image.getImageInfo().getRotationDegrees();
            int width = image.getWidth(); // 640
            int height = image.getHeight(); // 480
            // Turn ImageProxy to Gray Image
            // buf = gray Image
            Mat buf = new Mat(height, width, CvType.CV_8UC1);
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            buffer.rewind();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            buf.put(0, 0, bytes);
            Mat threshold = new Mat();
            Imgproc.threshold(buf, threshold, 0, 255, Imgproc.THRESH_OTSU + Imgproc.THRESH_BINARY);
            Scalar mean = Core.mean(threshold);

            // Canny Edge Detection
            if (rotation == 90) {
                Core.rotate(buf, buf, Core.ROTATE_90_CLOCKWISE);
            }
            height = buf.height();
            width = buf.width();
            Mat canny = new Mat();
            Imgproc.Canny(buf, canny, 64, 128);
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
//            Imgproc.dilate(canny, canny, kernel);
//            Imgproc.erode(canny, canny, kernel);
//            Imgproc.morphologyEx(canny, canny, Imgproc.MORPH_CLOSE, kernel);
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(canny, contours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
            ProcessImage.sortContours(contours);
            MatOfPoint2f temp = new MatOfPoint2f();
            MatOfPoint2f approx = new MatOfPoint2f();
            int maxAreaIndex = 0;
            double contourArea = 0;
            for (MatOfPoint contour : contours) {
                contour.convertTo(temp, CvType.CV_32FC2);
                double peri = Imgproc.arcLength(temp, true);
                Imgproc.approxPolyDP(temp, approx, 0.05 * peri, true);
                contourArea = Imgproc.contourArea(contour);
                maxAreaIndex++;
                break;
            }
            Bitmap overlay = Bitmap.createBitmap(previewView.getWidth(), previewView.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(overlay);

            if (maxAreaIndex > 0) {
                int canvasW = canvas.getWidth();
                int canvasH = canvas.getHeight();
                float ratioH = (float) height / canvasH;
                float ratioW = (float) width / canvasW;
                Path path = new Path();
                boolean firstPoint = true;
                float x, y;

                Point[] points = approx.toArray();
                for (Point point : points) {
                    x = (float) (point.x / ratioW);
                    y = (float) (point.y / ratioH);
                    if (firstPoint) {
                        path.moveTo(x, y);
                        firstPoint = false;
                    } else {
                        path.lineTo(x, y);
                    }
                }
                path.close();
                canvas.drawPath(path, paint);
//                canvas.drawText(contourArea + "-" + approx.size(), 50, 50, textPaint);
                pointCount = (int) approx.size().height;
            }
            canvas.drawText(mean.toString(), 50, 50, textPaint);
//            else {
//                canvas.drawText("No contour found", 50, 50, textPaint);
//            }
//            runOnUiThread(() -> imageView.setImageBitmap(overlay));
            image.close();
        });

        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(previewView.getDisplay().getRotation())
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build();
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalysis, imageCapture);
    }

    private boolean allPermissionsGranted() {

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private Paint getTextPaint() {
        Paint textPaint = new Paint();
        textPaint.setStrokeWidth(10f);
        textPaint.setTextSize(50);
        textPaint.setStyle(Paint.Style.STROKE);
        textPaint.setColor(Color.GREEN);
        return textPaint;
    }

    private Paint getPaint() {
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);
        paint.setStrokeWidth(12f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        return paint;
    }
}
