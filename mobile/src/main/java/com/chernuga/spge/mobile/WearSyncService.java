package com.chernuga.spge.mobile;

import android.util.Log;

import com.chernuga.spge.shared.SyncContract;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Receives refresh requests from the watch.
 *
 * <p>Declared in the manifest so the Data Layer can start it even when the
 * phone app is not running. The actual fetch is done by the visible
 * {@link MainActivity}, because reading the page requires a live WebView; this
 * service only nudges the user to open the app.
 */
public class WearSyncService extends WearableListenerService {

    private static final String TAG = "SpgeWearSyncService";

    @Override
    public void onMessageReceived(MessageEvent event) {
        if (!SyncContract.PATH_REQUEST.equals(event.getPath())) return;

        Log.d(TAG, "watch requested a refresh");

        // A WebView cannot be driven from a background service, so surface a
        // notification rather than silently failing.
        android.app.NotificationManager nm =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel ch = new android.app.NotificationChannel(
                    "spge_sync", "Schedule sync",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }

        android.content.Intent open = new android.content.Intent(this, MainActivity.class);
        open.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);

        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                this, 0, open,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        | android.app.PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification n = new android.app.Notification.Builder(this, "spge_sync")
                .setContentTitle("Watch requested schedule")
                .setContentText("Tap to open and send the latest schedule")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        nm.notify(1001, n);
    }
}
