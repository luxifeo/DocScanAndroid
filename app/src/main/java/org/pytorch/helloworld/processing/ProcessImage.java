package org.pytorch.helloworld.processing;

import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ProcessImage {
    public static String[] characters = {"", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            ",", ".", "-", "/", " "};
    public static int findBiggestContour(List<MatOfPoint> contours) {
        double maxArea = 0;
        int maxIndex = 0;
        int currIndex = 0;
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > maxArea) {
                maxIndex = currIndex;
                maxArea = area;
            }
            currIndex++;
        }

//        Collections.sort(contours, new Comparator<MatOfPoint>() {
//            @Override
//            public int compare(MatOfPoint matOfPoint, MatOfPoint t1) {
//                double area1 = Imgproc.contourArea(matOfPoint);
//                double area2 = Imgproc.contourArea(t1);
//                if (area1 < area2) {
//                    return -1;
//                } else if (area1 > area2) {
//                    return 1;
//                } return 0;
//            }
//        });
        return currIndex;
    }

    public static Mat fourPointTransform(Mat src, MatOfPoint2f contour) {
        // Length of contour is 4

        Point[] points = contour.toArray();
        Arrays.sort(points, new Comparator<Point>() {
            @Override
            public int compare(Point point, Point t1) {
                return Double.compare(point.x, t1.x);
            }
        });
        Point tl, bl, br, tr;
        if (points[0].y < points[1].y) {
            tl = points[0];
            bl = points[1];
        } else {
            tl = points[1];
            bl = points[0];
        }

        double dist1 = Math.pow(tl.x - points[2].x, 2) + Math.pow(tl.y - points[2].y, 2);
        double dist2 = Math.pow(tl.x - points[3].x, 2) + Math.pow(tl.y - points[3].y, 2);
        if (dist1 > dist2) {
            br = points[2];
            tr = points[3];
        } else {
            br = points[3];
            tr = points[2];
        }

        double widthA = Math.sqrt(Math.pow(br.x - bl.x, 2) + Math.pow(br.y - bl.y, 2));
        double widthB = Math.sqrt(Math.pow(tr.x - tl.x, 2) + Math.pow(tr.y - tl.y, 2));
        double maxWidth = Math.max(widthA, widthB);

        double heightA = Math.sqrt(Math.pow(br.x - tr.x, 2) + Math.pow(br.y - tr.y, 2));
        double heightB = Math.sqrt(Math.pow(bl.x - tl.x, 2) + Math.pow(bl.y - tl.y, 2));
        double maxHeight = Math.max(heightA, heightB);


        MatOfPoint rect = new MatOfPoint();
        rect.fromArray(tl, tr, br, bl);
        rect.convertTo(rect, CvType.CV_32F);
        MatOfPoint warped = new MatOfPoint();
        warped.fromArray(
                new Point(0, 0),
                new Point(maxWidth - 1, 0),
                new Point(maxWidth - 1, maxHeight - 1),
                new Point(0, maxHeight - 1));
        warped.convertTo(warped, CvType.CV_32F);
        Mat M = new Mat();
        try {
            M = Imgproc.getPerspectiveTransform(rect, warped);
        } catch (Exception E) {
            Log.wtf("Error", E.getMessage());
        }
        Mat dst = new Mat();
        Imgproc.warpPerspective(src, dst, M, new Size(maxWidth, maxHeight));

        return dst;
    }

    public static void sortContours(List<MatOfPoint> contours) {
        Collections.sort(contours, new Comparator<MatOfPoint>() {
            @Override
            public int compare(MatOfPoint matOfPoint, MatOfPoint t1) {
                double area1 = Imgproc.contourArea(matOfPoint);
                double area2 = Imgproc.contourArea(t1);
                if (area1 < area2) {
                    return 1;
                } else if (area1 > area2) {
                    return -1;
                } return 0;
            }
        });
    }

    public static Mat isolateLines(Mat src, Mat struct) {
        Mat erode = new Mat();
        Imgproc.erode(src, erode, struct, new Point(-1, -1), 1, Core.BORDER_CONSTANT, new Scalar(0));
        Mat dilate = new Mat();
        Imgproc.dilate(erode, dilate, struct);
        return dilate;
    }

    public static MatOfPoint2f getSpecificBox(int i, int order, List<List<Point>> group) {
        Point tl = group.get(i).get(order);
        Point tr = group.get(i).get(order + 1);
        Point br = group.get(i + 1).get(order + 1);
        Point bl = group.get(i + 1).get(order);
        return new MatOfPoint2f(tl, tr, br, bl);
    }

    public static Mat getBox(int i, int order, Mat table, List<List<Point>> group) {
        MatOfPoint2f box_point = getSpecificBox(i, order, group);
        return fourPointTransform(table, box_point);
    }

    public static Mat findVerticesImage(Mat grayTable) {
        int width = grayTable.width();
        Mat h_structure = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size((int) (width / 15), 1));
        Mat w_structure = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, (int) (width / 15)));
        Mat horizontal = ProcessImage.isolateLines(grayTable, h_structure);
        Mat vertical = ProcessImage.isolateLines(grayTable, w_structure);
        Mat intersection = new Mat();
        Core.bitwise_and(horizontal, vertical, intersection);
        return intersection;
    }
}
