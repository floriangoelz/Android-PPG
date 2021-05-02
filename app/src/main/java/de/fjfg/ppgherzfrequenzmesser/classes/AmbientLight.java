package de.fjfg.ppgherzfrequenzmesser.classes;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.widget.Toast;

import de.fjfg.ppgherzfrequenzmesser.MainActivity;

public class AmbientLight {

    private Sensor lightSensor;
    private SensorEventListener lightEventListener;
    private MainActivity context;
    private SensorManager sensorManager;
    private boolean started = false;

    public AmbientLight(MainActivity context, SensorManager sensorManager) {
        this.context = context;
        this.sensorManager = sensorManager;
        if (!hasLightSensor()) {
            // TODO Fehler werfen
        }
    }

    private boolean hasLightSensor() {
        return lightSensor == null;
    }

    public void startLightSensor() {
        if(!started) {
            registerLightSensor();
            started = true;
        }
    }

    public void stopLightSensor() {
        if(started) {
            sensorManager.unregisterListener(lightEventListener);
            started = false;
        }
    }

    private void registerLightSensor() {
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        lightEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                float value = sensorEvent.values[0];
                context.showLightResult(value);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
                // not needed
            }
        };
        sensorManager.registerListener(lightEventListener, lightSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }
}
