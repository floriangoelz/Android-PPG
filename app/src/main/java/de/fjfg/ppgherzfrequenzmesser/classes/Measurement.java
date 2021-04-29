package de.fjfg.ppgherzfrequenzmesser.classes;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;
import android.util.Size;
import android.view.View;

import org.jtransforms.fft.DoubleFFT_1D;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import de.fjfg.ppgherzfrequenzmesser.MainActivity;

public class Measurement {
    static final int OFFSET = 2000;
    static final int MEASURE_TIME = 15000;

    long startTime;
    List<Bitmap> bitmaps = new ArrayList<>();
    ProcessCameraProvider cameraProvider;
    MainActivity context;
    PreviewView previewView;

    public Measurement(@NonNull ProcessCameraProvider cameraProvider, MainActivity context, PreviewView previewView){
        this.cameraProvider = cameraProvider;
        this.context = context;
        this.previewView = previewView;

    }

    public void startMeasuring(){
        bindImageAnalysis();
    }

    private void finishMeasuring(){
        cameraProvider.unbindAll();
        Log.i("BLA", bitmaps.size() + " Bilder");
        double result = calculateValues();
        context.showResult(result);
    }

    private void bindImageAnalysis() {
        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder().setTargetResolution(new Size(240, 320))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context), new ImageAnalysis.Analyzer() {
            @SuppressLint("UnsafeExperimentalUsageError")
            @Override
            public void analyze(@NonNull ImageProxy image) {
                if (startTime == 0) {
                    startTime = System.currentTimeMillis();
                }
                double difference = System.currentTimeMillis() - startTime;
                if (difference >= OFFSET && difference <= OFFSET + MEASURE_TIME) {
                    bitmaps.add(toBitmap(image.getImage()));
                } else if (difference > OFFSET) {
                    finishMeasuring();
                }
                image.close();
                double x = ((difference - OFFSET) / MEASURE_TIME) * 100;
                Log.i("BLA","" + x);
                context.showProgress((int)x);
            }
        });
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.createSurfaceProvider());
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        Camera cam = cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector,
                imageAnalysis, preview);
        cam.getCameraControl().enableTorch(true);

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

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private double calculateValues() {
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

        return  getHeartRateFourier(averages);
    }

    private double getMean(List<Integer> list) {
        double sum = 0;
        for (int i = 0; i < list.size(); i++) {
            sum += list.get(i);
        }
        return sum / list.size();
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
}
