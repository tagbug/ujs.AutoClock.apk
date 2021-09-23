package com.tagbug.ujs.autoclock.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tagbug.ujs.autoclock.MainActivity
import com.tagbug.ujs.autoclock.R

/**
 * 定时运行的通知推送工具
 */
object TimerNotification {
    /**
     * 配置项
     */
    private const val channelId = "TimerNotificationChannel"
    private const val channelName = "AutoTimer"
    private const val channelDescription = "自动打卡的通知"
    private const val channelImportance = NotificationManager.IMPORTANCE_HIGH
    private const val defaultNotificationId = 1

    /**
     * 添加默认通知渠道
     */
    fun addNotificationChannel(context: Context) {
        // 创建通知渠道
        NotificationChannel(channelId, channelName, channelImportance).apply {
            description = channelDescription
            // 显示通知指示灯
            enableLights(true)
            // 通知时振动
            enableVibration(true)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(this)
        }
    }

    /**
     * 生成一个PendingIntent用于启动指定Activity
     */
    fun createPendingIntent(context: Context, target: Class<*>): PendingIntent {
        val intent = Intent(context, target).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(context, 0, intent, 0)
    }

    /**
     * 推送简单文本通知
     */
    fun showSimpleNotification(context: Context, message: String) {
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("江苏大学自动健康打卡")
            .setContentText(message)
            .setContentIntent(createPendingIntent(context, MainActivity::class.java))
            .build()
            .apply {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(defaultNotificationId, this)
            }
    }

    /**
     * 推送大文本通知
     */
    fun showLargeNotification(context: Context, messageTitle: String, message: String) {
        NotificationCompat.Builder(context, channelId).apply {
            setSmallIcon(R.mipmap.ic_launcher)
            setContentTitle("江苏大学自动健康打卡")
            setContentText(messageTitle)
            if (message.isNotEmpty()) {
                setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message)
                )
            }
            setContentIntent(createPendingIntent(context, MainActivity::class.java))
            build().apply {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(defaultNotificationId, this)
            }
        }
    }
}