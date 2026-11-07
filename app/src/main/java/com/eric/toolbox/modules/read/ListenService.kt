package com.eric.toolbox.modules.read

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.eric.toolbox.MainActivity

/**
 * 听书前台服务（mediaPlayback 类型）：锁屏、切到别的 App 时继续播放；通知栏和锁屏有 上一句/暂停/下一句；
 * 耳机按键、蓝牙按键通过 MediaSession 控制；拔耳机自动暂停。暂停时通知可以划掉，停止后服务退出。
 */
class ListenService : Service() {
    private var session: MediaSessionCompat? = null
    private var noisyRegistered = false
    private var fgStarted = false
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { if (i.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && Listen.mode == Listen.Mode.TTS) Listen.pause() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null) nm.createNotificationChannel(NotificationChannel(CH, "听书", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        session = MediaSessionCompat(this, "EricToolboxListen").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = Listen.play()
                override fun onPause() = Listen.pause()
                override fun onSkipToNext() = Listen.next()
                override fun onSkipToPrevious() = Listen.prev()
                override fun onStop() = Listen.stop()
            })
            isActive = true
        }
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        noisyRegistered = true
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACT_TOGGLE -> Listen.toggle()
            ACT_NEXT -> Listen.next()
            ACT_PREV -> Listen.prev()
            ACT_STOP -> { Listen.stop(); return START_NOT_STICKY }
        }
        update()
        return START_NOT_STICKY
    }

    fun update() {
        if (!Listen.active) {
            if (!fgStarted) { // 被系统用 startForegroundService 拉起但已无内容：先满足前台要求再退出
                val n0 = NotificationCompat.Builder(this, CH).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("听书").build()
                if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n0, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(ID, n0)
                fgStarted = true
            }
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }
        val s = session ?: return
        val state = if (Listen.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        s.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, Listen.speed).build(),
        )
        s.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, Listen.bookTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, Listen.chapterTitle)
            .build())
        val n = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(Listen.bookTitle)
            .setContentText((if (Listen.mode == Listen.Mode.TTS) "机器朗读 · " else "真人朗读 · ") + Listen.chapterTitle)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_MODULE, ReadModule.id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setDeleteIntent(pi(ACT_STOP, 4))
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, if (Listen.mode == Listen.Mode.TTS) "上一句" else "后退15秒", pi(ACT_PREV, 1))
            .addAction(if (Listen.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (Listen.playing) "暂停" else "播放", pi(ACT_TOGGLE, 2))
            .addAction(android.R.drawable.ic_media_next, if (Listen.mode == Listen.Mode.TTS) "下一句" else "前进15秒", pi(ACT_NEXT, 3))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(s.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .setOngoing(Listen.playing)
            .build()
        if (Listen.playing || !fgStarted) {
            // startForegroundService 之后必须马上 startForeground，否则系统会结束 App
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(ID, n)
            fgStarted = true
        }
        if (!Listen.playing) {
            // 暂停时退出前台，通知可划掉（划掉 = 停止听书）
            stopForeground(STOP_FOREGROUND_DETACH)
            getSystemService(NotificationManager::class.java).notify(ID, n)
        }
    }

    private fun pi(action: String, code: Int) =
        PendingIntent.getService(this, code, Intent(this, ListenService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    override fun onDestroy() {
        instance = null
        if (noisyRegistered) runCatching { unregisterReceiver(noisy) }
        session?.release(); session = null
        getSystemService(NotificationManager::class.java).cancel(ID)
        super.onDestroy()
    }

    companion object {
        private const val CH = "listen"
        private const val ID = 7
        const val ACT_TOGGLE = "toggle"
        const val ACT_NEXT = "next"
        const val ACT_PREV = "prev"
        const val ACT_STOP = "stop"
        @Volatile private var instance: ListenService? = null

        /** 开始播放时调用（必须在 App 前台时，否则系统不允许启动前台服务）。 */
        fun start(ctx: Context) {
            val i = instance
            if (i != null) { i.update(); return }
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, ListenService::class.java)) }
                .onFailure { Listen.message = "后台播放启动失败：${it.message}（App 在前台时仍可听）" }
        }

        fun refresh(ctx: Context) { instance?.update() }

        fun stop(ctx: Context) { instance?.let { it.stopForeground(STOP_FOREGROUND_REMOVE); it.stopSelf() } }
    }
}
