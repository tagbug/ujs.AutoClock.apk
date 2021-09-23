package com.tagbug.ujs.autoclock.utils;

import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.util.*;

public class MobileInfoUtils {

    //获取手机类型

    private static String getMobileType() {

        return Build.MANUFACTURER;

    }


    //跳转至授权页面

    public void jumpStartInterface(Context context) {

        Intent intent = new Intent();

        try {

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Log.e("HLQ_Struggle", "******************当前手机型号为：" + getMobileType());

            ComponentName componentName = null;

            switch (getMobileType()) {
                case "Xiaomi":  // 红米Note4测试通过

                    componentName = new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity");


                    break;
                case "Letv":  // 乐视2测试通过

                    intent.setAction("com.letv.android.permissionautoboot");

                    break;
                case "samsung":  // 三星Note5测试通过

                    //componentName = new ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.AutoRunActivity");

                    //componentName = ComponentName.unflattenFromString("com.samsung.android.sm/.ui.ram.RamActivity");// Permission Denial not exported from uid 1000，不允许被其他程序调用

                    componentName = ComponentName.unflattenFromString("com.samsung.android.sm/.app.dashboard.SmartManagerDashBoardActivity");

                    break;
                case "HUAWEI":  // 华为测试通过

                    //componentName = new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity");//锁屏清理

                    componentName = ComponentName.unflattenFromString("com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity");//跳自启动管理


                    //SettingOverlayView.show(context);

                    break;
                case "vivo":  // VIVO测试通过

                    componentName = ComponentName.unflattenFromString("com.iqoo.secure/.safeguard.PurviewTabActivity");

                    break;
                case "Meizu":  //万恶的魅族

                    //componentName = ComponentName.unflattenFromString("com.meizu.safe/.permission.PermissionMainActivity");//跳转到手机管家

                    componentName = ComponentName.unflattenFromString("com.meizu.safe/.permission.SmartBGActivity");//跳转到后台管理页面


                    break;
                case "OPPO":  // OPPO R8205测试通过

                    componentName = ComponentName.unflattenFromString("com.oppo.safe/.permission.startup.StartupAppListActivity");

                    break;
                case "ulong":  // 360手机 未测试

                    componentName = new ComponentName("com.yulong.android.coolsafe", ".ui.activity.autorun.AutoRunListActivity");

                    break;
                default:

                    // 将用户引导到系统设置页面

                    if (Build.VERSION.SDK_INT >= 9) {

                        Log.e("HLQ_Struggle", "APPLICATION_DETAILS_SETTINGS");

                        intent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");

                        intent.setData(Uri.fromParts("package", context.getPackageName(), null));

                    } else if (Build.VERSION.SDK_INT <= 8) {

                        intent.setAction(Intent.ACTION_VIEW);

                        intent.setClassName("com.android.settings", "com.android.settings.InstalledAppDetails");

                        intent.putExtra("com.android.settings.ApplicationPkgName", context.getPackageName());

                    }

                    break;
            }

            intent.setComponent(componentName);

            context.startActivity(intent);

        } catch (Exception e) {//抛出异常就直接打开设置页面

            Log.e("HLQ_Struggle", e.getLocalizedMessage());

            intent = new Intent(Settings.ACTION_SETTINGS);

            context.startActivity(intent);

        }

    }

    public void jumpDetailInterface(Context context) {
        Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        }
    }

}
