package com.eric.toolbox.modules.vod.core

import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderDebug
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 本地播放代理，监听 127.0.0.1:9978。
 *
 * 云盘类插件（夸克、阿里、UC……）拿到分享链接后不会直接给媒体地址，
 * 而是返回 `http://127.0.0.1:9978/proxy?do=xxx&...` —— 这个地址由宿主 App
 * 的本地 HTTP 服务实现：转发给对应插件的 proxy(params)，插件自己去换直链、
 * 带 cookie 拉流、拼 m3u8。TVBox / 影视仓之所以能播云盘资源，就是因为它常驻
 * 这个 9978 服务。
 *
 * 我们以前只把 playerContent 返回的字符串丢给播放器，127.0.0.1 上没人监听，
 * 于是「有线路、点开就报错」。这里把它补齐，端口号与 TVBox 保持一致——
 * 728 这类地址是写死在插件里的，改端口插件就不认了。
 */
object SpiderProxy {

    /** 与 TVBox 一致。插件里的地址是写死的常量，不要改成别的 */
    const val PORT = 9978

    private const val HOST = "127.0.0.1"

    /** 与站点一一对应。键有 site.key、api 原名、剥掉 csp_ 的类名三种写法 */
    private val targets = ConcurrentHashMap<String, Spider>()

    private val running = AtomicBoolean(false)

    private var server: ServerSocket? = null

    // HLS 播放时分片请求是连续不断的，固定 6 个线程会让分片在池里排队、
    // 播放器缓冲转圈。本地代理的并发天然有限，缓存线程池足够。
    private val pool = Executors.newCachedThreadPool()

    /**
     * 路由不到时的补建钩子，由 SpiderHub 注入。
     * 场景：插件 A 换出来的代理地址是 do=quark，指向配置里另一个站点类，
     * 但那个站点的 Spider 实例要「搜过/播过」才存在——现查现建，别让
     * 请求落到错误的兜底上。
     */
    @Volatile
    var resolver: ((String) -> Spider?)? = null

    /**
     * 兜底用：有些插件请求里不带 do 参数，或者带的是它自己才懂的名字。
     * 记一下最近一次真正发起播放的站点，找不到路由时用它。
     */
    @Volatile
    private var lastSpider: Spider? = null

    fun register(key: String, spider: Spider) {
        if (key.isBlank()) return
        targets[key] = spider
    }

    fun bindLast(spider: Spider) {
        lastSpider = spider
    }

    /** 给插件用的基地址，日志里方便对照 */
    fun baseUrl(): String = "http://$HOST:$PORT/proxy"

    /** 幂等：重复调用只有第一次真正起线程 */
    fun ensureStarted() {
        if (!running.compareAndSet(false, true)) return
        val t = Thread({
            try {
                val ss = ServerSocket(PORT, 50, InetAddress.getByName(HOST))
                server = ss
                SpiderDebug.log("播放代理已启动: http://$HOST:$PORT/proxy")
                while (!ss.isClosed) {
                    val sock = try {
                        ss.accept()
                    } catch (e: Exception) {
                        break
                    }
                    pool.execute { handle(sock) }
                }
            } catch (e: Throwable) {
                running.set(false)
                SpiderDebug.log("播放代理启动失败（端口 $PORT 被占用？）: ${e.javaClass.simpleName}: ${e.message}")
            }
        }, "spider-proxy")
        t.isDaemon = true
        t.start()
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        running.set(false)
    }

    // ------------------------------------------------------------- 请求处理

    private fun handle(sock: Socket) {
        try {
            sock.soTimeout = 20_000
            val input = BufferedInputStream(sock.getInputStream())
            val head = readHead(input) ?: return
            val lines = head.split("\r\n")
            val first = lines.firstOrNull().orEmpty().split(' ')
            if (first.size < 2) return

            val target = first[1]
            val headers = HashMap<String, String>()
            for (i in 1 until lines.size) {
                val line = lines[i]
                val c = line.indexOf(':')
                if (c > 0) headers[line.substring(0, c).trim().lowercase()] = line.substring(c + 1).trim()
            }

            val mark = target.indexOf('?')
            val query = if (mark < 0) "" else target.substring(mark + 1)
            val params = parseQuery(query)
            // 插件里有些实现会读 range 做分片，透传过去
            headers["range"]?.let { params["range"] = it }

            val spider = pick(params["do"].orEmpty())
            if (spider == null) {
                respondText(sock, 502, "没有找到对应的插件：do=${params["do"].orEmpty()}")
                return
            }
            // proxy 是 TVBox 的入口，proxyLocal 是旧版名字；基类里后者默认转前者，
            // 但子类可能只覆盖了其中一个，所以两个都试。
            // 抛异常不能吞掉——那是诊断页里唯一的线索。
            val result: Array<*>? = runCatching { spider.proxy(params) }
                .onFailure { SpiderDebug.log("播放代理 ${spider.javaClass.simpleName}.proxy 抛异常: ${SpiderEngine.rootMessage(it)}") }
                .getOrNull()
                ?: runCatching { spider.proxyLocal(params) }
                    .onFailure { SpiderDebug.log("播放代理 ${spider.javaClass.simpleName}.proxyLocal 抛异常: ${SpiderEngine.rootMessage(it)}") }
                    .getOrNull()
            if (result == null) {
                SpiderDebug.log("播放代理: ${spider.javaClass.simpleName}.proxy 返回空 do=${params["do"].orEmpty()}")
                respondText(sock, 502, "插件没有处理这个代理请求")
                return
            }
            respond(sock, result)
        } catch (e: Throwable) {
            SpiderDebug.log("播放代理请求异常: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            runCatching { sock.close() }
        }
    }

    private fun pick(name: String): Spider? {
        if (name.isNotBlank()) {
            targets[name]?.let { return it }
            targets["csp_$name"]?.let { return it }
            // 已登记的键没对上：这个站点的实例可能还没被创建过，让 SpiderHub 现查现建
            resolver?.invoke(name)?.let { return it }
        }
        return lastSpider
    }

    /**
     * 插件返回的 Object[] 是 TVBox 的约定：
     *   [0] 状态码 200 / 302
     *   [1] 200 时是 Content-Type，302 时是头信息 Map（含 Location）
     *   [2] 响应体：InputStream 或 byte[]
     */
    private fun respond(sock: Socket, result: Array<*>) {
        val out = BufferedOutputStream(sock.getOutputStream())
        val status = (result.getOrNull(0) as? Int) ?: 200

        if (status == 301 || status == 302 || status == 307) {
            val headers = result.getOrNull(1) as? Map<*, *>
            val location = headers?.entries
                ?.firstOrNull { it.key?.toString().equals("Location", ignoreCase = true) }
                ?.value?.toString().orEmpty()
            val sb = StringBuilder()
            sb.append("HTTP/1.1 ").append(status).append(" Redirect\r\n")
            if (location.isNotBlank()) sb.append("Location: ").append(location).append("\r\n")
            sb.append("Content-Length: 0\r\nConnection: close\r\n\r\n")
            out.write(sb.toString().toByteArray())
            out.flush()
            return
        }

        var contentType = "application/octet-stream"
        var body: Any? = null
        for (i in 1 until result.size) {
            when (val v = result[i]) {
                is String -> contentType = v.ifBlank { contentType }
                is InputStream -> body = v
                is ByteArray -> body = v
                else -> Unit
            }
        }

        val head = StringBuilder()
        head.append("HTTP/1.1 200 OK\r\n")
        head.append("Content-Type: ").append(contentType).append("\r\n")
        if (body is ByteArray) head.append("Content-Length: ").append(body.size).append("\r\n")
        // 不能一直挂着 keep-alive：不知道长度的流只能靠关闭连接告诉客户端结束
        head.append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray())

        when (val b = body) {
            is InputStream -> copyStream(b, out)
            is ByteArray -> out.write(b)
            else -> Unit
        }
        out.flush()
    }

    private fun copyStream(input: InputStream, out: BufferedOutputStream) {
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (n > 0) out.write(buf, 0, n)
        }
        runCatching { input.close() }
    }

    private fun respondText(sock: Socket, code: Int, msg: String) {
        val body = msg.toByteArray()
        val head = "HTTP/1.1 $code Error\r\nContent-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        val out = BufferedOutputStream(sock.getOutputStream())
        out.write(head.toByteArray())
        out.write(body)
        out.flush()
    }

    // --------------------------------------------------------------- 解析工具

    private fun readHead(input: InputStream): String? {
        val buf = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.isEmpty()) null else buf.toString()
            buf.append(b.toChar())
            if (buf.endsWith("\r\n\r\n")) return buf.toString()
            if (buf.length > 16_384) return null
        }
    }

    private fun parseQuery(q: String): HashMap<String, String> {
        val out = HashMap<String, String>()
        if (q.isBlank()) return out
        for (part in q.split('&')) {
            if (part.isBlank()) continue
            val i = part.indexOf('=')
            val k = if (i < 0) part else part.substring(0, i)
            val v = if (i < 0) "" else part.substring(i + 1)
            out[decode(k)] = decode(v)
        }
        return out
    }

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
