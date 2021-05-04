package de.fjfg.ppgherzfrequenzmesser.classes;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import de.fjfg.ppgherzfrequenzmesser.MainActivity;

/**
 * Class to retrieve data from the ambient light sensor
 */
public class AmbientLight {

    private Sensor lightSensor;
    private SensorEventListener lightEventListener;
    private MainActivity context;
    private SensorManager sensorManager;

    public AmbientLight(MainActivity context, SensorManager sensorManager) {
        this.context = context;
        this.sensorManager = sensorManager;
        this.lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (!hasLightSensor()) {
            context.noLightSensor();
        }
    }

    /**
     * Function to check whether the device has a built in lightsensor
     *
     * @return true if the device has a lightsensor, otherwise false
     */
    private boolean hasLightSensor() {
        return lightSensor != null;
    }

    /**
     * Starts the measuring of light via light sensor if the device has a light sensor
     */
    public void startLightSensor() {
        if (hasLightSensor()) {
            registerLightSensor();
        }
    }

    /**
     * Stops measuring light with the light sensor
     */
    public boolean stopLightSensor() {
        if (hasLightSensor()) {
            sensorManager.unregisterListener(lightEventListener);
            return true;
        }
        return false;
    }

    /**
     * Registers the light sensor using the sensorManager and updates the
     * TextView every time the data returned from the sensor changes
     */
    private void registerLightSensor() {
        lightEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                float value = sensorEvent.values[0];
                context.showLightResult(value);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
                // not needed since the accuracy can't change
            }
        };
        sensorManager.registerListener(lightEventListener, lightSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }
}
