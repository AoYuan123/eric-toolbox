package com.eric.toolbox.modules.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * 实况照片的字节解析。
 *
 * 这几条测试是拿临时文件真造一个"JPEG + XMP + MP4"的尾巴来验的——
 * 因为它错了不会报错，只会让所有动态照片悄悄变成静态图，那种 bug 没人会发现。
 */
class MotionPhotoTest {

    private val videoLen = 2000

    /** 造一个：头部（含 XMP 文本）+ 填充 + 尾部 MP4。 */
    private fun build(headText: String, withVideo: Boolean = true): File {
        val f = File.createTempFile("motion", ".jpg")
        val videoLen = if (withVideo) this.videoLen else 0
        val pad = 300_000 - headText.length - videoLen
        f.outputStream().use { os ->
            os.write(headText.toByteArray(Charsets.ISO_8859_1))
            os.write(ByteArray(pad) { 0x41 })
            if (withVideo) os.write(mp4Tail(videoLen))
        }
        f.deleteOnExit()
        return f
    }

    /** 一个最小可识别的 mp4 开头：4 字节盒子长度 + "ftyp"。 */
    private fun mp4Tail(len: Int): ByteArray = ByteArray(len).apply {
        this[3] = 8.toByte()
        "ftyp".toByteArray(Charsets.ISO_8859_1).copyInto(this, 4)
    }

    @Test
    fun `小米旧标准 MicroVideoOffset`() {
        val head = "<x:xmpmeta><rdf:Description GCamera:MicroVideo=\"1\" " +
            "GCamera:MicroVideoOffset=\"$videoLen\" /></x:xmpmeta>"
        val span = MotionPhoto.probe(build(head)) ?: throw AssertionError("应该认出是实况照片")
        assertEquals(videoLen.toLong(), span.length)
        assertEquals(300_000L - videoLen, span.start)
    }

    @Test
    fun `Google 新标准 Item Length`() {
        val head = "<rdf:li><Container:Item Item:Mime=\"image/jpeg\" Item:Semantic=\"Primary\"/></rdf:li>" +
            "<rdf:li><Container:Item Item:Mime=\"video/mp4\" Item:Semantic=\"MotionPhoto\" " +
            "Item:Length=\"$videoLen\"/></rdf:li>"
        val span = MotionPhoto.probe(build(head)) ?: throw AssertionError("应该认出是实况照片")
        assertEquals(videoLen.toLong(), span.length)
    }

    @Test
    fun `元数据被抹掉时靠 ftyp 兜底`() {
        val span = MotionPhoto.probe(build("<x:xmpmeta>什么都没有</x:xmpmeta>"))
            ?: throw AssertionError("扫 ftyp 应该也能找出来")
        assertEquals(videoLen.toLong(), span.length)
    }

    @Test
    fun `普通照片不会被误判成实况`() {
        assertNull(MotionPhoto.probe(build("<x:xmpmeta>普通照片</x:xmpmeta>", withVideo = false)))
    }

    @Test
    fun `候选长度对不上真实位置时不算数`() {
        // 元数据写了个离谱的值，倒推出的位置不是 ftyp，应该当普通照片
        val head = "GCamera:MicroVideoOffset=\"123\""
        val f = build(head, withVideo = false)
        assertNull(MotionPhoto.probe(f))
    }

    @Test
    fun `太小的文件直接跳过`() {
        val f = File.createTempFile("tiny", ".jpg")
        f.writeBytes(ByteArray(1000))
        f.deleteOnExit()
        assertNull(MotionPhoto.probe(f))
    }
}
