package de.fjfg.ppgherzfrequenzmesser;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import de.fjfg.ppgherzfrequenzmesser.classes.AmbientLight;
import de.fjfg.ppgherzfrequenzmesser.classes.Measurement;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.icu.util.Measure;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import org.w3c.dom.Text;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutionException;

/**
 * The main activity of the application. Used to handle all visuals that are shown on the main screen
 */
public class MainActivity extends AppCompatActivity {
    private static final String[] CAMERA_PERMISSION = new String[]{Manifest.permission.CAMERA};
    private static final int CAMERA_REQUEST_CODE = 10;
    private static final String LIGHT_MESSAGE = "Messung starten um Umgebungslicht anzuzeigen";

    private PreviewView previewView;

    private Dialog resultDialog;
    private ProgressBar progressBar;
    private AmbientLight ambientLight;
    private TextView light;
    private Toast runningToast;
    boolean measuring = false;


    private SensorManager sensorManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startMeasure = findViewById(R.id.startMeasurement);
        startMeasure.setOnClickListener(v -> onClick());

        previewView = findViewById(R.id.previewView);
        progressBar = findViewById(R.id.progressBar);
        light = findViewById(R.id.light);
        resultDialog = new Dialog(this);
        resultDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        runningToast = Toast.makeText(this, "Es läuft bereits eine Messung", Toast.LENGTH_SHORT);
        light.setText(LIGHT_MESSAGE);
    }

    /**
     * Gets called whenever the button to
     * start the pulse measurement is pressed
     * <p>
     * Accesses the devices camera if the permission
     * is granted. Otherwise asks the user for permission
     */
    private void onClick() {
        if (hasCameraPermission()) {
            enableCamera();
            ambientLight = new AmbientLight(this, sensorManager);
            ambientLight.startLightSensor();
        } else {
            requestPermission();
        }
    }

    /**
     * Is used to determine whether the application
     * is allowed to access the smartphones camera
     *
     * @return true if the app is allowed to use the
     * smartphone camera, otherwise false
     */
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks if a measurement is already running before starting to measure
     * <p>
     * If there is no active measurement:
     * Binds a cameraProvider, creates an Instance of measurement and uses it to
     * start measuring the pulse data. Also sets the measuring flag to true so
     * another measurement can not be started while there is one that is already
     * running.
     * <p>
     * If there is an active measurement:
     * Shows an error to the user that there is a currently active measurement that
     * needs to be finished before starting a new one
     */
    private void enableCamera() {
        if (!measuring) {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
            MainActivity context = this;
            cameraProviderFuture.addListener(new Runnable() {
                @Override
                public void run() {
                    try {
                        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                        Measurement measurement = new Measurement(cameraProvider, context, previewView);
                        measurement.startMeasuring();
                        measuring = true;
                    } catch (ExecutionException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }, ContextCompat.getMainExecutor(this));
        } else {
            runningToast.show();
        }
    }

    /**
     * Opens a standard android prompt that asks the user
     * for permission to use the camera of the device
     */
    private void requestPermission() {
        ActivityCompat.requestPermissions(
                this,
                CAMERA_PERMISSION,
                CAMERA_REQUEST_CODE
        );
    }

    /**
     * Is used to open the custom popup defined inside result_popup.xml
     * <p>
     * Before opening the popup:
     * - sets the measured bpm inside the popup so the actual result is shown
     * - adds an on click listener that resets the measuring flag (so a new
     * measure can be started)
     * - removes the option to dismiss the popup by clicking next to the popup
     *
     * @param result the result bpm that shall be shown inside the popup
     */
    private void showDialog(double result) {
        resultDialog.setContentView(R.layout.result_popup);
        TextView resultText = resultDialog.findViewById(R.id.resultText);
        Button okButton = resultDialog.findViewById(R.id.okButton);
        // casting to result to an int since a pulse with decimals wouldn't make sense
        resultText.setText((int) result + " BPM");

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                measuring = false;
                ambientLight.stopLightSensor();
                resultDialog.dismiss();
            }
        });
        resultDialog.setCanceledOnTouchOutside(false);
        resultDialog.show();

    }

    /**
     * Updates the status of the progress bar to a percentage (0-100)
     * Values outside the bounds are ignored by the progressBar
     *
     * @param progress the current progress that will be shown in the progress bar
     */
    public void showProgress(int progress) {
        progressBar.setProgress(progress);
    }

    public void showPulseResult(double result) {
        ambientLight.stopLightSensor();
        light.setText(LIGHT_MESSAGE);
        showDialog(result);
    }

    /**
     * Updates the TextView of the activity to show the measured light value
     *
     * @param result the current value that is delivered by the lightsensor (in lux)
     */
    public void showLightResult(double result) {
        // casting the result to an int since only X.0 lx values are returned by the sensor
        light.setText("Umgebungslicht: " + (int) result + " lx");
    }

    /**
     * Updates the TextView of the activity to show that the device got no light sensor
     */
    public void noLightSensor() {
        light.setText("Das Gerät besitzt keinen Lichtsensor.");
    }
}