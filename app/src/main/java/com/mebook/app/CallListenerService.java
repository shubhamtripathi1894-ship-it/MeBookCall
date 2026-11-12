package com.mebook.app;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
public class CallListenerService extends Service {
    private static final String TAG = "MeBookCalls";
    private static final String CHANNEL = "mebook_calls";
    private ListenerRegistration registration;
    private String activeUid = "";
    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(71, buildOngoingNotification());
        initializeFirebase();
    }
    private void initializeFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApiKey(getString(R.string.firebase_api_key))
                    .setApplicationId(getString(R.string.firebase_app_id))
                    .setProjectId(getString(R.string.firebase_project_id))
                    .setGcmSenderId(getString(R.string.firebase_sender_id))
                    .build();
                FirebaseApp.initializeApp(this, options);
            }
            startListeningForUid();
        } catch (Exception e) { Log.e(TAG, "Firebase setup failed", e); }
    }
    private void startListeningForUid() {
        String uid = getSharedPreferences("mebook", MODE_PRIVATE).getString("uid", "");
        if (uid == null || uid.isEmpty() || uid.equals(activeUid)) return;
        if (registration != null) registration.remove();
        activeUid = uid;
        registration = FirebaseFirestore.getInstance()
            .collection("calls")
            .whereEqualTo("to", uid)
            .whereEqualTo("status", "ringing")
            .addSnapshotListener((snapshot, error) -> {
                if (error != null) return;
                if (snapshot == null) return;
                for (DocumentChange change : snapshot.getDocumentChanges()) {
                    if (change.getType() == DocumentChange.Type.ADDED) {
                        String caller = change.getDocument().getString("callerName");
                        if (caller == null || caller.isEmpty()) caller = "MeBook user";
                        showIncomingCall(caller, change.getDocument().getId());
                    }
                }
            });
    }
    private void showIncomingCall(String caller, String callId) {
        Intent call = new Intent(this, CallActivity.class);
        call.putExtra("caller_name", caller);
        call.putExtra("call_id", callId);
        call.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreen = PendingIntent.getActivity(this, callId.hashCode(), call, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Incoming MeBook call")
            .setContentText(caller + " is calling")
            .setCategory(Notification.CATEGORY_CALL)
            .setPriority(Notification.PRIORITY_MAX)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setAutoCancel(true)
            .build();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(callId.hashCode(), n);
    }
    private Notification buildOngoingNotification() {
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("MeBook call listener")
            .setContentText("Listening for calls while this service is running")
            .setOngoing(true)
            .build();
    }
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "MeBook calls", NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { startListeningForUid(); return START_STICKY; }
    @Override public void onDestroy() { if (registration != null) registration.remove(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
