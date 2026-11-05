package com.example.opsc.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.opsc.R
import com.example.opsc.app.AppActivity
import com.example.opsc.ui.UiMessages
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** The user's notification choices, kept on the phone and in their profile. */
object NotificationPrefs {
    private const val PREFS = "battle_fitness_notifications"
    private const val KEY_ENABLED = "push_enabled"
    private const val KEY_PROMPT_DISMISSED = "permission_prompt_dismissed"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The Settings switch. On by default. */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

    /** True once the user chose Not now on the permission card. */
    fun promptDismissed(context: Context): Boolean = prefs(context).getBoolean(KEY_PROMPT_DISMISSED, false)

    fun dismissPrompt(context: Context) = prefs(context).edit().putBoolean(KEY_PROMPT_DISMISSED, true).apply()
}

/** The Android 13+ notification permission. */
object PushPermission {
    /** Android 13 and newer must ask before showing notifications. */
    fun needsRuntimePermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun isGranted(context: Context): Boolean =
        !needsRuntimePermission() || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

/** Creates the channels and shows notifications. */
object NotificationHelper {
    private const val TAG = "NotificationHelper"
    const val EXTRA_CLUB_ID = "clubId"
    const val EXTRA_TYPE = "type"

    /** Creates the three channels (safe to call every start). */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        listOf(
            Triple(PushChannel.FIXTURES, R.string.channel_fixtures, NotificationManager.IMPORTANCE_DEFAULT),
            Triple(PushChannel.REFEREE, R.string.channel_referee, NotificationManager.IMPORTANCE_HIGH),
            Triple(PushChannel.LEADERBOARD, R.string.channel_leaderboard, NotificationManager.IMPORTANCE_LOW)
        ).forEach { (channel, name, importance) ->
            manager.createNotificationChannel(NotificationChannel(channel.id, context.getString(name), importance))
        }
    }

    /** Shows a notification. Does nothing if notifications are off. Tapping it opens the club (if clubId is set) or the notification list. */
    fun show(context: Context, channel: PushChannel, title: String, body: String, clubId: String?, type: String?) {
        if (!NotificationPrefs.isEnabled(context) || !PushPermission.isGranted(context) ||
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            Log.i(TAG, "Notification suppressed (turned off or not permitted)")
            return
        }
        val intent = Intent(context, AppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            clubId?.let { putExtra(EXTRA_CLUB_ID, it) }
            type?.let { putExtra(EXTRA_TYPE, it) }
        }
        val pending = PendingIntent.getActivity(
            context, (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to notify", e)
        }
    }
}

/** Saves, refreshes and clears this phone's push token on the profile. */
object PushTokenManager {
    private const val TAG = "PushTokenManager"

    /** Saves the current token to the profile. Does nothing if signed out or notifications are off. */
    suspend fun register(context: Context) {
        if (!NotificationPrefs.isEnabled(context)) return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            save(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not get the push token", e)
        }
    }

    /** Called by Firebase when the token changes. */
    fun save(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(mapOf("fcmToken" to token, "fcmUpdatedAt" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "Could not save the push token", it) }
        Log.i(TAG, "Push token saved")
    }

    /** Deletes the token so this phone stops getting pushes. */
    suspend fun unregister() {
        try {
            FirebaseMessaging.getInstance().deleteToken().await()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("fcmToken", FieldValue.delete()).addOnFailureListener { Log.w(TAG, "Could not clear the token", it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete the push token", e)
        }
    }

    /** Debug helper: copies the token for a test push from the Firebase console. */
    fun copyTokenToClipboard(context: Context, root: View) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("FCM token", token))
            UiMessages.info(root, R.string.msg_token_copied)
        }.addOnFailureListener { UiMessages.error(root, R.string.error_generic) }
    }
}

/** Receives push messages. Messages from the console while the app is in the background are shown by Android. Messages while the app is open come here. */
class BattleMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.i("BattleMessaging", "New push token")
        PushTokenManager.save(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        val channel = PushChannel.fromId(message.data["channel"] ?: NotificationType.fromWire(message.data["type"])?.channel?.id)
        Log.i("BattleMessaging", "Push received: $title")
        NotificationHelper.show(this, channel, title, body, message.data[NotificationHelper.EXTRA_CLUB_ID], message.data[NotificationHelper.EXTRA_TYPE])
    }
}
