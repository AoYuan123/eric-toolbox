package com.github.catvod.crawler;

import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件的日志出口。
 *
 * <p>插件里到处是 {@code SpiderDebug.log(...)}，不提供这个类就是 NoClassDefFoundError。
 *
 * <p>顺手做了一件事：把日志留在内存里（最近 [MAX] 条）。插件出问题时的现场
 * 只有它自己知道，用户把这一段贴过来，比"加载失败"四个字有用得多。
 */
public class SpiderDebug {

    private static final String TAG = "Spider";
    private static final int MAX = 400;

    private static final List<String> BUFFER = new ArrayList<>();

    public static void log(String msg) {
        log(TAG, msg);
    }

    public static synchronized void log(String tag, String msg) {
        try {
            Log.d(tag == null ? TAG : tag, String.valueOf(msg));
        } catch (Throwable ignored) {
        }
        synchronized (BUFFER) {
            if (BUFFER.size() >= MAX) BUFFER.remove(0);
            BUFFER.add(String.valueOf(msg));
        }
    }

    public static void log(Throwable e) {
        log(TAG, e);
    }

    public static void log(String tag, Throwable e) {
        if (e == null) return;
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        log(tag, sw.toString());
    }

    /** 供诊断面板读取，调用方拿到的是快照 */
    public static synchronized String dump() {
        synchronized (BUFFER) {
            if (BUFFER.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (String s : BUFFER) sb.append(s).append('\n');
            return sb.toString();
        }
    }

    public static synchronized void clear() {
        synchronized (BUFFER) {
            BUFFER.clear();
        }
    }
}
