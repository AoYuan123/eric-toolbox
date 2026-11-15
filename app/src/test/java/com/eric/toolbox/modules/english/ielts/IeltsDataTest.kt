package com.eric.toolbox.modules.english.ielts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 雅思场景数据体检。30 个场景 × (4 句对话 + 10 个词 + 5 条语块 + 3 个追问)，
 * 全是手写常量，漏一个字段、场景 id 打错、场景内重复一个词，编译期都看不出来，靠这条测试兜住。
 *
 * 守的三件事：
 * 1. 词书 id 不能叫 "ielts"——那个 id 是离线词典的雅思标签词表（5038 个词）在用
 * 2. 每个场景结构完整（界面按 4/10/5/3 渲染，缺一条就是空白块）
 * 3. 场景内不重复词（跨场景可以重复，比如 deposit 在租房是押金、在银行是存款，
 *    各场景的词汇页各显示各的；背词卡片按词去重，只留第一次出现的释义）
 */
class IeltsDataTest {

    @Test
    fun bookIdDoesNotCollideWithExamTag() {
        assertNotEquals("ielts", IeltsData.BOOK)
    }

    @Test
    fun sceneIdsAreUnique() {
        val ids = IeltsData.scenes.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun hasThirtyScenesTenPerCategory() {
        assertEquals(30, IeltsData.scenes.size)
        Cat.values().forEach { c -> assertEquals(10, IeltsData.byCat(c).size) }
    }

    @Test
    fun everySceneHasFourLinesTenWordsFiveChunksThreeAsks() {
        IeltsData.scenes.forEach { s ->
            assertEquals("${s.id} lines", 4, s.lines.size)
            assertEquals("${s.id} words", 10, s.words.size)
            assertEquals("${s.id} chunks", 5, s.chunks.size)
            assertEquals("${s.id} asks", 3, s.asks.size)
        }
    }

    @Test
    fun sceneWordsAreUniqueWithinScene() {
        IeltsData.scenes.forEach { s ->
            val keys = s.words.map { it.en.lowercase() }
            assertEquals("${s.id} 场景内重复词", keys.size, keys.distinct().size)
        }
    }

    @Test
    fun reviewKeysAreDedupedAcrossScenes() {
        // 卡片 id 是 "book:词"，跨场景重复的词只能出一条，否则背单词会出两张卡
        val keys = IeltsData.keys()
        assertEquals(IeltsData.words.size, keys.size)
        assertEquals(keys.size, keys.distinct().size)
        assertTrue("场景词太少，检查是不是有场景没接进 IeltsData.scenes", keys.size >= 280)
    }

    @Test
    fun everyWordHasCollocationAndExample() {
        IeltsData.words.forEach { w ->
            assertTrue("${w.en} 缺英文例句", w.ex.isNotBlank())
            assertTrue("${w.en} 缺中文例句", w.exZh.isNotBlank())
            assertTrue("${w.en} 缺中文释义", w.zh.isNotBlank())
            assertTrue("${w.en} 缺固定搭配", w.coll.isNotBlank())
            // 例句必须是英文（中文混进例句字段会念出来很奇怪）
            assertTrue("${w.en} 的例句里混了中文：${w.ex}", !w.ex.any { it.code in 0x4E00..0x9FFF })
            // 中文释义必须真的是中文
            assertTrue("${w.en} 的中文释义里没有中文：${w.zh}", w.zh.any { it.code in 0x4E00..0x9FFF })
        }
    }

    @Test
    fun everyLineHasBothSidesAndTranslation() {
        IeltsData.scenes.forEach { s ->
            s.lines.forEachIndexed { i, l ->
                assertTrue("${s.id} 第 ${i + 1} 句 A 缺英文", l.a.isNotBlank())
                assertTrue("${s.id} 第 ${i + 1} 句 A 缺中文", l.aZh.isNotBlank())
                assertTrue("${s.id} 第 ${i + 1} 句 B 缺英文", l.b.isNotBlank())
                assertTrue("${s.id} 第 ${i + 1} 句 B 缺中文", l.bZh.isNotBlank())
                assertTrue("${s.id} 第 ${i + 1} 句 A 混了中文", !l.a.any { it.code in 0x4E00..0x9FFF })
                assertTrue("${s.id} 第 ${i + 1} 句 B 混了中文", !l.b.any { it.code in 0x4E00..0x9FFF })
            }
        }
    }

    @Test
    fun everyChunkHasIntentAndUsage() {
        IeltsData.scenes.forEach { s ->
            s.chunks.forEach { c ->
                assertTrue("${s.id} 语块缺中文", c.zh.isNotBlank())
                assertTrue("${s.id} 语块缺英文", c.en.isNotBlank())
                assertTrue("${s.id} 语块「${c.en}」缺使用说明", c.use.isNotBlank())
            }
        }
    }

    @Test
    fun askPartsAreOneTwoOrThree() {
        IeltsData.scenes.forEach { s ->
            s.asks.forEach { q ->
                assertTrue("${s.id} 的 part=${q.part} 不合法", q.part in 1..3)
                assertTrue("${s.id} 追问缺题目", q.q.isNotBlank() && q.qZh.isNotBlank())
                assertTrue("${s.id} 追问缺回答骨架", q.skeleton.isNotBlank())
            }
        }
    }

    @Test
    fun sceneLookupByIdWorks() {
        // 界面路由是 "s:场景id"，查不到会直接退回去
        IeltsData.scenes.forEach {
            assertTrue(it.id, IeltsData.scene(it.id) === it)
            assertEquals(it.cat, IeltsData.scene(it.id)!!.cat)
        }
        assertNull(IeltsData.scene("nope"))
    }

    @Test
    fun wordLookupIgnoresCase() {
        val w = IeltsData.words.first()
        assertEquals(w.en, IeltsData.word(w.en.uppercase())?.en)
        assertNull(IeltsData.word("zzzz-not-a-word"))
    }
}
