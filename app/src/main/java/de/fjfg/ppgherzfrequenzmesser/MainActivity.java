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
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.icu.util.Measure;
import android.media.Image;
import android.os.Bundle;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;

import org.w3c.dom.Text;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private static final String[] CAMERA_PERMISSION = new String[]{Manifest.permission.CAMERA};
    private static final int CAMERA_REQUEST_CODE = 10;

    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    Dialog resultDialog;
    ProgressBar progressBar;
    boolean measuring = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button cameraPrompt = findViewById(R.id.startMeasurement);
        cameraPrompt.setOnClickListener(v -> onClick(cameraPrompt));

        previewView = findViewById(R.id.previewView);
        progressBar = findViewById(R.id.progressBar);
        resultDialog = new Dialog(this);
    }

    private void onClick(View v) {
        if (hasCameraPermission()) {
            enableCamera();
        } else {
            requestPermission();
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void enableCamera() {
        if(!measuring) {
            cameraProviderFuture = ProcessCameraProvider.getInstance(this);
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
        }else{
            //TODO Fehlermeldung Messung läuft gerade
        }
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(
                this,
                CAMERA_PERMISSION,
                CAMERA_REQUEST_CODE
        );
    }

    private void showDialog(double result) {
        resultDialog.setContentView(R.layout.result_popup);
        TextView resultText = resultDialog.findViewById(R.id.resultText);
        Button okButton = resultDialog.findViewById(R.id.okButton);
        resultText.setText((int)result + " BPM");

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                measuring = false;
                resultDialog.dismiss();
            }
        });
        resultDialog.setCanceledOnTouchOutside(false);
        resultDialog.show();

    }

    public void showProgress(int progress){
        progressBar.setProgress(progress);
    }

    public void showResult(double result) {
        showDialog(result);
    }
}