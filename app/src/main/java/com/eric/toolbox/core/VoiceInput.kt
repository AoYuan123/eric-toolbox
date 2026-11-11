package com.eric.toolbox.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 系统语音识别的统一封装（AI 管家、练口语、学发音共用）。
 *
 * hold=true（按住说话）：系统识别遇到停顿会自己结束一段。这里只把这段攒起来、马上接着听，
 * 直到松手（stop）才把全部内容一次交出——11-10 真机反馈：按住不到 2 秒就被发出去了。
 * hold=false（点一下说一句）：说完自动结束。
 *
 * 英文结果如果全是大写（小米真机实测），用 [fixCase] 改成正常大小写。
 */
class VoiceInput(
    private val ctx: Context,
    private val lang: String,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (Int) -> Unit,
) {
    companion object {
        /** 按住期间一个字都没识别出来。 */
        const val NOTHING = -1

        fun available(ctx: Context) = SpeechRecognizer.isRecognitionAvailable(ctx)

        /** 只在一个小写字母都没有时才改：先全变小写，再把句首、独立的 i（含 I'm、I'll）改回大写。 */
        fun fixCase(text: String): String {
            val t = text.trim()
            if (t.count { it.isLetter() } < 4 || t.any { it.isLetter() && it.isLowerCase() }) return t
            if (t.any { it.code > 0x2E7F }) return t   // 含中文等，不动
            val sb = StringBuilder(t.lowercase())
            var start = true
            for (k in sb.indices) {
                val ch = sb[k]
                if (start && ch.isLetter()) { sb.setCharAt(k, ch.uppercaseChar()); start = false }
                if (ch == '.' || ch == '?' || ch == '!') start = true
            }
            return Regex("""\bi\b""").replace(sb.toString(), "I")
        }
    }

    private var rec: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())
    private var hold = false
    private var holding = false
    private var finished = true
    private var session = 0
    private val done = StringBuilder()
    private var last = ""
    private var errs = 0

    private fun norm(t: String) = if (lang.startsWith("en")) fixCase(t) else t.trim()
    private fun joined(extra: String = ""): String {
        val sep = if (lang.startsWith("zh")) "" else " "
        return listOf(done.toString().trim(), extra.trim()).filter { it.isNotBlank() }.joinToString(sep)
    }

    private fun deliver(text: String) {
        if (finished) return
        finished = true; session++
        main.removeCallbacksAndMessages(null)
        val t = norm(text)
        if (t.isNotBlank()) onFinal(t) else onError(NOTHING)
    }

    fun start(hold: Boolean = false): Boolean {
        if (!available(ctx)) return false
        main.removeCallbacksAndMessages(null)
        this.hold = hold; holding = hold; finished = false
        done.setLength(0); last = ""; errs = 0
        listen()
        return true
    }

    private fun listen() {
        val r = rec ?: SpeechRecognizer.createSpeechRecognizer(ctx).also { rec = it }
        val my = ++session
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onPartialResults(b: Bundle?) {
                if (my != session) return
                errs = 0
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { last = it; onPartial(norm(joined(it))) }
            }
            override fun onResults(b: Bundle?) {
                if (my != session) return
                segmentEnded(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().ifBlank { last })
            }
            override fun onError(e: Int) {
                if (my != session) return
                if (holding) {
                    // 还按着：没听到、超时、引擎忙都不算失败，歇一下接着听；连续出错 6 次才放弃
                    errs++
                    if (errs <= 6) { segmentEnded(last, retryDelay = 300); return }
                    holding = false
                }
                if (last.isNotBlank() || done.isNotBlank()) segmentEnded(last)
                else { finished = true; session++; main.removeCallbacksAndMessages(null); onError(e) }
            }
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // 有的识别引擎认这几个参数（停顿 3 秒才算说完）；不认也没关系，按住时会自动接着听
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L))
    }

    private fun segmentEnded(seg: String, retryDelay: Long = 0) {
        if (seg.isNotBlank()) { done.append(' ').append(seg); errs = 0 }
        last = ""
        if (holding) main.postDelayed({ if (holding && !finished) listen() }, retryDelay)   // 还按着：接着听
        else deliver(joined())
    }

    /** 松手：停止收音，等最后一段结果（最多 2.5 秒），然后整段交出。 */
    fun stop() {
        if (finished) return
        holding = false
        rec?.stopListening()
        main.postDelayed({ deliver(joined(last)) }, 2500)
    }

    fun destroy() { holding = false; finished = true; session++; main.removeCallbacksAndMessages(null); rec?.destroy(); rec = null }
}
