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

/**
 * Class to handle a measurement. All raw measuring data will be stored inside this class
 */
public class Measurement {
    // the offset in milliseconds after which the measuring will actually start
    static final int OFFSET = 2000;
    // the duration of the measure in milliseconds
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

    /**
     * Is used to start the measuring of the instance it is called on
     */
    public void startMeasuring(){
        startImageAnalysis();
    }

    /**
     * Is called after the needed data is collected in order to unbind
     * the camera provider and calculate the pulse data
     *
     * Sets the result inside the MainActivity after it was calculated
     */
    private void finishMeasuring(){
        cameraProvider.unbindAll();
        double result = calculateValues();
        context.showPulseResult(result);
    }

    /**
     * As the main function of this class, this is the function that is continuously called during
     * the process of collecting data.
     *
     * For each captured imaged the code inside the analyze(...) Block is called which first determines
     * how much time has passed since the measuring was started.
     *
     * If the time exceeds the set OFFSET
     * it starts converting every image and storing the image in a list of bitmaps while constantly
     * updating the progress bar
     *
     * After the MEASURE_TIME is exceeded, the collected the function calls finishMeasuring()
     * which closes the camera and starts calculating the raw data.
     */
    // TODO funktion ggf. aufräumen
    private void startImageAnalysis() {
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
                    bitmaps.add(yuvToBitmap(image.getImage()));
                } else if (difference > OFFSET) {
                    finishMeasuring();
                }
                image.close();
                double x = ((difference - OFFSET) / MEASURE_TIME) * 100;
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

    /**
     * Is used to convert a yuvImage that is provided by android into a bitmap
     * so it is possible to extract the RGB values of said image
     *
     * @param image the YUV-Image that shall be converted
     * @return a bitmap containing the converted YUV-Image
     */
    private Bitmap yuvToBitmap(Image image) {
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

    /**
     * Function to calculate the measured BPM using other functions.
     *
     * The steps per Image are as following:
     * - Iterating the left and right 10% of the image and extracting the redvalue
     * - Calculating the average redvalue of the image and storing it as an average
     *
     * After there is an average redvalue for each image, all values are calculated using
     * fourier-transformation
     *
     * @return the measured BPM after finishing all calculations
     */
    private double calculateValues() {
        List<Double> averages = new ArrayList<>();
        List<Integer> redvalues = new ArrayList<>();
        int width;
        int height;
        for (Bitmap map : bitmaps) {
            width = map.getWidth();
            height = map.getHeight();
            for (int i = 0; i < width; i++) {
                if (i <= width * 0.1 || i >= width * 0.9) {
                    for (int j = 0; j < height; j++) {
                        if (j <= height * 0.1 || j >= height * 0.9) {
                            int color = map.getPixel(i, j);
                            redvalues.add((color & 0xff0000) >> 16);
                        }
                    }
                }
            }
            averages.add(getMean(redvalues));
            redvalues.clear();
        }
        return  getHeartRateFourier(averages);
    }

    /**
     * Is used to analyse the averages by performing a fourier transform.
     * The heartrate is calculated by finding the maximum of the resulting frequency spectrum
     *
     * @param averages the averages of all measured red values per image
     * @return the calculated heartrate in bpm
     */
    private double getHeartRateFourier(List<Double> averages){
        List<Double> smoothValues = getSmoothValues(averages);
        DoubleFFT_1D fft = new DoubleFFT_1D(smoothValues.size());
        double[] values = new double[smoothValues.size() * 2];
        List<Double> realValues = new ArrayList<>();
        List<Double> imaginaryValues = new ArrayList<>();
        List<Double> absoluteValues = new ArrayList<>();

        //insert smooth values in double array for fourier transform
        for (int i = 0; i < smoothValues.size(); i++) {
            values[i] = smoothValues.get(i);
        }

        //execute fourier transform
        fft.realForward(values);

        //separate real and imaginary values of the result
        for(int i = 0; i < values.length; i++){
            if(i % 2 == 0){
                realValues.add(values[i]);
            }else{
                imaginaryValues.add(values[i]);
            }
        }

        //calculate the absolute value of each result
        for(int i = 0; i < realValues.size(); i++){
            double absoluteValue = Math.sqrt(realValues.get(i) * realValues.get(i) + imaginaryValues.get(i) * imaginaryValues.get(i));
            absoluteValues.add(absoluteValue);
        }

        //get maximums of frequency spectrum
        List<Integer> peaks = getPeaks(absoluteValues);


        for(int i = peaks.size() - 1; i >= 0; i--){
            //remove peaks below 0.5 Hz and above 5 Hz
            if(peaks.get(i) < 10 || peaks.get(i) > 75){
                peaks.remove(i);
            }
        }


        //find highest maximum
        double max = 0;
        double maxIndex = 0;
        for(int i = 0; i < peaks.size(); i++){
            if(absoluteValues.get(peaks.get(i)) > max){
                max = absoluteValues.get(peaks.get(i));
                maxIndex = peaks.get(i);
            }
        }

        //calculate heart rate
        return (maxIndex / 15) * 60;
    }

    /**
     * Function to smoothen the given redvalues
     *
     * To smoothen the resulting values, the average values
     * get calculated using the average between the value itself
     * and 6 values before the chosen value.
     *
     * This results in a returned list that has the size of
     * n-6 if n is the size of the given list.
     *
     * @param averages a list containing all average redvalues
     * @return a list of smoothened redvalues (size: n-6)
     */
    private List<Double> getSmoothValues(List<Double> averages) {
        List<Double> smooth = new ArrayList<>();
        double[] currentValues = new double[7];
        for (int i = 0; i < averages.size(); i++) {
            currentValues[i % 7] = averages.get(i);
            if (i >= 6) {
                smooth.add(getMean(currentValues));
            }
        }
        return smooth;
    }

    /**
     * Gets the average of all values in the array
     *
     * @param values an array containing all redvalues
     * @return the average of the given redvalues
     */
    private double getMean(double[] values) {
        double avg = 0;
        for (int i = 0; i < values.length; i++) {
            avg += values[i];
        }
        return avg / values.length;
    }

    /**
     * Gets the average of all values in the list
     *
     * @param list A list containing all redvalues
     * @return the average of the given redvalues
     */
    private double getMean(List<Integer> list) {
        double sum = 0;
        for (int i = 0; i < list.size(); i++) {
            sum += list.get(i);
        }
        return sum / list.size();
    }

    /**
     * Counts the maximum turning points of the collected redvalues
     *
     * @param values the redvalues that are used to figure out the maximum turning points
     * @return the indices of the list that are a maximum turning point
     */
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
