package de.fjfg.ppgherzfrequenzmesser;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.OrientationEventListener;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import org.jtransforms.fft.DoubleFFT_1D;

public class CameraActivity extends AppCompatActivity {
    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private TextView textView;

    long startTime = 0;
    boolean printed = false;
    List<Double> redvalues = new ArrayList<>();
    List<Bitmap> bitmaps = new ArrayList<>();
    final long OFFSET = 5000;
    final long MEASURE_TIME = 15000;
    final long WAIT_AFTER = 1000;
    //List<Integer> greenvalues = new ArrayList<>();
    //List<Integer> bluevalues = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        previewView = findViewById(R.id.previewView);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        textView = findViewById(R.id.orientation);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    bindImageAnalysis(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(this));

    }

    private void bindImageAnalysis(@NonNull ProcessCameraProvider cameraProvider) {
        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder().setTargetResolution(new Size(240, 320))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new ImageAnalysis.Analyzer() {
            @SuppressLint("UnsafeExperimentalUsageError")
            @Override
            public void analyze(@NonNull ImageProxy image) {
                if (startTime == 0) {
                    startTime = System.currentTimeMillis();
                }
                long difference = System.currentTimeMillis() - startTime;
                if (difference >= OFFSET && difference <= OFFSET + MEASURE_TIME) {
                    bitmaps.add(toBitmap(image.getImage()));
                } else if (difference > OFFSET + WAIT_AFTER + 1000 && !printed) {
                    printed = true;
                    calculateValuesSmallBorder();
                    //calculateValuesMiddleFrame();
                    calculateValuesLargeBorder();
                    //calculateValuesFullImage();
                    //calculateValuesOnePixel();
                }
                image.close();
//                @SuppressLint("UnsafeExperimentalUsageError") final Bitmap map = toBitmap(image.getImage());
//                image.close();
//                List<Integer> values = new ArrayList<>();
//                final double[] mean = new double[1];
//
//
//                        for(int i = 0; i < map.getWidth(); i++) {
//                            for(int j = 0; j < map.getHeight(); j++){
//                                int color = map.getPixel(i, j);
//                                values.add((color & 0xff0000) >> 16);
//                            }
//                        }
//                        //int G = (color & 0x00ff00) >> 8;
//                        //int B = (color & 0x0000ff) >> 0;
//                        mean[0] = getMean(values);
//
//
//                if(startTime == 0) {
//                    startTime = System.currentTimeMillis();
//                }
//                long difference = System.currentTimeMillis() - startTime;
//                if(difference > 5000 && difference < 15000) {
//                    redvalues.add(mean[0]);
//                    //greenvalues.add(G);
//                    //bluevalues.add(B);
//                }
//                if(difference > 16000 && !printed) {
//                    printed = true;
//                    Log.i("RESULT", "Red: " + redvalues);
//                    //System.out.println("Green: " + greenvalues);
//                    //System.out.println("Blue: " + bluevalues);
//                }
//                textView.setText(Double.toString(mean[0]));
            }
        });
        OrientationEventListener orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                // textView.setText(Integer.toString(orientation));
            }
        };
        orientationEventListener.enable();
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.createSurfaceProvider());
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        Camera cam = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector,
                imageAnalysis, preview);
        cam.getCameraControl().enableTorch(true);
    }

    private void showPopup(String title, String text) {
        AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(this);
        dlgAlert.setMessage(text);
        dlgAlert.setTitle(title);
        dlgAlert.setPositiveButton("OK", null);
        dlgAlert.setCancelable(true);
        dlgAlert.create().show();
    }

    private void testFourier(List<Double> averages) {
        System.out.println("Listsize: " + averages.size());
        System.out.println("Starting fourier...");
        DoubleFFT_1D fft = new DoubleFFT_1D(averages.size());
        double[] values = new double[averages.size() * 2];
        for (int i = 0; i < averages.size(); i++) {
            values[i] = averages.get(i);
        }
        fft.realForward(values);
        String arrayStr = "";
        for (int i = 0; i < values.length; i++) {
            arrayStr += ", " + values[i];
        }
        arrayStr = arrayStr.substring(2);
        System.out.println(arrayStr);
    }

    private double getHeartRateCountPeaks(List<Double> averages) {
        List<Double> smoothValues = getSmoothValues(averages);
        List<Integer> peaks = getPeaks(smoothValues);
        return peaks.size() * 0.5 * (60 / (MEASURE_TIME / 1000));
    }

    private double getHeartRateDistance(List<Double> averages) {
        double sampling_rate = averages.size() / (MEASURE_TIME / 1000);
        double frequency;
        List<Double> smoothValues = getSmoothValues(averages);
        List<Integer> peaks = getPeaks(smoothValues);
        List<Integer> distances = getDistances(peaks);
        frequency = 1 / (getMedian(distances) / sampling_rate);
        return frequency * 0.5 * 60;
    }

    private double getHeartRateFourier(List<Double> averages){
        List<Double> smoothValues = getSmoothValues(averages);
        DoubleFFT_1D fft = new DoubleFFT_1D(smoothValues.size());
        double[] values = new double[smoothValues.size() * 2];
        List<Double> realValues = new ArrayList<>();
        List<Double> imaginaryValues = new ArrayList<>();
        List<Double> absoluteValues = new ArrayList<>();
        for (int i = 0; i < smoothValues.size(); i++) {
            values[i] = smoothValues.get(i);
        }
        fft.realForward(values);

        for(int i = 0; i < values.length; i++){
            if(i % 2 == 0){
                realValues.add(values[i]);
            }else{
                imaginaryValues.add(values[i]);
            }
        }

        for(int i = 0; i < realValues.size(); i++){
            double absoluteValue = Math.sqrt(realValues.get(i) * realValues.get(i) + imaginaryValues.get(i) * imaginaryValues.get(i));
            absoluteValues.add(absoluteValue);
        }

        String arrayStr = "";
        for (int i = 0; i < absoluteValues.size(); i++) {
            arrayStr += ", " + absoluteValues.get(i);
        }
        arrayStr = arrayStr.substring(2);
        Log.i("FOURIER", arrayStr);

        List<Integer> peaks = getPeaks(absoluteValues);



        for(int i = peaks.size() - 1; i >= 0; i--){
            //remove peaks below 0.5 Hz and above 5 Hz
            if(peaks.get(i) < 10 || peaks.get(i) > 75){
                peaks.remove(i);
            }
        }

        String log = "";

        double max = 0;
        double maxIndex = 0;
        for(int i = 0; i < peaks.size(); i++){
            log += " " + peaks.get(i);
            if(absoluteValues.get(peaks.get(i)) > max){
                max = absoluteValues.get(peaks.get(i));
                maxIndex = peaks.get(i);
            }
        }

        Log.i("FOURIER", "peaks: " + log + "\n" + maxIndex);
        return (maxIndex / 15) * 60;
    }

    private List<Double> getSmoothValues(List<Double> averages) {
        List<Double> smooth = new ArrayList<>();
        double[] currentValues = new double[7];
        for (int i = 0; i < averages.size(); i++) {
            currentValues[i % 7] = averages.get(i);
            if (i >= 6) {
                smooth.add(getMean(currentValues));
            }
        }
        System.out.println("Smooth values: " + smooth);
        return smooth;
    }

    private double getMean(double[] values) {
        double avg = 0;
        for (int i = 0; i < values.length; i++) {
            avg += values[i];
        }
        return avg / values.length;
    }

    private List<Integer> getPeaks(List<Double> values) {
        List<Integer> peakIndices = new ArrayList<>();
        for (int i = 1; i < values.size() - 1; i++) {
            if (values.get(i - 1) < values.get(i) && values.get(i) > values.get(i + 1)) {
                peakIndices.add(i);
            }
        }
        System.out.println("Peaks: " + peakIndices);
        return peakIndices;
    }

    private List<Integer> getDistances(List<Integer> peakIndices) {
        List<Integer> distances = new ArrayList<>();
        for (int i = 0; i < peakIndices.size() - 1; i++) {
            distances.add(peakIndices.get(i + 1) - peakIndices.get(i));
        }
        System.out.println("Distances: " + distances);
        return distances;
    }

    private double getMedian(List<Integer> peakDistances) {
        int size = peakDistances.size();
        Collections.sort(peakDistances);
        if (peakDistances.size() % 2 == 0) {
            return (peakDistances.get(size / 2) + peakDistances.get(size / 2 - 1)) / 2;
        }
        System.out.println("Median: " + peakDistances.get(size / 2));
        return peakDistances.get(size / 2);
    }

    private void calculateValuesSmallBorder() {
        Log.i("RESULT", "Calculating Small Border...");
        List<Double> averages = new ArrayList<>();
        List<Integer> redvalues = new ArrayList<>();
        int width;
        int height;
        for (Bitmap map : bitmaps) {
            width = map.getWidth();
            height = map.getHeight();
            for (int i = 0; i < width; i++) {
                if (i < width * 0.05 || i > width * 0.95) {
                    for (int j = 0; j < height; j++) {
                        if (j < height * 0.05 || j > height * 0.95) {
                            int color = map.getPixel(i, j);
                            redvalues.add((color & 0xff0000) >> 16);
                        }
                    }
                }
            }
            averages.add(getMean(redvalues));
            redvalues.clear();
        }
        DecimalFormat df = new DecimalFormat("###.###");
        String averageStr = "";
        for (int i = 0; i < averages.size(); i++) {
            averageStr += ", " + df.format(averages.get(i));
        }
        averageStr = averageStr.substring(2);
        averageStr = "Redvalues small: [" + averageStr + "]";
        averageStr = averageStr.replace(", ", "%%");
        averageStr = averageStr.replace(",", ".");
        averageStr = averageStr.replace("%%", ", ");
        Log.i("RESULT", averageStr);

        double distanceCalc = getHeartRateDistance(averages);
        double countCalc = getHeartRateCountPeaks(averages);
        double fourierCalc = getHeartRateFourier(averages);

        String popuptext = "Small border: \n" + "BPM via distance: " + distanceCalc + "\n" + "BPM via count: " + countCalc + "\n" + "BPM via Fourier: " + fourierCalc;
        showPopup("Pulsfeedback", popuptext);
    }

    private void calculateValuesMiddleFrame() {
        Log.i("RESULT", "Calculating Middle-Frame...");
        List<Double> averages = new ArrayList<>();
        List<Integer> redvalues = new ArrayList<>();
        int width;
        int height;
        for (Bitmap map : bitmaps) {
            width = map.getWidth();
            height = map.getHeight();
            for (int i = 0; i < width; i++) {
                if (i > width * 0.45 && i < width * 0.55) {
                    for (int j = 0; j < height; j++) {
                        if (j > height * 0.45 && j < height * 0.55) {
                            int color = map.getPixel(i, j);
                            redvalues.add((color & 0xff0000) >> 16);
                        }
                    }
                }
            }
            averages.add(getMean(redvalues));
            redvalues.clear();
        }
        DecimalFormat df = new DecimalFormat("###.###");
        String averageStr = "";
        for (int i = 0; i < averages.size(); i++) {
            averageStr += ", " + df.format(averages.get(i));
        }
        averageStr = averageStr.substring(2);
        averageStr = "Redvalues middleframe: [" + averageStr + "]";
        averageStr = averageStr.replace(", ", "%%");
        averageStr = averageStr.replace(",", ".");
        averageStr = averageStr.replace("%%", ", ");
        Log.i("RESULT", averageStr);
    }

    private void calculateValuesLargeBorder() {
        Log.i("RESULT", "Calculating Large Border...");
        List<Double> averages = new ArrayList<>();
        List<Integer> redvalues = new ArrayList<>();
        int width;
        int height;
        for (Bitmap map : bitmaps) {
            width = map.getWidth();
            height = map.getHeight();
            for (int i = 0; i < width; i++) {
                if (i < width * 0.1 || i > width * 0.9) {
                    for (int j = 0; j < height; j++) {
                        if (j < height * 0.1 || j > height * 0.9) {
                            int color = map.getPixel(i, j);
                            redvalues.add((color & 0xff0000) >> 16);
                        }
                    }
                }
            }
            averages.add(getMean(redvalues));
            redvalues.clear();
        }
        DecimalFormat df = new DecimalFormat("###.###");
        String averageStr = "";
        for (int i = 0; i < averages.size(); i++) {
            averageStr += ", " + df.format(averages.get(i));
        }
        averageStr = averageStr.substring(2);
        averageStr = "Redvalues large: [" + averageStr + "]";
        averageStr = averageStr.replace(", ", "%%");
        averageStr = averageStr.replace(",", ".");
        averageStr = averageStr.replace("%%", ", ");
        Log.i("RESULT", averageStr);

        double distanceCalc = getHeartRateDistance(averages);
        double countCalc = getHeartRateCountPeaks(averages);
        double fourierCalc = getHeartRateFourier(averages);

        String popuptext = "Large border: \n" + "BPM via distance: " + distanceCalc + "\n" + "BPM via count: " + countCalc + "\n" + "BPM via Fourier: " + fourierCalc;
        showPopup("Pulsfeedback", popuptext);
    }

    private void calculateValuesFullImage() {
        Log.i("RESULT", "Calculating Full Images...");
        List<Double> averages = new ArrayList<>();
        List<Integer> redvalues = new ArrayList<>();
        int width;
        int height;
        for (Bitmap map : bitmaps) {
            width = map.getWidth();
            height = map.getHeight();
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    int color = map.getPixel(i, j);
                    redvalues.add((color & 0xff0000) >> 16);
                }
            }
            averages.add(getMean(redvalues));
            redvalues.clear();
        }
        DecimalFormat df = new DecimalFormat("###.###");
        String averageStr = "";
        for (int i = 0; i < averages.size(); i++) {
            averageStr += ", " + df.format(averages.get(i));
        }
        averageStr = averageStr.substring(2);
        averageStr = "Redvalues full: [" + averageStr + "]";
        averageStr = averageStr.replace(", ", "%%");
        averageStr = averageStr.replace(",", ".");
        averageStr = averageStr.replace("%%", ", ");
        Log.i("RESULT", averageStr);
    }

    private void calculateValuesOnePixel() {
        Log.i("RESULT", "Calculating One Pixel...");
        List<Integer> values = new ArrayList<>();
        int x, y;
        for (Bitmap map : bitmaps) {
            x = map.getWidth() / 2;
            y = map.getHeight() / 2;
            int color = map.getPixel(x, y);
            values.add((color & 0xff0000) >> 16);
        }
        DecimalFormat df = new DecimalFormat("###.###");
        String averageStr = "";
        for (int i = 0; i < values.size(); i++) {
            averageStr += ", " + df.format(values.get(i));
        }
        averageStr = averageStr.substring(2);
        averageStr = "Redvalues One Pixel: [" + averageStr + "]";
        averageStr = averageStr.replace(", ", "%%");
        averageStr = averageStr.replace(",", ".");
        averageStr = averageStr.replace("%%", ", ");
        Log.i("RESULT", averageStr);
    }

    private Bitmap toBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        //Log.i("TEST", "width: " + image.getWidth() + " height: " + image.getHeight());

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private double getMean(List<Integer> list) {
        double sum = 0;
        for (int i = 0; i < list.size(); i++) {
            sum += list.get(i);
        }
        return sum / list.size();
    }
}
