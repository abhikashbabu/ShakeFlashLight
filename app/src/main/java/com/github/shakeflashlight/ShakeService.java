package com.github.shakeflashlight;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class ShakeService extends Service implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometerSensor;
    private CameraManager cameraManager;
    private Vibrator vibrator;
    private String cameraId;
    private boolean isFlashlightOn = false;
    private int shakeCount = 0;
    private long lastShakeTime = 0;
    private static final int SHAKE_THRESHOLD = 15; // Adjust based on sensitivity
    private static final int SHAKE_COUNT_RESET_TIME_MS = 3000; // Time to reset shake count
    private static final int SHAKE_INTERVAL_MS = 500; // Interval to detect consecutive shakes

    @Override
    public void onCreate() {
        super.onCreate();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = cameraManager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        startForeground(1, getNotification());

        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private Notification getNotification() {
        String channelId = "ShakeFlashlightServiceChannel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Shake Flashlight Service Channel", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Shake Flashlight Service")
                .setContentText("Service is running")
                .setSmallIcon(R.drawable.ic_flashlight) // Replace with your app's icon
                .setContentIntent(pendingIntent)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            detectShake(event);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    private void detectShake(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        float acceleration = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;
        long currentTime = System.currentTimeMillis();

        if (acceleration > SHAKE_THRESHOLD) {
            if (currentTime - lastShakeTime < SHAKE_INTERVAL_MS) {
                shakeCount++;
                if (shakeCount >= 2) {
                    toggleFlashlight();
                    shakeCount = 0;
                }
            } else {
                shakeCount = 1;
            }
            lastShakeTime = currentTime;
        }

        // Reset shake count if no shake detected within SHAKE_COUNT_RESET_TIME_MS
        if (currentTime - lastShakeTime > SHAKE_COUNT_RESET_TIME_MS) {
            shakeCount = 0;
        }
    }

    private void toggleFlashlight() {
        try {
            if (isFlashlightOn) {
                cameraManager.setTorchMode(cameraId, false);
                isFlashlightOn = false;
                Toast.makeText(this, "Flashlight Off", Toast.LENGTH_SHORT).show();
            } else {
                cameraManager.setTorchMode(cameraId, true);
                isFlashlightOn = true;
                Toast.makeText(this, "Flashlight On", Toast.LENGTH_SHORT).show();
            }
            vibrator.vibrate(500); // Vibrate for 500 milliseconds
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
