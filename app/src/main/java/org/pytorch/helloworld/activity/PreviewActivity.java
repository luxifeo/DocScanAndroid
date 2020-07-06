package org.pytorch.helloworld.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.helloworld.processing.ProcessImage;
import org.pytorch.helloworld.R;
import org.pytorch.helloworld.view.PolygonView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PreviewActivity extends AppCompatActivity {
    private static final int TABLE_SHOW = 1;
    private String[][] tableData;
    private Mat table;
    private Mat gray_table;
    private TessBaseAPI mTess;
    private Module module;
    private ImageView imageView;
    private View progressView;
    private Handler handler = new Handler();
    static {
        if (!OpenCVLoader.initDebug())
            Log.d("ERROR", "Unable to load OpenCV");
        else
            Log.d("SUCCESS", "OpenCV loaded");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        Intent intent = getIntent();
        Uri uri = Uri.parse(intent.getStringExtra("URI"));
        imageView = findViewById(R.id.preview_image);
        progressView = findViewById(R.id.llProgressBar);
        Bitmap bitmap;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
            getTableFromBitmapAndShow(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "IO error", Toast.LENGTH_SHORT).show();
        }

        final Button buttonProcess = findViewById(R.id.buttonProcess);
        buttonProcess.setOnClickListener(view -> {
            if (table == null) {
                Toast.makeText(this, R.string.table_not_found, Toast.LENGTH_SHORT).show();
            } else {
                new Thread(() -> {
                    handler.post(() -> progressView.setVisibility(View.VISIBLE));
                    tableData = processTable(table, gray_table);
                    handler.post(() -> progressView.setVisibility(View.INVISIBLE));
                    launchTableActivity(buttonProcess);
                }).start();
            }

        });

        try {
            module = Module.load(assetFilePath(this, "model8770-new.pt"));
            // PC version must match Android version
            // Warm up model
            float[] input = new float[1 * 1 * 100 * 40];
            for (int i = 0; i < input.length; i++) {
                input[i] = 0;
            }
            long[] shape = {1, 1, 100, 40};
            Tensor inputTensor = Tensor.fromBlob(input, shape);
            module.forward(IValue.from(inputTensor)).toTensor();
            // Prepare OCR
            prepareLanguageDir();
            mTess = new TessBaseAPI();
            mTess.init(String.valueOf(getFilesDir()), "vie");
        } catch (IOException e) {
            Toast.makeText(this, "Cannot load the model", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void getTableFromBitmapAndShow(Bitmap bitmap) {
        // Convert Bitmap to Mat
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (height < width) {
            bitmap = RotateBitmap(bitmap, 90);
        }
        Mat mat = new Mat();
        Bitmap bmp32 = bitmap.copy(bitmap.getConfig(), true);
        Utils.bitmapToMat(bmp32, mat);
        // Resize Mat
        Mat small = new Mat();
        int orgHeight = mat.height();
        int smallHeight = 500;
        double ratio = (double) smallHeight / orgHeight;
        Imgproc.resize(mat, small, new Size(0, 0), ratio, ratio, Imgproc.INTER_LINEAR);
        // Calculate Canny
        Mat gray1 = new Mat();
        Mat threshold = new Mat();

        Imgproc.cvtColor(small, gray1, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(gray1, threshold, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
        Scalar mean = Core.mean(threshold);
        double meanVal = mean.val[0];
        Mat paper;
        List<MatOfPoint> contours = new ArrayList<>();
        if (meanVal > 200) {
            paper = mat;
        } else {
            Mat edge = new Mat();
            Imgproc.Canny(gray1, edge, 75, 200);
            // Close potential edge
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
            Mat close = new Mat();
            Imgproc.morphologyEx(edge, close, Imgproc.MORPH_CLOSE, kernel);



            // find max area
            Imgproc.findContours(close, contours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
            ProcessImage.sortContours(contours);
            MatOfPoint2f temp = new MatOfPoint2f();
            MatOfPoint2f approx = new MatOfPoint2f();
            int maxAreaIndex = 0;
            for (MatOfPoint contour : contours) {
                contour.convertTo(temp, CvType.CV_32FC2);
                double peri = Imgproc.arcLength(temp, true);
                Imgproc.approxPolyDP(temp, approx, 0.05 * peri, true);
                if (approx.total() == 4) {
                    break;
                }
                maxAreaIndex++;
                // Search only 5 biggest contour
                if (maxAreaIndex == 5) {
                    break;
                }
            }
            if (maxAreaIndex == 5) {
                imageView.setImageBitmap(bitmap);
                Toast.makeText(this, R.string.table_not_found, Toast.LENGTH_SHORT).show();
                return;
            }
            Point[] points = approx.toArray();
            for (Point point : points) {
                point.x /= ratio;
                point.y /= ratio;
            }
            approx.fromArray(points);

            paper = ProcessImage.fourPointTransform(mat, approx);
        }

        height = paper.height();
        width = paper.width();
        Imgproc.cvtColor(paper, gray1, Imgproc.COLOR_BGR2GRAY);
        Mat thresh = new Mat();
        Imgproc.threshold(gray1, thresh, 127, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);
        Imgproc.findContours(thresh, contours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        MatOfPoint2f temp = new MatOfPoint2f();
        MatOfPoint2f approx = new MatOfPoint2f();
        ProcessImage.sortContours(contours);
        int maxAreaIndex = 0;
        MatOfInt hull = new MatOfInt();
        for (MatOfPoint contour : contours) {
            contour.convertTo(temp, CvType.CV_32FC2);
            Imgproc.convexHull(contour, hull);
            if (Imgproc.contourArea(contour) / (height * width) > 0.9) {
                maxAreaIndex++;
                continue;
            }
            Point[] contourArray = contour.toArray();
            Point[] hullPoints = new Point[hull.rows()];
            int[] hullContourIdxList = hull.toArray();
            for (int i = 0; i < hullContourIdxList.length; i++) {
                hullPoints[i] = contourArray[hullContourIdxList[i]];
            }
            MatOfPoint2f hullMat = new MatOfPoint2f(hullPoints);
            double peri = Imgproc.arcLength(hullMat, true);
            Imgproc.approxPolyDP(hullMat, approx, 0.05 * peri, true);
            break;
        }

//        if (approx.total() != 4) {
//            RotatedRect rotatedRect = Imgproc.minAreaRect(new MatOfPoint2f(hull));
//            Imgproc.boxPoints(rotatedRect, approx);
//        }

        if (maxAreaIndex == contours.size()) {
            return;
        }

        MatOfPoint approxf1 = new MatOfPoint();
        approx.convertTo(approxf1, CvType.CV_32S);
        contours.add(approxf1);
        List<MatOfPoint> contourTemp = new ArrayList<>();
        contourTemp.add(approxf1);
        Imgproc.drawContours(paper, contourTemp, 0, new Scalar(255, 0, 0, 255), 10);

        Mat smallPaper = new Mat();
        Imgproc.resize(paper, smallPaper, new Size(0, 0), 0.5, 0.5, Imgproc.INTER_LINEAR);
        Bitmap bitmapPaper = Bitmap.createBitmap(smallPaper.width(), smallPaper.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(smallPaper, bitmapPaper);
        this.table = ProcessImage.fourPointTransform(gray1, approx);
        imageView.setImageBitmap(bitmapPaper);
        this.gray_table = ProcessImage.fourPointTransform(thresh, approx);
    }


    private String[][] processTable(Mat table, Mat grayTable) {
        // Finding the and the vertices in the image
        int width = grayTable.width();
        Mat intersection = ProcessImage.findVerticesImage(grayTable);

        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(intersection, contours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        final List<Point> joints = new ArrayList<>();
        for (MatOfPoint contour : contours) {
            joints.add(new Point(contour.get(0, 0)));
        }
        // Sort vertices and groups into rows
        Collections.sort(joints, (point, t1) -> {
            if (point.y > t1.y) {
                return 1;
            } else if (point.y < t1.y) {
                return -1;
            }
            return 0;
        });
        List<List<Point>> group = new ArrayList<>();
        group.add(new ArrayList<Point>() {
            {
                add(joints.get(0));
            }
        });
        int group_id = 0;
        for (int i = 1; i < joints.size(); i++) {
            if (Math.abs(joints.get(i).y - joints.get(i - 1).y) < 10) {
                group.get(group_id).add(joints.get(i));
            } else {
                // Very likely this is a false straight line
                if (group.get(group_id).size() <= 2) {
                    int finalI = i;
                    group.set(group_id, new ArrayList<Point>() {
                        {
                            add(joints.get(finalI));
                        }
                    });
                } else {
                    group_id++;
                    int finalI1 = i;
                    group.add(new ArrayList<Point>() {
                        {
                            add(joints.get(finalI1));
                        }
                    });
                }
            }
        }
        //
        for (List<Point> g : group) {
            Collections.sort(g, (point, t1) -> {
                if (point.x > t1.x) {
                    return 1;
                } else if (point.x < t1.x) {
                    return -1;
                }
                return 0;
            });
        }

        ArrayList<Integer>[] kp_count = new ArrayList[9];

        // initializing
        for (int i = 0; i < kp_count.length; i++) {
            kp_count[i] = new ArrayList<>();
        }
        for (int i = 0; i < group.size(); i++) {
            kp_count[group.get(i).size()].add(i);
        }
        // 7 is the number of columns, and 8 is the number of joints
        int[] avg_x = new int[8];
        if (kp_count[8].size() > 0) {
            for (int i : kp_count[8]) {
                for (int j = 0; j < 8; j++) {
                    avg_x[j] += group.get(i).get(j).x;
                }
            }

            for (int i = 0; i < avg_x.length; i++) {
                avg_x[i] /= kp_count[8].size();
            }
        } else { // Fail back to hard coded config
            avg_x = new int[]{0, 55, 166, 463, 732, 832, 963, 1117};
            for (int i = 0; i < avg_x.length; i++) {
                avg_x[i] = avg_x[i] * width / 1117;
            }
        }

        for (int i = 0; i < 8; i++) {
            for (int idx : kp_count[i]) {
                List<Point> g = group.get(idx);
                for (int j = 0; j < 8; j++) {
                    int g_size = g.size();
                    int group_y = (int) g.get(g_size - 1).y;
                    if (j == g.size()) {
                        g.add(j, new Point(avg_x[j], group_y));
                    } else if (Math.abs(avg_x[j] - g.get(j).x) > 15) {
                        g.add(j, new Point(avg_x[j], g.get(j).y));
                    }
                }
            }
        }

        String[][] tableData = new String[group.size() - 2][5];
        for (int i = 1; i < group.size() - 1; i++) {
            // Deal with 4 columns
            for (int j = 0; j < 4; j++) {
                Mat box = ProcessImage.getBox(i, j, table, group);
                int box_width = box.width();
                int box_height = box.height();
                Bitmap temp_bitmap = Bitmap.createBitmap(box_width - 10, box_height - 10, Bitmap.Config.ARGB_8888);
                Mat pad_box = box.submat(5, box_height - 5, 5, box_width - 5);
                Utils.matToBitmap(pad_box, temp_bitmap);
                mTess.setImage(temp_bitmap);
                tableData[i - 1][j] = mTess.getUTF8Text();
            }

            // Dealt with score cells
            Mat box = ProcessImage.getBox(i, 4, table, group);
            Imgproc.resize(box, box, new Size(100, 40));
            Mat transposeBox = new Mat();
            Core.transpose(box, transposeBox);
            transposeBox.convertTo(transposeBox, CvType.CV_64FC1);
            Core.divide(transposeBox, new Scalar(255.0), transposeBox);
            transposeBox.convertTo(transposeBox, CvType.CV_32FC1);
            float[] input = new float[1 * 1 * 100 * 40];
            long[] shape = {1, 1, 100, 40};
            transposeBox.get(0, 0, input);
            final Tensor inputTensor = Tensor.fromBlob(input, shape);
            final Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();
            final float[] scores = outputTensor.getDataAsFloatArray();
            tableData[i - 1][4] = decodeOutput(scores);
        }

        return tableData;

    }

    private String decodeOutput(float[] scores) {
        // scores shape is
        String[] characters = ProcessImage.characters;
        String result = "";
        String last_char = "";
        int row = scores.length / characters.length;
        int col = characters.length;
        // Skip first two row
        for (int i = 2; i < row; i++) {
            int max_index = -1;
            float max_score = Float.MIN_VALUE;
            for (int j = 0; j < col; j++) {
                if (scores[i * col + j] > max_score) {
                    max_index = j;
                    max_score = scores[i * col + j];
                }
            }
            String next_char = characters[max_index];
            if (!next_char.equals(last_char)) {
                if (next_char.equals(",")) {
                    result = result.concat(".");
                } else
                    result = result.concat(next_char);
            }
            last_char = characters[max_index];
        }

        return result;
    }

    public void launchTableActivity(View view) {
        Intent intent = new Intent(this, TableActivity.class);
        Bundle b = new Bundle();
        b.putSerializable("TABLE DATA", this.tableData);
        intent.putExtras(b);

        startActivityForResult(intent, TABLE_SHOW);
    }

    private void prepareLanguageDir() throws IOException {
        File dir = new File(getFilesDir() + "/tessdata");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File trainedData = new File(getFilesDir() + "/tessdata/vie.traineddata");
        if (!trainedData.exists()) {
            copyFile();
        }
    }

    private void copyFile() throws IOException {
        // work with assets folder
        AssetManager assMng = getAssets();
        InputStream is = assMng.open("tessdata/vie.traineddata");
        OutputStream os = new FileOutputStream(getFilesDir() + "/tessdata/vie.traineddata");
        byte[] buffer = new byte[1024];
        int read;
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
        }
        is.close();
        os.flush();
        os.close();
    }

    public static Bitmap RotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }
}
