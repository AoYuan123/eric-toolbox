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
        setupPlayer()
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
        val source = src?.getStringExtra(EXTRA_SOURCE).orEmpty()
        sourceLabel = source
        episodes = parseEpisodes(src?.getStringExtra(EXTRA_EPISODES).orEmpty())
        val startUrl = src?.getStringExtra(EXTRA_URL).orEmpty()
        val idx = episodes.indexOfFirst { it.url == startUrl }
        epIndex = if (idx < 0) 0 else idx
        if (episodes.isEmpty()) hint = "没有可播放的地址"
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

    private fun setupPlayer() {
        val factory = DefaultHttpDataSource.Factory()
            .setUserAgent(ua.ifBlank { VodHttp.DEFAULT_UA })
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        if (referer.isNotBlank()) {
            factory.setDefaultRequestProperties(mapOf("Referer" to referer))
        }
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && hasIndex(epIndex + 1)) play(epIndex + 1)
            }

            override fun onPlayerError(error: PlaybackException) {
                saveProgress()
                val msg = VodHttp.messageOf(error)
                hint = if (currentUrl().startsWith("http://")) {
                    "播放失败：$msg\n这条线路是明文 http，App 的安全策略不允许加载，请换 https 线路"
                } else {
                    "播放失败：$msg\n可以换一条线路试试，或在设置里启用解析器"
                }
                barVisible = true
            }
        })
        exoPlayer = p
        readyPlayer = p
    }

    private fun play(index: Int) {
        if (!hasIndex(index)) return
        epIndex = index
        val ep = episodes[index]
        hint = ""
        displayTitle = if (originalTitle == "播放") ep.name else "$originalTitle · ${ep.name}"
        barVisible = true
        val p = exoPlayer ?: return
        p.setMediaItem(MediaItem.Builder().setUri(ep.url).setMediaId(ep.url).build())
        p.prepare()
        p.playWhenReady = true
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

        fun start(
            ctx: Context,
            title: String,
            pic: String,
            sourceName: String,
            referer: String,
            ua: String,
            episodes: List<Episode>,
            startIndex: Int,
        ) {
            if (episodes.isEmpty()) {
                Toast.makeText(ctx, "这条线路没有可播放的地址", Toast.LENGTH_SHORT).show()
                return
            }
            val from = startIndex.coerceIn(0, episodes.lastIndex)
            val arr = JSONArray()
            episodes.forEach { arr.put(JSONObject().put("n", it.name).put("u", it.url)) }
            ctx.startActivity(
                Intent(ctx, PlayerActivity::class.java).apply {
                    putExtra(EXTRA_URL, episodes[from].url)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_PIC, pic)
                    putExtra(EXTRA_SOURCE, sourceName)
                    putExtra(EXTRA_REFERER, referer)
                    putExtra(EXTRA_UA, ua)
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
