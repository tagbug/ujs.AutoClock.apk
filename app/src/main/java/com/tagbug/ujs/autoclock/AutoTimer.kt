package com.tagbug.ujs.autoclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tagbug.ujs.autoclock.utils.ClockHelper
import com.tagbug.ujs.autoclock.utils.TimerNotification
import java.util.*

/**
 * 定时运行广播接收器
 */
class AutoTimer : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.apply {
            tryToRun(this)
            val alarmMgr = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val aintent = Intent(this, AutoTimer::class.java)
            aintent.setPackage(this.packageName)
            val alarmIntent = PendingIntent.getBroadcast(this, 0, aintent, 0)
            alarmMgr.cancel(alarmIntent)
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.DATE, 1)
            alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, alarmIntent)
        }
    }

    private fun tryToRun(context: Context) {
        var username: String? = null
        var password: String? = null
        context.getSharedPreferences(MainActivity.Config.configFileName, Context.MODE_PRIVATE).apply {
            username = getString("username", null)
            password = getString("password", null)
        }
        if (username == null || password == null) {
            TimerNotification.showSimpleNotification(context, "自动打卡失败：用户名或密码未设置")
        }
        ClockHelper(username, password, context).run()
    }
}