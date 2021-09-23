package com.tagbug.ujs.autoclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.content.edit
import androidx.core.widget.addTextChangedListener
import com.tagbug.ujs.autoclock.MainActivity.Config.configFileName
import com.tagbug.ujs.autoclock.utils.ClockHelper
import com.tagbug.ujs.autoclock.utils.MobileInfoUtils
import com.tagbug.ujs.autoclock.utils.ObjectToBase64
import com.tagbug.ujs.autoclock.utils.TimerNotification
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.io.ObjectInputStream
import java.lang.Exception
import java.util.*

class MainActivity : AppCompatActivity() {

    /**
     * 配置项
     */
    object Config {
        const val configFileName = "MainActivity.Config"
    }

    private val infoToShowWithStart =
        listOf("为了保证定时任务效果，建议给予自启动权限，点击右上角有三个点导航到设置页", "通知推送效果可以手动调节，可以手动测试", "一天请不要测试太多次，否则OCR服务器可能会拒绝你的访问，第二天恢复")
    private var username: String? = null
    private var password: String? = null
    private var timingTime: Calendar? = null
    private var logHistory: Deque<String> = ArrayDeque(100)
    private var logHistoryIterator: Iterator<String>? = null

    private fun loadConfig() {
        getSharedPreferences(configFileName, MODE_PRIVATE).apply {
            username = getString("username", null)
            password = getString("password", null)
            getString("timingTime", null)?.apply {
                ObjectToBase64.decode(this)?.apply { timingTime = this as Calendar }
            }
            loadRecentLog()
        }
    }

    private fun saveConfig() {
        getSharedPreferences(configFileName, MODE_PRIVATE).edit {
            putString("username", username)
            putString("password", password)
            putString("timingTime", ObjectToBase64.encode(timingTime))
        }
    }

    private fun updateConfigToUI() {
        input_username.setText(username)
        input_password.setText(password)
        button_chooseAutoClockTime.text =
            timingTime?.let { String.format("%tT", timingTime) } ?: getText(R.string.timeChoiceButtonHint)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logTextView.movementMethod = ScrollingMovementMethod.getInstance()
        // 加载配置项
        loadConfig()
        // 更新UI
        updateConfigToUI()
        if (timingTime != null) {
            button_cancelAutoClock.visibility = View.VISIBLE
        }
        // 显示默认提示信息
        infoToShowWithStart.forEach { log("->提示：$it") }
        log("")
        // 创建通知渠道
        TimerNotification.addNotificationChannel(this)
        // 设置用户名输入监听器
        input_username.addTextChangedListener {
            username = input_username.text.toString()
        }
        // 设置密码输入监听器
        input_password.addTextChangedListener {
            password = input_password.text.toString()
        }
        // 设置选取定时运行时间按钮的回调
        button_chooseAutoClockTime.setOnClickListener {
            timingTime = timingTime ?: Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    timingTime!!.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    timingTime!!.set(Calendar.MINUTE, minute)
                    timingTime!!.set(Calendar.SECOND, 1)
                    log(String.format("你选择的定时运行时间为：%tT", timingTime))
                    updateConfigToUI()
                },
                timingTime!!.get(Calendar.HOUR_OF_DAY),
                timingTime!!.get(Calendar.MINUTE),
                true
            ).show()
        }
        // 设置打卡测试按钮的回调
        button_test.setOnClickListener {
            if (username == null || password == null) {
                log("Error: 请先填写用户名和密码")
                return@setOnClickListener
            }
            ClockHelper(username, password, this, logTextView).run()
        }
        // 设置保存&定时运行按钮的回调
        button_save.setOnClickListener {
            if (username == null || password == null) {
                log("Error: 请先填写用户名和密码")
                return@setOnClickListener
            }
            saveConfig()
            log("i: 配置已保存！")
            if (timingTime == null) {
                log("Error: 请先设置定时运行时间！")
                return@setOnClickListener
            }
            // 设置定时任务
            val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, AutoTimer::class.java)
            intent.setPackage(packageName)
            val alarmIntent = PendingIntent.getBroadcast(this, 0, intent, 0)
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
            button_cancelAutoClock.visibility = View.VISIBLE
            // 设置重启恢复
            val receiver = ComponentName(this, BootReceiver::class.java)
            packageManager.setComponentEnabledSetting(
                receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            log("i: 定时任务已设置！")
            log("i: 重启监听已开启，将自动恢复定时任务")
            log(String.format("下次运行时间：%tF %tT", timingTime, timingTime))
        }
        // 设置取消定时任务按钮的回调
        button_cancelAutoClock.setOnClickListener {
            // 取消定时任务
            val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, AutoTimer::class.java)
            val alarmIntent = PendingIntent.getBroadcast(this, 0, intent, 0)
            alarmMgr.cancel(alarmIntent)
            button_chooseAutoClockTime.setText(R.string.timeChoiceButtonHint)
            button_cancelAutoClock.visibility = View.INVISIBLE
            log("i: 定时任务已取消")
            // 取消重启监听
            val receiver = ComponentName(this, BootReceiver::class.java)
            packageManager.setComponentEnabledSetting(
                receiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            timingTime = null
            saveConfig()
            log("i: 重启监听已关闭")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveConfig()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val miu = MobileInfoUtils()
        when (item.itemId) {
            R.id.menu_DetailInterface -> miu.jumpDetailInterface(this)
            R.id.menu_StartInterface -> miu.jumpStartInterface(this)
            R.id.menu_ShowRecentDialog -> showRecentLog()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showRecentLog() {
        if (logHistory.isEmpty()) {
            log("--暂无记录--")
        } else {
            logHistoryIterator = logHistoryIterator ?: logHistory.iterator()
            logHistoryIterator?.apply {
                var i = 0
                while (i < 5 && this.hasNext()) {
                    log(this.next())
                    i++
                }
                if (this.hasNext()) {
                    log("重复操作再看5个...")
                } else {
                    log("到底了...")
                }
            }
        }
    }

    /**
     * (Convert From Java)
     */
    private fun loadRecentLog() {
        var objectIn: ObjectInputStream? = null
        var fileExist = false
        val files = fileList()
        val logName = "log.dat"
        for (file in files) {
            if (file == logName) {
                fileExist = true
                break
            }
        }
        if (fileExist) {
            try {
                val `in` = openFileInput(logName)
                objectIn = ObjectInputStream(`in`)
                logHistory = objectIn.readObject() as Deque<String>
                logHistoryIterator = logHistory.iterator()
            } catch (ioException: IOException) {
                logHistory = ArrayDeque()
                log("读取日志时错误：$ioException（是否未授予存储权限？）")
            } catch (e: Exception) {
                logHistory = ArrayDeque()
                log("读取日志时错误：$e")
            } finally {
                if (objectIn != null) {
                    try {
                        objectIn.close()
                    } catch (ioException: IOException) {
                        ioException.printStackTrace()
                    }
                }
            }
        } else {
            logHistory = ArrayDeque()
        }
    }

    /**
     * 打印即时日志并在必要时滚动
     */
    private val log: ((message: String) -> Unit) =
        { message: String ->
            logTextView.append("$message\n")
            logTextView.layout?.let {
                val scrollAmount: Int =
                    it.getLineTop(logTextView.lineCount) - logTextView.height
                logTextView.scrollTo(0, scrollAmount.coerceAtLeast(0))
            }
        }
}