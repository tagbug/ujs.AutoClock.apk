package com.tagbug.ujs.autoclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tagbug.ujs.autoclock.utils.ObjectToBase64
import com.tagbug.ujs.autoclock.utils.TimerNotification
import java.util.*

/**
 * 重启监听器
 */
class BootReceiver : BroadcastReceiver() {
    /**
     * 配置项
     */
    private val systemBootAction = "android.intent.action.BOOT_COMPLETED"

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.run {
            if (action == systemBootAction) {
                context?.apply {
                    var timingTime: Calendar? = null
                    getSharedPreferences(MainActivity.Config.configFileName, Context.MODE_PRIVATE).apply {
                        getString("timingTime", null)?.apply {
                            ObjectToBase64.decode(this)?.apply { timingTime = this as Calendar }
                        }
                    }
                    if (timingTime == null) {
                        TimerNotification.showSimpleNotification(context, "重启恢复失败：未设置定时运行时间")
                        return@apply
                    }
                    // 设置（恢复）定时任务
                    val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    Intent(this, AutoTimer::class.java).apply {
                        setPackage(packageName)
                        val alarmIntent = PendingIntent.getBroadcast(context, 0, this, 0)
                        alarmMgr.cancel(alarmIntent)
                        val ca = Calendar.getInstance()
                        timingTime?.apply {
                            set(ca.get(Calendar.YEAR), ca.get(Calendar.MONTH), ca.get(Calendar.DAY_OF_MONTH))
                            if (get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE) <= ca.get(Calendar.HOUR_OF_DAY) * 60 + ca.get(
                                    Calendar.MINUTE
                                )
                            ) {
                                set(Calendar.DAY_OF_MONTH, get(Calendar.DAY_OF_MONTH) + 1)
                            }
                            alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, alarmIntent)
                        }
                    }
                    TimerNotification.showLargeNotification(
                        context,
                        "重启恢复成功，长按显示下次运行时间：",
                        String.format("下次运行时间：%tF %tT", timingTime, timingTime)
                    )
                }
            }
        }
    }
}