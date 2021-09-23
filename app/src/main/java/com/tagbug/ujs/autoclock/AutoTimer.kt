package com.tagbug.ujs.autoclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tagbug.ujs.autoclock.utils.ClockHelper
import com.tagbug.ujs.autoclock.utils.TimerNotification

/**
 * 定时运行广播接收器
 */
class AutoTimer : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.apply {
            tryToRun(this)
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