package com.example.arsudokusolver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.arsudokusolver.ml.HandwrittenDigits;
import com.example.arsudokusolver.ml.OcrModel;

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

public class SudokuDNS {
    private static final int HEIGHT = 630;
    private static final int WIDTH = 630;
    private static final int CONTOUR_SIZE = 1;
    private static final int INPUT_SIZE = 224;


    public static Bitmap solve(Context context, LinearLayout linearLayout, Bitmap image) {
        //Converting bitmap to image
        Mat img = bitmapToMat(image);

        //Gray Scale Image
        Mat gray = getMatCopy(img);
        Imgproc.cvtColor(gray, gray, Imgproc.COLOR_BGR2GRAY);

        //Blurring the Gray Image.
        Mat blur = new Mat();
        Imgproc.GaussianBlur(gray, blur, new Size(9, 9), 500);

        //Adaptive Thresholding
        Mat thresh = new Mat();
        Imgproc.adaptiveThreshold(blur, thresh, 255,
                Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 21, 10);
        Imgproc.resize(thresh, thresh, new Size(WIDTH, HEIGHT));


        //Finding the Contours of the thresh image.
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        //Sorting the Contours based on Contour area
        Collections.sort(contours, (o1, o2) -> (int) (Imgproc.contourArea(o2) - Imgproc.contourArea(o1)));

        //Taking only largest 20 contours
        contours = getTopContours(contours, CONTOUR_SIZE);

        //Applying contours to temp image
        Mat cnt_img = getMatCopy(img);


        //Traversing the Contours for Sudoku Detection
        for (MatOfPoint cnt : contours) {
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
                Mat warp_thresh_img = warpPerspective(thresh, sortedPoints);

                //Getting vertical and horizontal Lines.
//                Mat vhLines = getVHLines(img);


                List<Bitmap> listOfSubMats = getListOfSubMats(warp_thresh_img, img);
                try {
                    OcrModel model = OcrModel.newInstance(context);

                    HandwrittenDigits model2 = HandwrittenDigits.newInstance(context);


                    for (Bitmap subMat : listOfSubMats) {
                        Bitmap bmp = Bitmap.createScaledBitmap(subMat, INPUT_SIZE, INPUT_SIZE, false);
                        int predictedNumber = classifyImage(model2, bmp);

                        LinearLayout linearLayout1 = new LinearLayout(context);
                        linearLayout1.setOrientation(LinearLayout.HORIZONTAL);

                        ImageView imageView = new ImageView(context);
                        imageView.setImageBitmap(bmp);
                        imageView.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
                        linearLayout1.addView(imageView);

                        TextView textView = new TextView(context);
                        textView.setTextSize(22);
                        textView.setText(String.valueOf(predictedNumber));
                        linearLayout1.addView(textView);

                        linearLayout.addView(linearLayout1);
                    }
                    // Releases model resources if no longer used.
                    model.close();
                    model2.close();
                } catch (IOException e) {
                    // TODO Handle the exception
                }

                return matToBitmap(warp_thresh_img);
            }
        }
        return matToBitmap(cnt_img);
    }

    private static int classifyImage(HandwrittenDigits model, Bitmap bmp) {
        // Creates inputs for reference.
        TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 28, 28}, DataType.FLOAT32);
        ByteBuffer byteBuffer = getByteBuffer(bmp);
        inputFeature0.loadBuffer(byteBuffer);

        // Runs model inference and gets result.
        HandwrittenDigits.Outputs outputs = model.process(inputFeature0);
        TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

        //Confidence
        float[] confidence = outputFeature0.getFloatArray();
        int index = 0;
        for (int i = 0; i < confidence.length; i++) {
            if (confidence[index] < confidence[i]) {
                index = i;
            }
        }
        return index;
    }

    private static Mat getVHLines(Mat mat) {
        Mat flooded = new Mat();
        Point flood = new Point(1, 1);
        Scalar lowerDiff = new Scalar(10, 10, 10);
        Scalar upperDiff = new Scalar(10, 10, 10);
        Imgproc.floodFill(mat, flooded, flood, new Scalar(255, 255, 255), new Rect(), lowerDiff, upperDiff, 4);
        return flooded;

        //Contours
        //Finding the Contours of the thresh image.

//        List<MatOfPoint> contours = new ArrayList<>();
//        Mat hierarchy = new Mat();
//        Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
//        Log.d("MYAPP", String.valueOf(contours.size()));
////        Collections.sort(contours, (o1, o2) -> (int) (Imgproc.contourArea(o2) - Imgproc.contourArea(o1)));
//
////        contours = getTopContours(contours, 90);
//
//        Mat cnt_img = mat.clone();
//
//        Imgproc.drawContours(cnt_img, contours, -1, new Scalar(0, 255, 0, 255),
//                10, Imgproc.LINE_8);
//
//        return mat;

//
//                if (contours.size() == 1) {
//                    //Black screen zero to be predicted
//                    Mat cnt_img = Mat.zeros(new Size(crp.rows(), crp.width()), CvType.CV_8UC3);
//                    listOfSubMats.add(matToBitmap(cnt_img));
//                } else {
////                Sorting the Contours based on Contour area
//                    Collections.sort(contours, (o1, o2) -> (int) (Imgproc.contourArea(o2) - Imgproc.contourArea(o1)));
//
//                    Mat cnt_img = Mat.zeros(new Size(crp.rows(), crp.width()), CvType.CV_8UC3);
//
//                    List<MatOfPoint> temp = new ArrayList<>();
//                    temp.add(contours.get(0));
//                    Imgproc.drawContours(cnt_img, temp, -1, new Scalar(0, 255, 0, 255),
//                            -1, Imgproc.LINE_8);
//
//                    listOfSubMats.add(matToBitmap(cnt_img));
//                }

//        Mat blur = new Mat();
//        Imgproc.GaussianBlur(mat, blur, new Size(9, 9), 500);
//
//        Mat horizontalLines = new Mat();
//        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(11, 1));
//        Imgproc.erode(blur, horizontalLines, kernel);
//
//
//        Mat verticalLines = new Mat();
//        kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, 11));
//        Imgproc.erode(blur, verticalLines, kernel);
//
//        Mat diff = new Mat();
//        Core.subtract(blur, horizontalLines, diff);
//        Core.subtract(diff, verticalLines, diff);
//        return diff;
    }

    private static int classifyImage(OcrModel model, Bitmap bmp) {
        // Creates inputs for reference.
        TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);

        //Getting byteBuffer
        ByteBuffer byteBuffer = getByteBuffer(bmp);
        inputFeature0.loadBuffer(byteBuffer);

        // Runs model inference and gets result.
        OcrModel.Outputs outputs = model.process(inputFeature0);
        TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
        //Confidence
        float[] confidence = outputFeature0.getFloatArray();
        int index = 0;
        for (int i = 0; i < confidence.length; i++) {
            if (confidence[index] < confidence[i]) {
                index = i;
            }
        }
        return index;
    }

    private static ByteBuffer getByteBuffer(Bitmap bitmap) {
        bitmap = Bitmap.createScaledBitmap(bitmap, 28, 28, false);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        ByteBuffer mImgData = ByteBuffer
                .allocateDirect(4 * width * height);
        mImgData.order(ByteOrder.nativeOrder());
        int[] pixels = new int[width*height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int pixel : pixels) {
            float value = (float) Color.red(pixel);
            mImgData.putFloat(value);
        }
        return mImgData;

//        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
//        byteBuffer.order(ByteOrder.nativeOrder());
//
//        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
//        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
//        int pixel = 0;
//        for (int i = 0; i < INPUT_SIZE; i++) {
//            for (int j = 0; j < INPUT_SIZE; j++) {
//                int val = intValues[pixel++];
//                byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f));
//                byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255.f));
//                byteBuffer.putFloat((val & 0xFF) * (1.f / 255.f));
//            }
//        }
//        return byteBuffer;
    }

    @NonNull
    private static List<Bitmap> getListOfSubMats(Mat warp_thresh_img, Mat img) {
        List<Bitmap> listOfSubMats = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                int start_x = j * (WIDTH / 9);
                int start_y = i * (HEIGHT / 9);
                Mat sub = new Mat(warp_thresh_img, new Rect(start_x, start_y, WIDTH / 9, HEIGHT / 9));

                int cropFactor = 7;
                //Get ROI
                Mat crp = sub.submat(new Rect(cropFactor, cropFactor, sub.width() - cropFactor, sub.height() - cropFactor));

                Imgproc.resize(crp, crp, new Size(INPUT_SIZE, INPUT_SIZE));
                //Contours
                List<MatOfPoint> contours = new ArrayList<>();
                Mat hierarchy = new Mat();
                Imgproc.findContours(crp, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

                Mat mat = Mat.zeros(new Size(crp.rows(), crp.width()), CvType.CV_8UC3);
                for (MatOfPoint c :
                        contours) {
                    Rect rect = Imgproc.boundingRect(c);
                    if (rect.x < cropFactor || rect.y < cropFactor || rect.height < cropFactor || rect.width < cropFactor) {
                    } else {
                        mat = new Mat(crp, rect);
                        break;
                    }
                }
                Mat inv = new Mat();
                Core.bitwise_not(mat, inv);
                listOfSubMats.add(matToBitmap(inv));


                //Finding the Contours of the thresh image.

//                List<MatOfPoint> contours = new ArrayList<>();
//                Mat hierarchy = new Mat();
//                Imgproc.findContours(crp, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
//                Log.d("MYAPP", i + " : " + j + " = " + contours.size());
//
//                if (contours.size() == 1) {
//                    //Black screen zero to be predicted
//                    Mat cnt_img = Mat.zeros(new Size(crp.rows(), crp.width()), CvType.CV_8UC3);
//                    listOfSubMats.add(matToBitmap(cnt_img));
//                } else {
////                Sorting the Contours based on Contour area
//                    Collections.sort(contours, (o1, o2) -> (int) (Imgproc.contourArea(o2) - Imgproc.contourArea(o1)));
//
//                    Mat cnt_img = Mat.zeros(new Size(crp.rows(), crp.width()), CvType.CV_8UC3);
//
//                    List<MatOfPoint> temp = new ArrayList<>();
//                    temp.add(contours.get(0));
//                    Imgproc.drawContours(cnt_img, temp, -1, new Scalar(0, 255, 0, 255),
//                            -1, Imgproc.LINE_8);
//
//                    listOfSubMats.add(matToBitmap(cnt_img));
//                }


//                Mat inv = new Mat();
//                Core.bitwise_not(sub, inv);
//                Bitmap bmp = matToBitmap(inv);
//
//
//                listOfSubMats.add(bmp);
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
    private static List<MatOfPoint> getTopContours(List<MatOfPoint> contours, int top_number) {
        List<MatOfPoint> temp = new ArrayList<>();
        for (int i = 0; i < Math.min(top_number, contours.size()); i++) {
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

    private static Mat getMatCopy(Mat mat) {
        return mat.clone();
    }

}
