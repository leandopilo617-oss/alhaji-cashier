package iq.haji.cashier;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;

public class CallerScreeningService extends CallScreeningService {

    @Override
    public void onScreenCall(Call.Details details) {
        try {
            CallResponse response = new CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSilenceCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build();

            respondToCall(details, response);

            if (details.getCallDirection()
                    != Call.Details.DIRECTION_INCOMING) {
                return;
            }

            Uri handle = details.getHandle();

            if (handle == null ||
                    !"tel".equalsIgnoreCase(handle.getScheme())) {
                return;
            }

            String number = handle.getSchemeSpecificPart();

            if (number == null || number.trim().isEmpty()) {
                return;
            }

            getSharedPreferences("haji_cashier", MODE_PRIVATE)
                    .edit()
                    .putString("latest_number", number)
                    .apply();

            showNumberNotification(number);

        } catch (Exception ignored) {
        }
    }

    private void showNumberNotification(String number) {
        NotificationManager manager =
                (NotificationManager)
                        getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "haji_calls",
                    "أرقام المتصلين",
                    NotificationManager.IMPORTANCE_HIGH
            );

            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                10,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? new Notification.Builder(this, "haji_calls")
                        : new Notification.Builder(this);

        Notification notification = builder
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle("مطعم الحجي")
                .setContentText("رقم المتصل: " + number)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        manager.notify(1001, notification);
    }
}
