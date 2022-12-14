package com.example.arsudokusolver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.arsudokusolver.ml.Mnist;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SudokuDNS2 {
    private static final int HEIGHT = 630;
    private static final int WIDTH = 630;
    private static final int CONTOUR_SIZE = 1;
    private static final int INPUT_SIZE = 28;


    public static Bitmap solve(Context context, LinearLayout linearLayout, Bitmap image) {
        linearLayout.removeAllViews();

        Mat sudoku_grid = getSudokuGrid(context);

//        Converting bitmap to image
        Mat org_img = bitmapToMat(image);

        //Image of size (H x W)
        Mat img = new Mat();
        Imgproc.resize(org_img.clone(), img, new Size(WIDTH, HEIGHT));

        //Gray Scale Image
        Mat gray = new Mat();
        Imgproc.cvtColor(img.clone(), gray, Imgproc.COLOR_BGR2GRAY);

        //Blurring the Gray Image.
        Imgproc.GaussianBlur(gray.clone(), gray, new Size(3, 3), 5);

        //Adaptive Thresholding
        Mat thresh = new Mat();
        Imgproc.adaptiveThreshold(gray.clone(), thresh, 255,
                Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 21, 10);

        //Finding the Contours of the thresh image.
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        //Sorting the Contours based on Contour area
        Collections.sort(contours, (o1, o2) -> (int) (Imgproc.contourArea(o2) - Imgproc.contourArea(o1)));

        //Taking only largest contour
        MatOfPoint cnt = contours.get(0);

        //Applying contours to temp image
        Mat cnt_img = img.clone();

        //Traversing the Contours for Sudoku Detection
        MatOfPoint2f c2f = new MatOfPoint2f(cnt.toArray());
        double peri = Imgproc.arcLength(c2f, true);
        MatOfPoint2f approx = new MatOfPoint2f();
        Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true);

        Point[] points = approx.toArray();

        if (points.length == 4) {
            List<MatOfPoint> temp = new ArrayList<>();
            temp.add(cnt);
            Imgproc.drawContours(cnt_img, temp, -1, new Scalar(255, 0, 0, 255),
                    10, Imgproc.LINE_8);

            //Sorting 4 corners
            Point[] sortedPoints = getSortedPoints(cnt);

            //Getting the Warped Perspective Image.
            Mat warp_thresh_img = warpPerspective(thresh.clone(), sortedPoints);

//            Getting vertical and horizontal Lines.
//            Mat vhLines = getVHLines(warp_thresh_img);

            //
//            Imgproc.cvtColor(vhLines, vhLines, Imgproc.COLOR_BGR2GRAY);
//
            Mat diff = new Mat();
            Core.subtract(warp_thresh_img, sudoku_grid, diff);
//
//
//            List<Bitmap> listOfSubMats = getListOfSubMats(diff);
//            try {
//                Mnist model = Mnist.newInstance(context);
//                for (Bitmap subMat : listOfSubMats) {
//                    Bitmap bmp = Bitmap.createScaledBitmap(subMat, INPUT_SIZE, INPUT_SIZE, false);
//                    int predictedNumber;
//                    predictedNumber = classifyImage(model, bmp);
//
//                    LinearLayout linearLayout1 = new LinearLayout(context);
//                    linearLayout1.setOrientation(LinearLayout.HORIZONTAL);
//
//                    ImageView imageView = new ImageView(context);
//                    imageView.setImageBitmap(bmp);
//                    imageView.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
//                    linearLayout1.addView(imageView);
//
//                    TextView textView = new TextView(context);
//                    textView.setTextSize(22);
//                    textView.setText(String.valueOf(predictedNumber));
//                    linearLayout1.addView(textView);
//
//                    linearLayout.addView(linearLayout1);
//                }
//                model.close();
//            } catch (IOException e) {
//                // TODO Handle the exception
//            }

            return matToBitmap(diff);
        }
        return matToBitmap(cnt_img);
    }

    private static Mat getSudokuGrid(Context context) {
        Bitmap sudoku_grid_bm = BitmapFactory.decodeResource(context.getResources(), R.drawable.sudoku_grid);

        Mat sudoku_grid = bitmapToMat(sudoku_grid_bm);
        Imgproc.cvtColor(sudoku_grid.clone(), sudoku_grid, Imgproc.COLOR_BGR2GRAY);
        Core.bitwise_not(sudoku_grid, sudoku_grid);
//        Finding the Contours of the thresh image.
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(sudoku_grid, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);


        Imgproc.drawContours(sudoku_grid, contours, -1, new Scalar(255, 255, 255, 255),
                25, Imgproc.LINE_AA);

        Imgproc.resize(sudoku_grid, sudoku_grid, new Size(WIDTH, HEIGHT));
        return sudoku_grid;
    }

    private static Mat getVHLines(Mat mat) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE);

        int cropFactor = 10;
        int factor = 60;
        int lineWidth = 13;
        Mat cnt_img = Mat.zeros(mat.rows(), mat.cols(), CvType.CV_8UC3);
        for (MatOfPoint c :
                contours) {
            Rect rect = Imgproc.boundingRect(c);
            if (Math.abs(rect.height - rect.width) < cropFactor
                    && rect.height >= factor && rect.width >= factor
                    && rect.height < HEIGHT / 3 && rect.width < WIDTH / 3) {

                List<MatOfPoint> temp = new ArrayList<>();
                temp.add(c);
                Imgproc.drawContours(cnt_img, temp, -1, new Scalar(255, 255, 255, 255),
                        lineWidth, Imgproc.LINE_8);
            }
        }
        int border = 10;
        Imgproc.rectangle(cnt_img, new Point(1, 1), new Point(cnt_img.width(), cnt_img.height()), new Scalar(255, 255, 255, 255), border);
        return cnt_img;
    }

    private static int classifyImage(Mnist model, Bitmap bmp) {
        bmp = Bitmap.createScaledBitmap(bmp, 28, 28, false);
        // Creates inputs for reference.
        TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 28, 28}, DataType.FLOAT32);
        inputFeature0.loadBuffer(getByteBuffer(bmp));

        // Runs model inference and gets result.
        Mnist.Outputs outputs = model.process(inputFeature0);
        TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

        float[] confidence = outputFeature0.getFloatArray();
        int index = 0;
        for (int i = 1; i < confidence.length; i++) {
            if (confidence[index] < confidence[i]) {
                index = i;
            }
        }
        Log.d("MYAPP", Arrays.toString(confidence));
        return confidence[index] < 0.5f ? 0 : index;
    }

    private static ByteBuffer getByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * 28 * 28);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[28 * 28];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < INPUT_SIZE; j++) {

                int p = intValues[pixel++];

                int R = (p >> 16) & 0xff;
                int G = (p >> 8) & 0xff;
                int B = p & 0xff;
                float normalized = (R + G + B) / 3.0f / 255.0f;
                byteBuffer.putFloat(normalized);
            }
        }
        return byteBuffer;
    }

    @NonNull
    private static List<Bitmap> getListOfSubMats(Mat warp_thresh_img) {
        List<Bitmap> listOfSubMats = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                int start_x = j * (WIDTH / 9);
                int start_y = i * (HEIGHT / 9);
                Mat sub = new Mat(warp_thresh_img, new Rect(start_x, start_y, WIDTH / 9, HEIGHT / 9));

                int cropFactor = 3;
                Mat crp = sub.submat(new Rect(cropFactor, cropFactor, sub.width() - 2 * cropFactor, sub.height() - 2 * cropFactor));
                Imgproc.resize(crp, crp, new Size(INPUT_SIZE, INPUT_SIZE));
                listOfSubMats.add(matToBitmap(crp));
            }
        }
        return listOfSubMats;
    }

    @NonNull
    private static Point[] getSortedPoints(MatOfPoint cnt) {
        MatOfPoint2f c2f = new MatOfPoint2f(cnt.toArray());
        double peri = Imgproc.arcLength(c2f, true);
        MatOfPoint2f approx = new MatOfPoint2f();
        Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true);

        Point[] points = approx.toArray();
        Moments moment = Imgproc.moments(cnt);
        int x = (int) (moment.get_m10() / moment.get_m00());
        int y = (int) (moment.get_m01() / moment.get_m00());

        Point[] sortedPoints = new Point[4];

        for (int i = 0; i < 4; i++) {
            double data_x = points[i].x;
            double data_y = points[i].y;
            if (data_x < x && data_y < y) {
                sortedPoints[0] = new Point(data_x, data_y);
            } else if (data_x > x && data_y < y) {
                sortedPoints[1] = new Point(data_x, data_y);
            } else if (data_x < x && data_y > y) {
                sortedPoints[2] = new Point(data_x, data_y);
            } else if (data_x > x && data_y > y) {
                sortedPoints[3] = new Point(data_x, data_y);
            }
        }
        return sortedPoints;
    }

    private static Mat warpPerspective(Mat originalImg, Point[] sortedPoints) {
//        Prepare Mat src and det
        MatOfPoint2f src = new MatOfPoint2f(
                sortedPoints[0],
                sortedPoints[1],
                sortedPoints[2],
                sortedPoints[3]);

        MatOfPoint2f dst = new MatOfPoint2f(
                new Point(0, 0),
                new Point(WIDTH - 1, 0),
                new Point(0, HEIGHT - 1),
                new Point(WIDTH - 1, HEIGHT - 1)
        );
//        Use getPerspectiveTransform and wrapPerspective

        Mat warpMat = Imgproc.getPerspectiveTransform(src, dst);
        //This is you new image as Mat
        Mat destImage = new Mat();
        Imgproc.warpPerspective(originalImg, destImage, warpMat, originalImg.size());
        return destImage;
    }

    @NonNull
    private static List<MatOfPoint> getTopContours(List<MatOfPoint> contours) {
        List<MatOfPoint> temp = new ArrayList<>();
        for (int i = 0; i < Math.min(CONTOUR_SIZE, contours.size()); i++) {
            temp.add(contours.get(i));
        }
        contours = temp;
        return contours;
    }

    private static Bitmap matToBitmap(Mat mat) {
        Bitmap bmp;
        bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bmp);
        return bmp;
    }

    private static Mat bitmapToMat(Bitmap bmp) {
        Mat mat = new Mat();
        Utils.bitmapToMat(bmp, mat);
        return mat;
    }
}