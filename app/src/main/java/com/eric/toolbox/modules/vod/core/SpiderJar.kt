package com.eric.toolbox.modules.vod.core

import android.content.Context
import com.github.catvod.crawler.SpiderDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Spider 插件包（TVBox 配置里 spider 字段指向的那个文件）的下载与缓存。
 *
 * 这个字段的写法五花八门：
 *   https://xxx.com/spider.jar;md5;9d5f341aa5f3353b50cc3bc16b4f3e8a
 *   https://xxx.com/xxx.jpg;md5;abc...        ← 伪装成图片，很常见
 *   ./lib/spider.jar                          ← 相对路径，跟着配置仓库走
 *
 * 前两种能下，第三种没法处理（我们没有配置所在的仓库）。
 *
 * 下下来不一定叫 .jar：它就是个 zip，里面通常是 classes.dex（或若干 .dex），
 * 有些加固过的还带 assets/*.so + 加密载荷。所以这里做两件事：
 *   1. 原样存成 .jar 交给 DexClassLoader
 *   2. 解压一份到同名目录——加固壳常常要按相对路径找自己的 assets
 */
object SpiderJar {

    data class Decl(val url: String, val md5: String = "") {
        val isRemote: Boolean get() = url.startsWith("http://") || url.startsWith("https://")
    }

    private fun root(ctx: Context): File = File(ctx.filesDir, "spider").apply { mkdirs() }

    /**
     * 解析 spider 字段。返回 null 表示这份配置没指定插件。
     * 返回的 Decl 里 isRemote=false 表示写了但我们下不了（相对路径）。
     */
    fun parse(raw: String): Decl? {
        val s = raw.trim()
        if (s.isBlank()) return null
        val bits = s.split(';')
        val url = bits.firstOrNull()?.trim().orEmpty()
        if (url.isBlank()) return null
        var md5 = ""
        val at = bits.indexOfFirst { it.equals("md5", true) }
        if (at >= 0 && at + 1 < bits.size) md5 = bits[at + 1].trim().lowercase()
        return Decl(url, md5)
    }

    /** 已经下好的插件文件；没下过返回 null。不联网。 */
    fun cached(ctx: Context, decl: Decl): File? {
        val jar = File(workDir(ctx, decl), "spider.jar")
        if (!jar.isFile || jar.length() == 0L) return null
        // 声明里带 md5 就必须对得上，否则宁可当没缓存，重新下。
        // 这类文件是会被远程替换的，校验是唯一的把关点。
        if (decl.md5.isNotBlank() && md5Of(jar) != decl.md5) {
            SpiderDebug.log("插件 md5 不匹配，缓存作废: ${jar.name}")
            return null
        }
        return jar
    }

    /** 下载 + 校验 + 解压。失败抛异常，消息可直接给用户看。 */
    suspend fun ensure(ctx: Context, decl: Decl, force: Boolean = false): File = withContext(Dispatchers.IO) {
        if (!decl.isRemote) {
            throw IllegalStateException("这份配置的插件写的是相对路径（${decl.url}），App 里没法下载")
        }
        if (!force) cached(ctx, decl)?.let { return@withContext it }

        SpiderDebug.log("开始下载插件: ${decl.url}")
        val bytes = try {
            VodHttp.getBytes(decl.url, timeoutMs = 90_000)
        } catch (e: Exception) {
            SpiderDebug.log("插件下载失败: ${VodHttp.messageOf(e)}")
            throw IllegalStateException("插件下载失败：${VodHttp.messageOf(e)}")
        }
        if (bytes.size < 512) {
            throw IllegalStateException("插件文件只有 ${bytes.size} 字节，不像是有效文件")
        }
        if (decl.md5.isNotBlank()) {
            val got = md5Of(bytes)
            if (got != decl.md5) {
                // 不静默放过：md5 不符意味着拿到的内容和配置声明的不是同一个东西，
                // 可能是被中间人换了，也可能只是配置作者没更新 md5。两种都不该直接执行。
                throw IllegalStateException("插件 md5 与配置声明不一致（声明 ${decl.md5}，实际 $got），已停止加载")
            }
        }

        val dir = workDir(ctx, decl).apply { mkdirs() }
        // 解压放在写 jar 之前：unzip 会先清空目录，顺序反了会把 jar 删掉。
        runCatching { unzip(bytes, dir) }
            .onFailure { SpiderDebug.log("插件解压失败（不影响 dex 加载）: ${it.message}") }

        // jar 与它的 assets 放在同一层：加固壳普遍按「jar 所在目录」找自己的
        // so 和加密载荷，放到别处它找不到，表现就是初始化直接崩。
        val jar = File(dir, "spider.jar")
        if (jar.exists()) {
            jar.setWritable(true)
            jar.delete()
        }
        jar.writeBytes(bytes)
        // Android 14 起，DexClassLoader 不接受「可写」的 dex 文件（W^X 策略），
        // 所以落盘后立刻置只读；反过来要覆盖时得先改回可写。
        if (!jar.setReadOnly()) {
            SpiderDebug.log("插件置只读失败，Android 14+ 可能会拒绝加载")
        }
        SpiderDebug.log("插件已保存 ${jar.absolutePath} / ${bytes.size} 字节")
        jar
    }

    /** 插件的工作目录（含解压出来的 assets 与 spider.jar） */
    fun workDir(ctx: Context, decl: Decl): File = File(root(ctx), key(decl))

    private fun key(decl: Decl): String =
        decl.md5.ifBlank { md5Of(decl.url.toByteArray()) }.take(40)

    fun md5Of(file: File): String = runCatching { md5Of(file.readBytes()) }.getOrDefault("")

    fun md5Of(bytes: ByteArray): String {
        val d = MessageDigest.getInstance("MD5").digest(bytes)
        val sb = StringBuilder(d.size * 2)
        for (b in d) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    private fun unzip(bytes: ByteArray, target: File) {
        target.deleteRecursively()
        target.mkdirs()
        val base = target.canonicalPath + File.separator
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val f = File(target, e.name)
                // zip slip：条目名里带 ../ 就能写到目录外面去
                if (!f.canonicalPath.startsWith(base)) {
                    SpiderDebug.log("跳过可疑的 zip 条目: ${e.name}")
                    zis.closeEntry()
                    e = zis.nextEntry
                    continue
                }
                if (e.isDirectory) {
                    f.mkdirs()
                } else {
                    f.parentFile?.mkdirs()
                    f.outputStream().use { out -> zis.copyTo(out) }
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
    }
}
