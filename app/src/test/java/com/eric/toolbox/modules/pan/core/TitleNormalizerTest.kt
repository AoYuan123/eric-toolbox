package com.eric.toolbox.modules.pan.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标题归一化的回归测试。
 * 这几个用例是聚合准不准的底线，改动正则后必须先跑通再提交。
 */
class TitleNormalizerTest {

    @Test
    fun 去掉渠道标签与画质标签() {
        assertEquals("星际穿越", TitleNormalizer.normalize("【夸克网盘】星际穿越 4K.mp4"))
        assertEquals("星际穿越", TitleNormalizer.normalize("星际穿越 1080p 高清 中字.mkv"))
    }

    @Test
    fun 保留年份() {
        assertEquals("星际穿越 (2014)", TitleNormalizer.normalize("星际穿越 (2014) 1080p"))
    }

    @Test
    fun 保留季数并删掉更新进度() {
        val out = TitleNormalizer.normalize("庆余年 第一季 更新至10集")
        assertTrue("季数应保留：$out", out.contains("第一季"))
        assertTrue("更新进度应删除：$out", !out.contains("更新至"))
    }

    @Test
    fun 英文噪词不误伤片名里的字符() {
        // 有前后瞻才不会把 4KIDS 里的 4K 吃掉
        val out = TitleNormalizer.normalize("4KIDS.2019.1080p")
        assertTrue("片名里的 4K 不该被删：$out", out.contains("4KIDS"))
    }

    @Test
    fun 全角括号与中文标签也能处理() {
        // 全角括号会先转半角，所以年份紧跟片名，中间没有空格
        assertEquals("流浪地球2(2023)", TitleNormalizer.normalize("【4K超清】流浪地球2（2023）"))
    }
}
