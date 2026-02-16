package com.example.mobileagent.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.mobileagent.R
import com.example.mobileagent.ui.ResultViewerActivity

object TaskNotification {
    private const val CHANNEL_ID = "tasks"

    fun notifyDone(context: Context, taskId: String, resultText: String) {
        ensureChannel(context)

        val intent = Intent(context, ResultViewerActivity::class.java)
            .putExtra(ResultViewerActivity.EXTRA_TASK_ID, taskId)
            .putExtra(ResultViewerActivity.EXTRA_RESULT_TEXT, resultText)

        val pending = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Task completed")
            .setContentText(resultText.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(resultText))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
    }

    fun notifyFailed(context: Context, taskId: String, message: String) {
        ensureChannel(context)

        val intent = Intent(context, ResultViewerActivity::class.java)
            .putExtra(ResultViewerActivity.EXTRA_TASK_ID, taskId)
            .putExtra(ResultViewerActivity.EXTRA_RESULT_TEXT, message)

        val pending = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Task failed")
            .setContentText(message.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tasks",
            NotificationManager.IMPORTANCE_DEFAULT,
        )

        manager.createNotificationChannel(channel)
    }
}
