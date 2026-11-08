package com.eric.toolbox.modules.vod

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.eric.toolbox.modules.vod.core.VodHttp
import com.eric.toolbox.modules.vod.core.VodPrefs
import com.eric.toolbox.modules.vod.model.Episode
import com.eric.toolbox.modules.vod.model.PlayHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.runtime.Composable

/**
 * 播放页。
 *
 * 用独立 Activity 而不是在模块里内嵌，是因为全屏横屏、屏幕常亮、沉浸模式
 * 这几件事在这里最简单可靠，也不会被上层页面的重组牵连。
 *
 * 自带的 PlayerView 控制器负责进度 / 倍速 / 音轨，
 * 我们只在其上补一层「返回 + 标题 + 下一集」的业务栏。
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    /** 必须做成 state：PlayerView 的 update 要跟着它走，否则会出现黑屏。 */
    private var readyPlayer by mutableStateOf<ExoPlayer?>(null)

    private var barVisible by mutableStateOf(true)
    private var hint by mutableStateOf("")
    private var displayTitle by mutableStateOf("播放")
    private var sourceLabel by mutableStateOf("")
    private var epIndex by mutableIntStateOf(0)

    private var episodes: List<Episode> = emptyList()
    private var originalTitle = "播放"
    private var referer = ""
    private var ua = ""
    private var pic = ""
    /** 这一路播放属于哪个站点，云盘集要靠它把 token 换成真地址 */
    private var siteKey = ""
    private var playFlag = ""

    /** 插件给每一集指定的请求头（Referer/UA 等），按最终地址存 */
    private val headersByUrl = HashMap<String, Map<String, String>>()

    /** 当前播放器是用哪套头建的。变了才重建，见 ensurePlayer */
    private var playerHeaders: Map<String, String>? = null

    /** 换地址是异步的（要问插件），Activity 自己管生命周期比 lifecycleScope 稳妥 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 嗅探结果按地址缓存：一集只探一次，重试/换集不再重复 */
    private val mimeCache = HashMap<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        readExtras(intent)

        setContent {
            // BackHandler 必须在 Compose 上下文里，放到 setContent 外会编译不过
            BackHandler { finish() }
            Column(Modifier.fillMaxSize().background(Color.Black)) {
                if (barVisible) {
                    ChromeBar(
                        title = displayTitle,
                        source = sourceLabel,
                        hasNext = hasIndex(epIndex + 1),
                        onBack = { finish() },
                        onNext = { play(epIndex + 1) },
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = true
                                controllerShowTimeoutMs = 3000
                                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                                // 两个重载同名（PlayerView / PlayerControlView），必须显式指定类型
                                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                                    barVisible = v == android.view.View.VISIBLE
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view -> view.player = readyPlayer },
                    )
                    if (hint.isNotBlank()) {
                        Box(Modifier.align(Alignment.Center).padding(24.dp)) {
                            Text(hint, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
        // 播放器在 startUrl 里按需创建（请求头每集可能不同）
        play(epIndex)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras(intent)
        play(0)
    }

    override fun onResume() {
        super.onResume()
        val p = exoPlayer ?: return
        if (p.playbackState != Player.STATE_ENDED) p.playWhenReady = true
    }

    override fun onPause() {
        saveProgress()
        exoPlayer?.playWhenReady = false
        super.onPause()
    }

    override fun onDestroy() {
        saveProgress()
        scope.cancel()
        exoPlayer?.release()
        exoPlayer = null
        readyPlayer = null
        super.onDestroy()
    }

    // -------------------------------------------------------------- 参数读取

    private fun readExtras(src: Intent?) {
        originalTitle = src?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "播放" }
        referer = src?.getStringExtra(EXTRA_REFERER).orEmpty()
        ua = src?.getStringExtra(EXTRA_UA).orEmpty()
        pic = src?.getStringExtra(EXTRA_PIC).orEmpty()
        siteKey = src?.getStringExtra(EXTRA_SITE_KEY).orEmpty()
        playFlag = src?.getStringExtra(EXTRA_PLAY_FLAG).orEmpty()
        val source = src?.getStringExtra(EXTRA_SOURCE).orEmpty()
        sourceLabel = source
        episodes = parseEpisodes(src?.getStringExtra(EXTRA_EPISODES).orEmpty())
        val startUrl = src?.getStringExtra(EXTRA_URL).orEmpty()
        val idx = episodes.indexOfFirst { it.url == startUrl }
        epIndex = if (idx < 0) 0 else idx
        parseHeaders(src?.getStringExtra(EXTRA_HEADERS)).let { h ->
            if (h.isNotEmpty()) headersByUrl[startUrl] = h
        }
        if (episodes.isEmpty()) hint = "没有可播放的地址"
    }

    private fun parseHeaders(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            val out = HashMap<String, String>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = o.optString(k)
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun parseEpisodes(json: String): List<Episode> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                Episode(o.optString("n"), o.optString("u"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------------------------------------------------------------- 播放器

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED && hasIndex(epIndex + 1)) play(epIndex + 1)
        }

        override fun onPlayerError(error: PlaybackException) {
            saveProgress()
            // PlaybackException 的 message 是笼统的「Source error」，
            // 真正的原因（404 / 解析失败 / 超时）在 cause 链上，挖到底层再说人话
            var root: Throwable = error
            while (root.cause != null && root.cause !== root) root = root.cause!!
            val msg = VodHttp.messageOf(root).ifBlank { error.message.orEmpty() }
            val proxyNote = if (currentUrl().contains("127.0.0.1")) {
                "\n这条线路走了本地代理（9978），失败原因可在影视的 Spider 插件诊断里看日志"
            } else ""
            hint = "播放失败：$msg$proxyNote\n可以换一条线路试试；云盘线路要等插件换地址，稍等再点一次"
            barVisible = true
        }
    }

    /**
     * 请求头是「每集一套」，而 Media3 1.6 的 DefaultHttpDataSource 既不能子类化
     * 也没有 setMediaSourceFactory，所以只有在 header 变了的时候重建播放器。
     * 绝大多数剧集 header 相同，不会每集都重建。
     */
    private fun ensurePlayer(headers: Map<String, String>): ExoPlayer {
        exoPlayer?.let { if (playerHeaders == headers) return it }
        exoPlayer?.release()
        val factory = DefaultHttpDataSource.Factory()
            .setUserAgent(ua.ifBlank { VodHttp.DEFAULT_UA })
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val props = HashMap<String, String>()
        if (referer.isNotBlank()) props["Referer"] = referer
        props.putAll(headers)
        if (props.isNotEmpty()) factory.setDefaultRequestProperties(props)
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .build()
        p.addListener(playerListener)
        exoPlayer = p
        readyPlayer = p
        playerHeaders = headers
        return p
    }

    private fun play(index: Int) {
        if (!hasIndex(index)) return
        epIndex = index
        val ep = episodes[index]
        hint = ""
        displayTitle = if (originalTitle == "播放") ep.name else "$originalTitle · ${ep.name}"
        barVisible = true
        // 只有确定的媒体直链（m3u8/mp4 等）才能直接喂播放器。
        // 云盘站的集地址经常是分享链接网页（https://pan.quark.cn/s/xxx），
        // 虽然以 http 开头，直接播必然 Source error——必须先问插件换真地址。
        if (isDirectMedia(ep.url)) {
            startUrl(ep.url)
            return
        }
        hint = "正在换取播放地址…"
        scope.launch {
            val r = VodModule.resolvePlayUrl(this@PlayerActivity, siteKey, playFlag, ep.url)
            if (!r.ok) {
                hint = r.error.ifBlank { "这个站点没有给出可播放的地址" }
                barVisible = true
                return@launch
            }
            headersByUrl[r.url] = r.headers
            episodes = episodes.toMutableList().also { it[index] = Episode(ep.name, r.url) }
            hint = ""
            startUrl(r.url)
        }
    }

    /**
     * 是不是「拿去就能播」的地址。
     * 与 VodModule.looksDirect 同口径：必须是 http(s) 且带已知媒体扩展名。
     * 本地代理地址（127.0.0.1:9978/proxy）没有扩展名，但它是插件换出来的
     * 最终产物，绝不能再拿去问一遍插件（插件不认，等于又坏一层），所以算直链，
     * 格式交给 sniffMime 去嗅探。
     */
    private fun isDirectMedia(u: String): Boolean {
        if (u.contains("127.0.0.1") || u.contains("localhost")) return true
        if (!u.startsWith("http")) return false
        val s = u.lowercase()
        return ".m3u8" in s || ".mp4" in s || ".flv" in s ||
            ".mkv" in s || ".ts" in s
    }

    private fun startUrl(u: String) {
        if (u.isBlank()) return
        scope.launch {
            val headers = headersByUrl[u].orEmpty()
            val mime = sniffMime(u, headers)
            val p = ensurePlayer(headers)
            // 本地代理地址没有文件扩展名，Media3 默认按 MP4 解析；
            // 而插件回的常是 m3u8 播放列表——不显式给 mimeType 就是
            // 「Source error」的头号来源。
            val item = MediaItem.Builder().setUri(u).setMediaId(u)
            if (mime != null) item.setMimeType(mime)
            p.setMediaItem(item.build())
            p.prepare()
            p.playWhenReady = true
        }
    }

    /** 从地址字面就能定的格式。 */
    private fun mimeFromUrl(u: String): String? {
        val s = u.lowercase()
        return when {
            ".m3u8" in s -> "application/x-mpegURL"
            ".mpd" in s -> "application/dash+xml"
            else -> null
        }
    }

    /**
     * 本地代理地址嗅探：拉流头部几百字节看内容长什么样。
     * 插件的代理返回什么格式没有约定，只能看一眼——
     * #EXTM3U 开头是 HLS，<MPD 开头是 DASH，其余交给默认 progressive。
     */
    private suspend fun sniffMime(u: String, headers: Map<String, String>): String? {
        mimeFromUrl(u)?.let { return it }
        val local = u.contains("127.0.0.1") || u.contains("localhost")
        if (!local) return null
        synchronized(mimeCache) { mimeCache[u] }?.let { return it }
        val probed = withContext(Dispatchers.IO) {
            runCatching {
                val client = OkHttpClient.Builder()
                    .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()
                val b = Request.Builder().url(u)
                headers.forEach { (k, v) -> if (!k.equals("range", true)) b.header(k, v) }
                // 只要头部几百字节，够了；服务器不支持 Range 会回全量，读完就断开
                b.header("Range", "bytes=0-1023")
                client.newCall(b.build()).execute().use { resp ->
                    val body = resp.body ?: return@runCatching null
                    val buf = ByteArray(256)
                    var n = 0
                    while (n < buf.size) {
                        val r = body.byteStream().read(buf, n, buf.size - n)
                        if (r < 0) break
                        n += r
                    }
                    if (n == 0) return@runCatching null
                    val head = String(buf, 0, n, Charsets.ISO_8859_1).trimStart()
                    when {
                        head.startsWith("#EXTM3U") -> "application/x-mpegURL"
                        head.startsWith("<MPD") -> "application/dash+xml"
                        else -> null
                    }
                }
            }.getOrNull()
        }
        if (probed != null) synchronized(mimeCache) { mimeCache[u] = probed }
        return probed
    }

    private fun currentUrl(): String = episodes.getOrNull(epIndex)?.url.orEmpty()

    private fun hasIndex(i: Int): Boolean = i in episodes.indices

    private fun saveProgress() {
        val p = exoPlayer ?: return
        val pos = p.currentPosition
        val url = currentUrl()
        if (pos <= 0L || url.isBlank()) return
        VodPrefs.addHistory(
            this,
            PlayHistory(
                title = originalTitle,
                sourceName = sourceLabel,
                url = url,
                episodeName = episodes.getOrNull(epIndex)?.name.orEmpty(),
                pic = pic,
                positionMs = pos,
                durationMs = if (p.duration > 0) p.duration else 0L,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    companion object {
        const val EXTRA_URL = "vod_url"
        const val EXTRA_TITLE = "vod_title"
        const val EXTRA_REFERER = "vod_referer"
        const val EXTRA_UA = "vod_ua"
        const val EXTRA_SOURCE = "vod_source"
        const val EXTRA_PIC = "vod_pic"
        const val EXTRA_EPISODES = "vod_episodes"
        const val EXTRA_SITE_KEY = "vod_site_key"
        const val EXTRA_PLAY_FLAG = "vod_play_flag"
        const val EXTRA_HEADERS = "vod_headers"

        fun start(
            ctx: Context,
            title: String,
            pic: String,
            sourceName: String,
            referer: String,
            ua: String,
            siteKey: String,
            playFlag: String,
            episodes: List<Episode>,
            startIndex: Int,
            startHeaders: Map<String, String> = emptyMap(),
        ) {
            if (episodes.isEmpty()) {
                Toast.makeText(ctx, "这条线路没有可播放的地址", Toast.LENGTH_SHORT).show()
                return
            }
            val from = startIndex.coerceIn(0, episodes.lastIndex)
            val arr = JSONArray()
            episodes.forEach { arr.put(JSONObject().put("n", it.name).put("u", it.url)) }
            val head = JSONObject()
            startHeaders.forEach { (k, v) -> head.put(k, v) }
            ctx.startActivity(
                Intent(ctx, PlayerActivity::class.java).apply {
                    putExtra(EXTRA_URL, episodes[from].url)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_PIC, pic)
                    putExtra(EXTRA_SOURCE, sourceName)
                    putExtra(EXTRA_REFERER, referer)
                    putExtra(EXTRA_UA, ua)
                    putExtra(EXTRA_SITE_KEY, siteKey)
                    putExtra(EXTRA_PLAY_FLAG, playFlag)
                    putExtra(EXTRA_HEADERS, head.toString())
                    putExtra(EXTRA_EPISODES, arr.toString())
                },
            )
        }
    }
}

/** 播放器顶栏：返回 + 标题 + 下一集。 */
@Composable
private fun ChromeBar(
    title: String,
    source: String,
    hasNext: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xCC000000)).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, color = Color.White, fontSize = 15.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (source.isNotBlank()) {
                Text(source, color = Color(0xFFB0B0B0), fontSize = 12.sp, maxLines = 1)
            }
        }
        if (hasNext) {
            Row(
                Modifier.clickable(onClick = onNext).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("下一集", color = Color.White, fontSize = 13.sp)
                Icon(Icons.Filled.SkipNext, "下一集", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
