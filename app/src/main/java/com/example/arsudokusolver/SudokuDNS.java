package com.example.arsudokusolver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.arsudokusolver.ml.OldModel;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
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

public class SudokuDNS {
    private static final int HEIGHT = 630;
    private static final int WIDTH = 630;
    private static final int CONTOUR_SIZE = 1;
    private static final int INPUT_SIZE = 48;

    public static Bitmap solve(Context context, Mat image) {
        if (image == null) return null;
        if (!OpenCVLoader.initDebug()) {
            return null;
        } else {
            return solveHelper(context, image);
        }
    }

    public static Bitmap solve(Context context, Bitmap image) {
        if (image == null) return null;
        if (!OpenCVLoader.initDebug()) {
            return null;
        } else {
            return solveHelper(context, bitmapToMat(image));
        }
    }

    private static Bitmap solveHelper(Context context, Mat image) {

        //Image Cloning.
        Mat img = image.clone();

        //Resizing the image
        Imgproc.resize(img, img, new Size(WIDTH, HEIGHT));

        //Gray Scale Image
        Mat gray = new Mat();
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);

        Imgproc.GaussianBlur(gray, gray, new Size(3, 3), 3);

        //Adaptive Thresholding
        Mat thresh = new Mat();
        Imgproc.adaptiveThreshold(gray, thresh, 255,
                Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 21, 10);

        //Getting contours!
        List<MatOfPoint> contours;
        try {
            contours = getContours(thresh);
        } catch (Exception e) {
            return null;
        }
        if (contours.size() != 0) {
            //Traversing the Contours for Sudoku Detection
            MatOfPoint cnt = contours.get(0);
            MatOfPoint2f c2f = new MatOfPoint2f(cnt.toArray());
            double peri = Imgproc.arcLength(c2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true);

            Point[] points = approx.toArray();

            if (points.length == 4) {
                Point[] sortedPoints = getSortedPoints(cnt);
                if (sortedPoints[0] != null && sortedPoints[1] != null && sortedPoints[2] != null && sortedPoints[3] != null) {
                    //Getting the Warped Perspective Image.
                    Mat warp_img = warpPerspective(gray, sortedPoints);
                    List<Bitmap> listOfSubMats = getListOfSubMats(warp_img);
                    try {
                        List<Integer> listOfPredictedNumbers = new ArrayList<>();
                        OldModel model = OldModel.newInstance(context);
                        for (Bitmap bmp : listOfSubMats) {
                            int predictedNumber;
                            predictedNumber = classifyImage(model, bmp);
                            listOfPredictedNumbers.add(predictedNumber);
                        }
                        model.close();

                        List<List<Integer>> predictedNumbers = new ArrayList<>();

                        for (int i = 0; i < 9; i++) {
                            List<Integer> temp1 = new ArrayList<>();
                            for (int j = 0; j < 9; j++) {
                                temp1.add(listOfPredictedNumbers.get(i * 9 + j));
                            }
                            predictedNumbers.add(temp1);
                        }

                        if (SudokuSolver.solve(predictedNumbers)) {
                            Mat dst = getSolvedSudokuTextImg(context, listOfPredictedNumbers, predictedNumbers);
                            return matToBitmap(dst);
                        }
                    } catch (IOException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    @NonNull
    private static List<MatOfPoint> getContours(Mat thresh) {
        //Finding the Contours of the thresh image.
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);


        //Sorting the Contours based on Contour area
        Collections.sort(contours, (o1, o2) -> {
            if (Imgproc.contourArea(o1) >= Imgproc.contourArea(o2)) {
                return -1;
            } else if (Imgproc.contourArea(o1) == Imgproc.contourArea(o2)) {
                return 0;
            } else {
                return 1;
            }
        });

        //Taking only largest 20 contours
        contours = getTopContours(contours);
        return contours;
    }

    @NonNull
    private static Mat getSolvedSudokuTextImg(Context context, List<Integer> listOfPredictedNumbers, List<List<Integer>> predictedNumbers) {
        Bitmap sudoku_grid_bm = BitmapFactory.decodeResource(context.getResources(), R.drawable.sudoku_grid);
        Mat dst = bitmapToMat(sudoku_grid_bm);
        Imgproc.resize(dst, dst, new Size(WIDTH, HEIGHT));

        float fontScale = 1.6f;
        float shiftFactor = 10 * fontScale;
        Scalar color;
        int W = WIDTH / 9;
        int H = HEIGHT / 9;
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (listOfPredictedNumbers.get(i * 9 + j) == 0) {
                    color = new Scalar(0, 207, 34, 255);
                } else {
                    color = new Scalar(0, 0, 0, 255);
                }
                Imgproc.putText(dst, predictedNumbers.get(i).get(j).toString(),
                        new Point(j * W + W / 2.f - shiftFactor, i * H + H / 2.f + shiftFactor),
                        Imgproc.FONT_HERSHEY_COMPLEX
                        , fontScale, color, 2, Imgproc.LINE_AA);
            }
        }
        return dst;
    }

    private static int classifyImage(OldModel model, Bitmap bmp) {
        bmp = Bitmap.createScaledBitmap(bmp, INPUT_SIZE, INPUT_SIZE, false);
        // Creates inputs for reference.
        TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, INPUT_SIZE, INPUT_SIZE}, DataType.FLOAT32);
        inputFeature0.loadBuffer(getByteBuffer(bmp));

        // Runs model inference and gets result.
        OldModel.Outputs outputs = model.process(inputFeature0);
        TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

        float[] confidence = outputFeature0.getFloatArray();
        int index = 0;
        for (int i = 1; i < confidence.length; i++) {
            if (confidence[index] < confidence[i]) {
                index = i;
            }
        }
        Log.d("MYAPP", Arrays.toString(confidence));
        return confidence[index] < 0.8f ? 0 : index;
    }

    private static ByteBuffer getByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
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

                //Inverse
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
            if (data_x <= x && data_y <= y) {
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
