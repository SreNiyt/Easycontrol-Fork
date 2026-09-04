package com.daitj.easycontrolfork.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class KeepAliveService extends Service {

    public static final String ACTION_START =
            "com.daitj.easycontrolfork.action.START";

    public static final String ACTION_STOP =
            "com.daitj.easycontrolfork.action.STOP";

    public static final String EXTRA_HOST =
            "com.daitj.easycontrolfork.extra.HOST";

    private static final String CHANNEL_ID =
            "easycontrol_connection";

    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(
            @Nullable Intent intent,
            int flags,
            int startId
    ) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_START.equals(action)) {
            String host = intent.getStringExtra(EXTRA_HOST);

            if (host == null || host.trim().isEmpty()) {
                host = "Host";
            }

            Notification notification =
                    new NotificationCompat.Builder(this, CHANNEL_ID)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("EasyControl")
                            .setContentText("Connected to " + host)
                            .setOngoing(true)
                            .setOnlyAlertOnce(true)
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                            .setCategory(NotificationCompat.CATEGORY_SERVICE)
                            .build();

            startForeground(NOTIFICATION_ID, notification);

            return START_NOT_STICKY;
        }

        if (ACTION_STOP.equals(action)) {
            stopForeground(true);
            stopSelf();

            return START_NOT_STICKY;
        }

        // Unknown action
        stopSelf();
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

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

        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
