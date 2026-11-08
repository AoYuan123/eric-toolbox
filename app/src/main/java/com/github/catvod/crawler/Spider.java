package com.github.catvod.crawler;

import android.app.Activity;
import android.content.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TVBox / 影视仓那套 Spider 插件的基类。
 *
 * <p>为什么要在这里重现一份：那些 csp_ 开头的插件类编译时是 {@code extends
 * com.github.catvod.crawler.Spider}，插件 jar 被 DexClassLoader 加载后，父加载器
 * 会顺着找到我们这份实现。基类缺失的表现是 NoClassDefFoundError，
 * 方法签名差一个字母的表现是 AbstractMethodError / NoSuchMethodError——
 * 两种都会让插件整个起不来，所以下面每个签名都是从 TVBox 与 FongMi/TV
 * 两个分支的源码逐条对齐过的，不要随手改。
 *
 * <p>方法体一律给空实现：插件的子类会覆盖自己需要的那几个，我们只需要保证
 * 「类能找到、方法能解析」。真正的逻辑全在插件 jar 里。
 */
public abstract class Spider {

    /**
     * 站点 key。插件里大量代码直接读这个字段来拼代理地址（proxy?siteKey=xxx），
     * 所以它必须是 public 字段而不是函数——字段访问和函数调用在字节码层面不是一回事。
     */
    public String siteKey;

    /** 插件初始化时拿到的 Context，代理/本地服务会用到 */
    protected Context context;

    public void init(Context context) throws Exception {
        this.context = context;
    }

    public void init(Context context, String extend) throws Exception {
        init(context);
    }

    public void prepare(String key) {
        this.siteKey = key;
    }

    public String homeContent(boolean filter) throws Exception {
        return "";
    }

    public String homeVideoContent() throws Exception {
        return "";
    }

    public String categoryContent(String tid, String pg, boolean filter,
                                  HashMap<String, String> extend) throws Exception {
        return "";
    }

    public String detailContent(List<String> ids) throws Exception {
        return "";
    }

    public String searchContent(String key, boolean quick) throws Exception {
        return "";
    }

    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return "";
    }

    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return "";
    }

    public String liveContent(String url) throws Exception {
        return "";
    }

    public boolean manualVideoCheck() throws Exception {
        return false;
    }

    public boolean isVideoFormat(String url) throws Exception {
        return false;
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        return null;
    }

    /** 旧版 TVBox 的入口名，等价于 proxy */
    public Object[] proxyLocal(Map<String, String> params) throws Exception {
        return proxy(params);
    }

    public String action(String action) throws Exception {
        return "";
    }

    public void destroy() {
    }

    public void close() {
    }

    /**
     * 插件里有些代码会调 {@code getActivity()} 去 runOnUiThread / 弹框。
     * 我们只有 Application Context，拿不到 Activity，返回 null。
     * 真遇到 NPE 会体现在诊断日志里，届时再评估是否需要反射拿当前 Activity。
     */
    public Activity getActivity() {
        return null;
    }

    public Context getContext() {
        return context;
    }
}
