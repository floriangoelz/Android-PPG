package de.fjfg.ppgherzfrequenzmesser;

import android.annotation.SuppressLint;
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
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

public class CameraActivity extends AppCompatActivity {
    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private TextView textView;

    long startTime = 0;
    boolean printed = false;
    List<Double> redvalues = new ArrayList<>();
    List<Bitmap> bitmaps = new ArrayList<>();
    final long OFFSET = 5000;
    final long MEASURE_TIME = 10000;
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
                    calculateValuesLargeBorder();
                    calculateValuesFullImage();
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
