package com.tagbug.ujs.autoclock.utils;

import android.annotation.*;
import android.content.*;
import android.os.*;
import android.util.*;
import android.widget.*;

import androidx.annotation.*;

import com.tagbug.ujs.autoclock.okhttp.CookieManager;

import org.jetbrains.annotations.*;
import org.json.*;
import org.jsoup.*;
import org.jsoup.Connection;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

import java.io.*;
import java.net.*;
import java.util.Base64;
import java.util.*;

import okhttp3.*;

/**
 * 打卡器核心逻辑，日后采用Kotlin重写
 */
public class ClockHelper {
    private static final String serviceURL = "http://yun.ujs.edu.cn/xxhgl/yqsb/index";
    private static final String LoginGetUrl = "https://pass.ujs.edu.cn/cas/login";
    private final Mode mode;
    private final Context context;
    private final String username;
    private final String password;
    private final String ocrUsername;
    private final String ocrPassword;
    private ArrayDeque<String> recentLog;
    private ObjectOutputStream objectOut;
    private TextView logText;
    private String AESpassword;
    private int retryCount;
    private String _pwdDefaultEncryptSalt;
    private String captchaBase64;
    private String captcha;
    private String OCR_uuid;
    private String OCR_token;
    private String job_token;
    private String OCR_jobId;
    private HashMap<String, String> postForm;
    private HashMap<String, String> HealthForm;
    private OkHttpClient client;

    public ClockHelper(String username, String password, String ocrUsername, String ocrPassword, Context context, TextView textView) {
        this.username = username;
        this.password = password;
        this.ocrUsername = ocrUsername;
        this.ocrPassword = ocrPassword;
        this.mode = textView == null ? Mode.Background : Mode.Test;
        this.context = context;
        this.logText = textView;
        if (textView == null) {
            loadRecentLog(context);
        }
    }

    public ClockHelper(String username, String password, String ocrUsername, String ocrPassword, Context context) {
        this(username, password, ocrUsername, ocrPassword, context, null);
    }

    public void run() {
        if (username.isEmpty() || password.isEmpty()) {
            Notification("错误", "用户名或密码为空！");
        } else {
            init();
        }
    }

    private void init() {
        client = new OkHttpClient.Builder()
                .cookieJar(new CookieManager(context))
                .build();
        postForm = new HashMap<>();
        postForm.put("lt", "");
        postForm.put("dllt", "");
        postForm.put("execution", "");
        postForm.put("_eventId", "");
        postForm.put("rmShown", "");
        HealthForm = new HashMap<>();
        GetLoginHTML();
    }

    private void Failed(String progress, Status type) {
        if (this.retryCount < 5) {
            Log.e("e", "Catch an Error:" + progress + "," + type);
            if (this.mode == Mode.Test) {
                Notification("Catch an Error", progress + "," + type);
            }
            this.retryCount += 1;
            this.GetLoginHTML();
        } else {
            if (progress.startsWith("onOCR")) {
                Notification("错误", "OCR服务异常，可能没有正确设置账号或密码");
            } else {
                //重试次数过多
                Notification("错误", "运行时重试次数过多，请检查网络连接是否正常");
            }
        }
    }

    @SuppressLint({"WrongConstant", "SetTextI18n"})
    private void Notification(String type, String message) {
        Log.i("Notification", type + "：" + message);
        if (this.mode == Mode.Test) {
            logText.setText(logText.getText().toString() + type + "：" + message + "\n");
            int scrollAmount = logText.getLayout().getLineTop(logText.getLineCount()) - logText.getHeight();
            logText.scrollTo(0, Math.max(scrollAmount, 0));
        } else {
            if (!type.equals("日志"))
                saveRecentLog(message);
        }
        TimerNotification.INSTANCE.showLargeNotification(context, "运行通知，长按以显示详情", type + "：" + message);
    }

    private void loadRecentLog(Context context) {
        ObjectInputStream objectIn = null;
        boolean fileExist = false;
        String[] files = context.fileList();
        String log_name = "log.dat";
        for (String file : files) {
            if (file.equals(log_name)) {
                fileExist = true;
                break;
            }
        }
        if (fileExist) {
            try {
                FileInputStream in = context.openFileInput(log_name);
                objectIn = new ObjectInputStream(in);
                recentLog = (ArrayDeque<String>) objectIn.readObject();
            } catch (IOException ioException) {
                recentLog = new ArrayDeque<>();
                Notification("日志", "记录日志时错误：" + ioException.toString() + "（是否未授予存储权限？）");
            } catch (Exception e) {
                recentLog = new ArrayDeque<>();
                Notification("日志", "记录日志时错误：" + e.toString());
            } finally {
                if (objectIn != null) {
                    try {
                        objectIn.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            }
        } else {
            recentLog = new ArrayDeque<>();
        }
        try {
            FileOutputStream out = context.openFileOutput(log_name, Context.MODE_PRIVATE);
            objectOut = new ObjectOutputStream(out);
        } catch (IOException ioException) {
            recentLog = new ArrayDeque<>();
            Notification("日志", "记录日志时错误：" + ioException.toString() + "（是否未授予存储权限？）");
        } catch (Exception e) {
            recentLog = new ArrayDeque<>();
            Notification("日志", "记录日志时错误：" + e.toString());
        }
    }

    private void saveRecentLog(String message) {
        try {
            if (recentLog.size() >= 100) {
                recentLog.removeLast();
            }
            recentLog.addFirst(message);
            objectOut.writeObject(recentLog);
            objectOut.flush();
        } catch (Exception e) {
            recentLog = new ArrayDeque<>();
            Notification("日志", "记录日志时错误：" + e.toString());
        }
    }

    private void GetLoginHTML() {
        String progress = "GetLoginHTML";
        Request request = new Request.Builder()
                .url(LoginGetUrl)
                .get()
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Failed(progress, Status.NetWorkFail);
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() == 200) {
                    try {
                        String result = response.body().string();
                        if (result.contains("<i class=\"nav_icon nav_icon_logout\"></i><span>安全退出</span>")) {
                            //已登录，直接打卡
                            GetHealthForm(0);
                        } else {
                            for (String key : postForm.keySet()) {
                                String searchStr = "name=\"" + key + "\" value=\"";
                                int start = result.indexOf(searchStr);
                                int end = result.indexOf("\"", start + searchStr.length());
                                postForm.replace(key, result.substring(start + searchStr.length(), end));
                            }
                            String searchStr = "id=\"pwdDefaultEncryptSalt\" value=\"";
                            int start = result.indexOf(searchStr);
                            int end = result.indexOf("\"", start + searchStr.length());
                            _pwdDefaultEncryptSalt = result.substring(start + searchStr.length(), end);
                            AESpassword = AESUtils.encryptAES(password, _pwdDefaultEncryptSalt);
                            Log.i("i", "password encrypted by AES = " + AESpassword);
                            if (mode == Mode.Test) {
                                Notification("i", "password encrypted by AES = " + AESpassword);
                            }
                            isNeedCaptcha();
                        }
                    } catch (Exception e) {
                        Failed(progress, Status.RunningError);
                    }
                } else {
                    Log.e("e", response.message());
                    Failed(progress, Status.StatusCodeError);
                }
            }
        });
    }

    private void isNeedCaptcha() {
        String progress = "isNeedCaptcha";
        Request request = new Request.Builder()
                .url("https://pass.ujs.edu.cn/cas/needCaptcha.html?username=" + this.username + "&pwdEncrypt2=pwdEncryptSalt&_=" + (int) (Math.random() * Math.pow(10, 13)))
                .get()
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Failed(progress, Status.NetWorkFail);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() == 200) {
                    try {
                        String result = response.body().string();
                        if (result.equals("true")) {
                            //需要验证码，调用OCR接口
                            GetACaptcha();
                        } else {
                            //不需要验证码，直接登陆
                            PostToLogin();
                        }
                    } catch (Exception e) {
                        Failed(progress, Status.RunningError);
                    }
                } else {
                    Log.e("e", response.message());
                    Failed(progress, Status.StatusCodeError);
                }
            }
        });
    }

    private void GetACaptcha() {
        String progress = "GetACaptcha";
        Request request = new Request.Builder()
                .url("https://pass.ujs.edu.cn/cas/captcha.html?ts=" + (int) (Math.random() * Math.pow(10, 3)))
                .get()
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Failed(progress, Status.NetWorkFail);
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() == 200) {
                    try {
                        captchaBase64 = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(response.body().bytes());
                        Log.i("i", "Get the captchaBase64 = " + captchaBase64);
                        if (mode == Mode.Test) {
                            Notification("i", "Get the captchaBase64 = " + captchaBase64);
                        }
                        onOCR1();
                    } catch (Exception e) {
                        Failed(progress, Status.RunningError);
                    }
                } else {
                    Log.e("e", response.message());
                    Failed(progress, Status.StatusCodeError);
                }
            }
        });
    }

    private void onOCR1() {
        String progress = "onOCR1";
        double e = new Date().getTime();
        char[] t = "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".toCharArray();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length; i++) {
            int n = (int) (e + 16 * Math.random()) % 16;
            if (t[i] == 'x') {
                e = Math.floor(e / 16);
                t[i] = Integer.toString(n, 16).toCharArray()[0];
            } else if (t[i] == 'y') {
                e = Math.floor(e / 16);
                t[i] = Integer.toString(3 & n | 8, 16).toCharArray()[0];
            }
            sb.append(t[i]);
        }
        OCR_uuid = sb.toString();
        Log.i("i", "Create OCR_uuid = " + OCR_uuid);
        JSONObject ocrForm = new JSONObject();
        try {
            ocrForm.put("username", ocrUsername);
            ocrForm.put("password", ocrPassword);
            ocrForm.put("type", "mobile");
        } catch (Exception ex) {
            Failed(progress, Status.RunningError);
        }
        Request request = new Request.Builder()
                .url("https://web.baimiaoapp.com/api/user/login")
                .addHeader("x-auth-uuid", OCR_uuid)
                .post(RequestBody.create(MediaType.parse("application/json; charset=UTF-8"), ocrForm.toString()))
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Failed(progress, Status.NetWorkFail);
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() == 200) {
                    try {
                        String result = response.body().string();
                        if (result.contains("success")) {
                            String searchStr = "\"token\":\"";
                            int start = result.indexOf(searchStr);
                            int end = result.indexOf("\"", start + searchStr.length());
                            OCR_token = result.substring(start + searchStr.length(), end);
                            Log.i("i", "Get OCR_token = " + OCR_token);
                            if (mode == Mode.Test) {
                                Notification("i", "Get OCR_token = " + OCR_token);
                            }
                            onOCR2();
                        } else {
                            Failed(progress, Status.RunningError);
                        }
                    } catch (Exception e) {
                        Failed(progress, Status.RunningError);
                    }
                } else {
                    Log.e("e", response.message());
                    Failed(progress, Status.StatusCodeError);
                }
            }
        });
    }

    private void onOCR2() {
        String progress = "onOCR2";
        Request request = new Request.Builder()
                .url("https://web.baimiaoapp.com/api/perm/single")
                .addHeader("x-auth-uuid", OCR_uuid)
                .addHeader("x-auth-token", OCR_token)
                .post(RequestBody.create(MediaType.parse("text/html; charset=utf-8"), ""))
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Failed(progress, Status.NetWorkFail);
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() == 200) {
                    try {
                        String result = response.body().string();
                        if (result.contains("success")) {
                            String searchStr = "\"token\":\"";
                            int start = result.indexOf(searchStr);
                            int end = result.indexOf("\"", start + searchStr.length());
                            job_token = result.substring(start + searchStr.length(), end);
                            onOCR3();
                        } else {
                            Failed(progress, Status.RunningError);
                        }
                    } catch (Exception e) {
                        Failed(progress, Status.RunningError);
                    }
                } else {
                    Log.e("e", response.message());
                    Failed(progress, Status.StatusCodeError);
                }
            }
        });
    }

    private void onOCR3() {
        String progress = "onOCR3";
        JSONObject ocrForm = new JSONObject();
        try {
            ocrForm.put("batchId", "");
            ocrForm.put("total", 1);
            ocrForm.put("hash", captchaBase64.hashCode());
            ocrForm.put("name", "captcha.jfif");
            ocrForm.put("size", captchaBase64.length());
            ocrForm.put("dataUrl", captchaBase64);
            ocrForm.put("status", "processing");
            ocrForm.put("isSuccess", false);
            ocrForm.put("token", job_token);
        } catch (Exception e) {
            Failed(progress, Status.RunningError);
        }
        Request request = new Request.Builder()
                .url("https://web.baimiaoapp.com/api/ocr/image/xunfei")
                .addHeader("x-auth-uuid", OCR_uuid)
                .addHeader("x-auth-token", OCR_token)
                .post(RequestBody.create(MediaType.parse("application/json; charset=UTF-8"), ocrForm.toString()))
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Failed(progress, Status.NetWorkFail);
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() == 200) {
                    try {
                        String result = response.body().string();
                        if (result.contains("success")) {
                            String searchStr = "\"jobStatusId\":\"";
                            int start = result.indexOf(searchStr);
                            int end = result.indexOf("\"", start + searchStr.length());
                            OCR_jobId = URLEncoder.encode(result.substring(start + searchStr.length(), end), "UTF-8");
                            Log.i("i", "Get OCR_jobId = " + OCR_jobId);
                            if (mode == Mode.Test) {
                                Notification("i", "Get OCR_jobId = " + OCR_jobId);
                            }
                            Thread.sleep(5000);
                            onOCR4();
                        } else {
                            Failed(progress, Status.RunningError);
                        }
                    } catch (Exception e) {
                        Failed(progress, Status.RunningError);
                    }
                } else {
                    Log.e("e", response.message());
                    Failed(progress, Status.StatusCodeError);
                }
            }
        });
    }

    private void onOCR4() {
        String progress = "onOCR4";
        Request request = new Request.Builder()
                .url("https://web.baimiaoapp.com/api/ocr/image/xunfei/status?jobStatusId=" + OCR_jobId)
                .addHeader("x-auth-uuid", OCR_uuid)
                .addHeader("x-auth-token", OCR_token)
                .get()
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Failed(progress, Status.NetWorkFail);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() == 200) {
                    try {
                        String result = response.body().string();
                        if (result.contains("success")) {
                            String searchStr = "\"text\":\"";
                            int start = result.indexOf(searchStr);
                            int end = result.indexOf("\"", start + searchStr.length());
                            captcha = result.substring(start + searchStr.length(), end).replaceAll("\u0020", "");
                            Log.i("i", "Get captcha = " + captcha);
                            if (mode == Mode.Test) {
                                Notification("i", "Get captcha = " + captcha);
                            }
                            PostToLogin();
                        } else {
                            Thread.sleep(5000);
                            onOCR4();
                        }
                    } catch (Exception e) {
                        Failed(progress, Status.RunningError);
                    }
                } else {
                    Log.e("e", response.message());
                    Failed(progress, Status.StatusCodeError);
                }
            }
        });
    }

    private void PostToLogin() {
        String progress = "PostToLogin";
        FormBody.Builder formBuilder = new FormBody.Builder()
                .add("username", username)
                .add("password", AESpassword);
        if (!captcha.isEmpty()) {
            formBuilder.add("captchaResponse", captcha);
        }
        for (String key : postForm.keySet()) {
            formBuilder.add(key, postForm.get(key));
        }
        Request request = new Request.Builder()
                .url("https://pass.ujs.edu.cn/cas/login?service=" + serviceURL)
                .post(formBuilder.build())
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Failed(progress, Status.NetWorkFail);
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() == 200) {
                    try {
                        String result = response.body().string();
                        String searchStr = "class=\"auth_error\"";
                        int start = result.indexOf(searchStr);
                        if (start == -1) {
                            Log.i("i", "登录成功");
                            if (mode == Mode.Test) {
                                Notification("i", "登录成功");
                            }
                            GetHealthForm(0);
                        } else {
                            start = result.indexOf(">", start) + 1;
                            int end = result.indexOf("<", start);
                            String errorText = result.substring(start, end);
                            Log.i("i", errorText);
                            if (errorText.equals("无效的验证码")) {
                                //OCR失误，重试
                                GetLoginHTML();
                            } else if (errorText.equals("您提供的用户名或者密码有误")) {
                                //用户名或密码错误
                                Notification("错误", "用户名或密码错误，登录失败");
                            } else {
                                //未知异常
                                Notification("错误", "在登陆时发生意料之外的错误：" + errorText);
                            }
                        }
                    } catch (Exception e) {
                        Failed(progress, Status.RunningError);
                    }
                } else {
                    Log.e("e", response.message());
                    Failed(progress, Status.StatusCodeError);
                }
            }
        });
    }

    private void GetHealthForm(int retryCount) {
        String progress = "GetHealthForm";
        Request request = new Request.Builder()
                .url("http://yun.ujs.edu.cn/xxhgl/yqsb/grmrsb?v=" + (int) (Math.random() * 10000))
                .get()
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Failed(progress, Status.NetWorkFail);
            }

            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() == 200) {
                    try {
                        String result = response.body().string();
                        String searchStr = "<div class=\"weui_cells weui_cells_form\">";
                        if (result.contains(searchStr)) {
                            Document doc = Jsoup.parse(result);
                            //设置HealForm表单
                            FormElement form = (FormElement) doc.selectFirst("form");
                            for (Connection.KeyVal entry : form.formData()) {
                                HealthForm.put(entry.key(), entry.value());
                            }
                            HealthForm.replace("btn", "");
                            HealthForm.replace("xwwd", "36.5");
                            HealthForm.replace("swwd", "36.5");
                            HealthForm.replace("qtyc", "无");
                            PostHealthForm();
                        } else {
                            //重试
                            if (retryCount < 3) {
                                GetHealthForm(retryCount + 1);
                            } else {
                                Failed(progress, Status.RunningError);
                            }
                        }
                    } catch (Exception e) {
                        Failed(progress, Status.RunningError);
                    }
                } else {
                    Log.e("e", response.message());
                    Failed(progress, Status.StatusCodeError);
                }
            }
        });
    }

    private void PostHealthForm() {
        String progress = "PostHealthForm";
        FormBody.Builder postHealthForm = new FormBody.Builder();
        for (String key : HealthForm.keySet()) {
            postHealthForm.add(key, HealthForm.get(key));
        }
        Request request = new Request.Builder()
                .url("http://yun.ujs.edu.cn/xxhgl/yqsb/grmrsb?v=" + (int) (Math.random() * 10000))
                .post(postHealthForm.build())
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Failed(progress, Status.NetWorkFail);
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() == 200) {
                    try {
                        String result = response.body().string();
                        String searchStr = "<div class=\"weui_media_bd\">";
                        int start = result.indexOf(searchStr);
                        if (start != -1) {
                            start += searchStr.length();
                            int timestart = result.indexOf("最新打卡时间：", start);
                            int timeend = result.indexOf("<", timestart);
                            Notification("打卡成功", result.substring(timestart, timeend));
                        } else {
                            Notification("错误", "提交打卡表单时服务器内部错误：" + result);
                        }
                    } catch (Exception e) {
                        Failed(progress, Status.RunningError);
                    }
                } else {
                    Log.e("e", response.message());
                    Failed(progress, Status.StatusCodeError);
                }
            }
        });
    }

    private enum Status {NetWorkFail, StatusCodeError, RunningError}

    private enum Mode {Test, Background}
}