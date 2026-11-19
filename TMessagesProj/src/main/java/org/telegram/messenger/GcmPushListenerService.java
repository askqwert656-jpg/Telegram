/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class GcmPushListenerService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage message) {
        String from = message.getFrom();
        Map<String, String> data = message.getData();
        long time = message.getSentTime();
        String encryptedPayload = data.get("p");
        android.util.Log.i("PG_PUSH_DIAG", "FCM onMessageReceived keys=" + data.keySet() + " encryptedPayloadPresent=" + !android.text.TextUtils.isEmpty(encryptedPayload) + " payloadLength=" + (encryptedPayload == null ? 0 : encryptedPayload.length()));

        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("FCM received data: " + data + " from: " + from);
        }

        PushListenerController.processRemoteMessage(PushListenerController.PUSH_TYPE_FIREBASE, encryptedPayload, time);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        android.util.Log.i("PG_PUSH_DIAG", "FCM onNewToken tokenLength=" + (token == null ? 0 : token.length()));
        AndroidUtilities.runOnUIThread(() -> {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("Refreshed FCM token: " + token);
            }
            ApplicationLoader.postInitApplication();
            PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_FIREBASE, token);
        });
    }
}
