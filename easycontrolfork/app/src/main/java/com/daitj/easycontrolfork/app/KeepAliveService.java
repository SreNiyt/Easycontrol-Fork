package com.daitj.easycontrolfork.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

public class KeepAliveService extends Service {

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";

    public static final String EXTRA_HOST = "host";

    private static final String CHANNEL_ID = "easycontrol_connection";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {

            String host = intent.getStringExtra(EXTRA_HOST);

            if (host == null || host.isEmpty()) {
                host = "Host";
            }

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(com.daitj.easycontrolfork.app.R.mipmap.ic_launcher)
                    .setContentTitle("EasyControl")
                    .setContentText("Connected to " + host)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .build();

            startForeground(NOTIFICATION_ID, notification);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "EasyControl Connection",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription(
                    "Shows when EasyControl is connected to a host"
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
