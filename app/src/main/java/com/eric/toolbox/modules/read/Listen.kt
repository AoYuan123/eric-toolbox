package com.eric.toolbox.modules.read

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.util.Locale

/**
 * 听书引擎（全 App 只有一个）：机器朗读用系统 TextToSpeech 逐句读；真人朗读用 ExoPlayer 播放已下载到手机的 LibriVox 章节。
 * 状态都是 Compose 可观察的，阅读页、听书面板、通知栏共用；离开阅读页、锁屏后由 ListenService 保持在前台继续播放。
 */
object Listen {
    enum class Mode { TTS, AUDIO }

    var bookId by mutableStateOf<String?>(null); private set
    var bookTitle by mutableStateOf(""); private set
    var mode by mutableStateOf(Mode.TTS); private set
    var playing by mutableStateOf(false); private set
    var chapter by mutableIntStateOf(0); private set
    var chapterTitle by mutableStateOf(""); private set
    var chapterCount by mutableIntStateOf(0); private set
    var sentences by mutableStateOf<List<ReadLogic.Sentence>>(emptyList()); private set
    var index by mutableIntStateOf(0); private set
    var speed by mutableFloatStateOf(1f); private set
    /** 0 不定时；>0 到这个时间点停；-1 读完本章停 */
    var sleepAt by mutableLongStateOf(0L); private set
    var message by mutableStateOf<String?>(null)
    var autoNext by mutableStateOf(true)
    /** 每次跳到新句子加 1，阅读页据此同步高亮 */
    var tick by mutableIntStateOf(0); private set

    // 真人朗读
    var audio by mutableStateOf<ReadLogic.AudioBook?>(null); private set
    var section by mutableIntStateOf(0); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set

    private val main = Handler(Looper.getMainLooper())
    private lateinit var app: Context
    private var content: BookContent? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var cjk = false
    private var player: ExoPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var gen = 0

    val active get() = bookId != null
    val current: ReadLogic.Sentence? get() = sentences.getOrNull(index)

    private fun prefs() = app.getSharedPreferences("mod_read", Context.MODE_PRIVATE)

    // ================= 机器朗读 =================
    /** 从某章某句开始朗读。 */
    fun startTts(ctx: Context, book: Library.Book, ch: Int, sentenceIndex: Int) {
        app = ctx.applicationContext
        if (bookId != book.id || mode != Mode.TTS || content == null) {
            releaseAll(keepTts = true)
            content = BookContent.open(Library.bookFile(app, book), book.format)
            cjk = content!!.cjk
        }
        bookId = book.id; bookTitle = book.title; mode = Mode.TTS
        speed = prefs().getFloat("tts_speed", 1f)
        autoNext = prefs().getBoolean("tts_auto_next", true)
        loadChapter(ch)
        index = sentenceIndex.coerceIn(0, maxOf(0, sentences.size - 1))
        message = null
        ensureTts { play() }
    }

    private fun loadChapter(ch: Int) {
        val c = content ?: return
        chapter = ch.coerceIn(0, c.chapterCount - 1)
        chapterCount = c.chapterCount
        chapterTitle = c.chapterTitle(chapter).trim()
        sentences = ReadLogic.splitSentences(c.chapterText(chapter))
        index = 0
    }

    private fun ensureTts(then: () -> Unit) {
        val t = tts
        if (t != null && ttsReady) { applyVoice(); then(); return }
        if (t != null) return
        tts = TextToSpeech(app) { status ->
            main.post {
                ttsReady = status == TextToSpeech.SUCCESS
                if (!ttsReady) { message = "手机没有可用的语音引擎（系统设置 › 文字转语音）"; playing = false; ListenService.refresh(app); return@post }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String) {}
                    override fun onDone(id: String) { main.post { onSentenceDone(id) } }
                    @Deprecated("Deprecated in Java") override fun onError(id: String) { main.post { onSentenceDone(id) } }
                    override fun onError(id: String, code: Int) { main.post { message = "朗读出错（代码 $code），已跳过这一句"; onSentenceDone(id) } }
                })
                applyVoice()
                then()
            }
        }
    }

    private fun locale() = if (cjk) Locale.SIMPLIFIED_CHINESE else Locale.US

    private fun applyVoice() {
        val t = tts ?: return
        val r = t.setLanguage(locale())
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
            message = "手机的语音引擎不支持${if (cjk) "中文" else "英语"}，可在系统设置 › 文字转语音里换一个引擎"
        prefs().getString(voiceKey(), null)?.let { name -> t.voices?.firstOrNull { it.name == name }?.let { t.voice = it } }
        t.setSpeechRate(speed)
    }

    private fun voiceKey() = if (cjk) "tts_voice_zh" else "tts_voice_en"

    /** 当前语言可选的声音（名字 → 是否需要联网）。 */
    fun voices(): List<Voice> {
        val t = tts ?: return emptyList()
        val lang = locale().language
        return runCatching { t.voices?.filter { it.locale.language == lang && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }?.sortedBy { it.name } }.getOrNull() ?: emptyList()
    }

    fun currentVoice(): String? = runCatching { tts?.voice?.name }.getOrNull()

    fun setVoice(name: String) {
        prefs().edit().putString(voiceKey(), name).apply()
        tts?.voices?.firstOrNull { it.name == name }?.let { tts?.voice = it }
        if (playing && mode == Mode.TTS) speakCurrent()
    }

    private fun speakCurrent() {
        val t = tts ?: return
        val s = current ?: run { onChapterEnd(); return }
        gen++
        t.setSpeechRate(speed)
        t.speak(s.text, TextToSpeech.QUEUE_FLUSH, null, "s$gen")
        tick++
        saveProgress()
    }

    private fun onSentenceDone(id: String) {
        if (!playing || mode != Mode.TTS || id != "s$gen") return
        if (sleepAt > 0 && System.currentTimeMillis() >= sleepAt) { sleepAt = 0; pause(); message = "定时到了，已暂停"; return }
        if (index + 1 < sentences.size) { index++; speakCurrent() } else onChapterEnd()
    }

    private fun onChapterEnd() {
        val c = content ?: return
        if (sleepAt == -1L) {
            sleepAt = 0
            if (chapter + 1 < c.chapterCount) { loadChapter(chapter + 1); saveProgress() }
            pause(); message = "本章读完，已按定时暂停"; return
        }
        if (autoNext && chapter + 1 < c.chapterCount) { loadChapter(chapter + 1); if (playing) speakCurrent() }
        else { pause(); message = if (chapter + 1 >= c.chapterCount) "全书读完了" else "本章读完" }
    }

    fun next() {
        if (mode == Mode.AUDIO) { player?.let { it.seekTo((it.currentPosition + 15_000).coerceAtMost(maxOf(0, it.duration))) }; return }
        if (index + 1 < sentences.size) { index++; if (playing) speakCurrent() else { tick++; saveProgress() } } else onChapterEnd()
    }

    fun prev() {
        if (mode == Mode.AUDIO) { player?.let { it.seekTo((it.currentPosition - 15_000).coerceAtLeast(0)) }; return }
        if (index > 0) index-- else if (chapter > 0) { loadChapter(chapter - 1); index = maxOf(0, sentences.size - 1) }
        if (playing) speakCurrent() else { tick++; saveProgress() }
    }

    /** 跳到本章第 i 句（阅读页“从这里听”用）。 */
    fun jumpTo(i: Int) { index = i.coerceIn(0, maxOf(0, sentences.size - 1)); if (playing) speakCurrent() else tick++ }

    // ================= 真人朗读 =================
    fun audioFile(ctx: Context, bookId: String, ab: ReadLogic.AudioBook, i: Int) =
        File(File(ctx.filesDir, "read/audio/$bookId").apply { mkdirs() }, "${ab.id}_$i.mp3")

    fun startAudio(ctx: Context, book: Library.Book, ab: ReadLogic.AudioBook, sectionIndex: Int, startMs: Long = 0) {
        app = ctx.applicationContext
        releaseAll(keepTts = true)
        tts?.stop()
        bookId = book.id; bookTitle = book.title; mode = Mode.AUDIO; audio = ab
        speed = prefs().getFloat("audio_speed", 1f)
        playSection(sectionIndex, startMs)
    }

    private fun playSection(i: Int, startMs: Long) {
        val ab = audio ?: return
        val f = audioFile(app, bookId ?: return, ab, i)
        section = i; chapterTitle = ab.sections.getOrNull(i)?.title ?: ""; chapterCount = ab.sections.size
        if (!f.isFile) { pause(); message = "「$chapterTitle」还没下载到手机，请先在听书面板里下载"; return }
        val p = player ?: ExoPlayer.Builder(app).build().also { pl ->
            pl.setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            pl.setHandleAudioBecomingNoisy(true)
            pl.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        if (sleepAt == -1L) { sleepAt = 0; pause(); message = "本段听完，已按定时暂停"; return }
                        val n = section + 1
                        if (n < (audio?.sections?.size ?: 0)) playSection(n, 0) else { pause(); message = "全部听完了" }
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying || (pl.playWhenReady && pl.playbackState == Player.STATE_BUFFERING); ListenService.refresh(app) }
                override fun onPlayerError(error: PlaybackException) { message = "播放失败：${error.errorCodeName}（文件可能没下载完整，删掉重下试试）" }
            })
            player = pl
        }
        p.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(f)))
        p.prepare()
        if (startMs > 0) p.seekTo(startMs)
        p.setPlaybackSpeed(speed)
        p.playWhenReady = true
        playing = true
        message = null
        ListenService.start(app)
        startTicker()
    }

    private fun startTicker() {
        main.removeCallbacks(ticker); main.post(ticker)
    }

    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (mode != Mode.AUDIO) return
            positionMs = p.currentPosition; durationMs = maxOf(0, p.duration)
            if (sleepAt > 0 && System.currentTimeMillis() >= sleepAt) { sleepAt = 0; pause(); message = "定时到了，已暂停" }
            if (System.currentTimeMillis() / 1000 % 10 == 0L) saveProgress()
            main.postDelayed(this, 1000)
        }
    }

    fun seekAudio(ms: Long) { player?.seekTo(ms) }

    // ================= 通用 =================
    fun play() {
        if (!active) return
        requestFocus()
        playing = true
        if (mode == Mode.TTS) ensureTts { speakCurrent() } else { player?.play(); startTicker() }
        ListenService.start(app)
    }

    fun pause() {
        playing = false
        if (mode == Mode.TTS) { gen++; tts?.stop() } else player?.pause()
        saveProgress()
        abandonFocus()
        if (::app.isInitialized) ListenService.refresh(app)
    }

    fun toggle() { if (playing) pause() else play() }

    fun setSpeed(v: Float) {
        speed = v
        if (mode == Mode.TTS) { prefs().edit().putFloat("tts_speed", v).apply(); if (playing) speakCurrent() }
        else { prefs().edit().putFloat("audio_speed", v).apply(); player?.setPlaybackSpeed(v) }
    }

    fun setAutoNextChapter(v: Boolean) { autoNext = v; prefs().edit().putBoolean("tts_auto_next", v).apply() }

    /** minutes: 0 取消；-1 读完本章；其他为分钟数。 */
    fun setSleep(minutes: Int) { sleepAt = when { minutes == 0 -> 0; minutes < 0 -> -1; else -> System.currentTimeMillis() + minutes * 60_000L } }

    fun stop() {
        if (active) pause()
        releaseAll(keepTts = false)
        bookId = null; audio = null; sentences = emptyList()
        if (::app.isInitialized) ListenService.stop(app)
    }

    private fun releaseAll(keepTts: Boolean) {
        main.removeCallbacks(ticker)
        player?.release(); player = null
        content?.close(); content = null
        if (!keepTts) { tts?.shutdown(); tts = null; ttsReady = false }
    }

    private fun saveProgress() {
        val id = bookId ?: return
        if (!::app.isInitialized) return
        if (mode == Mode.TTS) Library.update(app, id) { it.copy(listenChapter = chapter, listenSentence = index, listenAt = System.currentTimeMillis()) }
        else { val ab = audio ?: return; Library.update(app, id) { it.copy(audioId = ab.id, audioSection = section, audioMs = player?.currentPosition ?: 0, listenAt = System.currentTimeMillis()) } }
    }

    // 音频焦点：来电、别的 App 播放时自动暂停（机器朗读需要自己处理，ExoPlayer 自带）
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) main.post { if (mode == Mode.TTS && playing) pause() }
    }

    private fun requestFocus() {
        if (mode != Mode.TTS || !::app.isInitialized) return
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(focusListener).build()
        focus = req
        am.requestAudioFocus(req)
    }

    private fun abandonFocus() {
        val f = focus ?: return
        if (!::app.isInitialized) return
        (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(f)
        focus = null
    }
}
