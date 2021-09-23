package com.tagbug.ujs.autoclock.okhttp;

import android.content.*;

import org.jetbrains.annotations.*;

import java.util.*;

import okhttp3.*;

public class CookieManager implements CookieJar {
    private final PersistentCookieStore cookieStore;

    public CookieManager(Context context) {
        cookieStore = new PersistentCookieStore(context);
    }

    @Override
    public void saveFromResponse(@NotNull HttpUrl url, @NotNull List<Cookie> cookies) {
        if (cookies != null && cookies.size() > 0) {
            for (Cookie item : cookies) {
                cookieStore.add(url, item);
            }
        }
    }

    @NotNull
    @Override
    public List<Cookie> loadForRequest(@NotNull HttpUrl url) {
        return cookieStore.get(url);
    }
}