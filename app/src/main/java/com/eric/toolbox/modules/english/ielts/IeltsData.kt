package com.eric.toolbox.modules.english.ielts

/**
 * 雅思场景学习：对话 + 场景词 + 功能语块 + 追问练习。
 *
 * 为什么按场景组织，而不是再给一份词表：离线词典里已经有 5038 个雅思标签词，
 * 缺的从来不是词，而是「这些词在什么场合、跟谁、怎么说」。场景把词、搭配、
 * 语块和对话流绑在一起，学完能直接开口；场景词还能一键进背词队列（SM-2），
 * 第二天背单词就考它，形成闭环。
 *
 * 每个场景对应雅思口语的三类题：
 * - Part 1 生活问答（生活类场景）
 * - Part 3 抽象讨论（职场/学术类场景）
 * - Part 2 独白（学术类场景里的 presentation / 导师会面）
 *
 * 内容按 band 7-8 的口语写法编写：用缩写、填充词和口语连接词，
 * 不是书面作文腔。tip 字段专门标「中式说法 → 地道说法」。
 */
enum class Cat(val key: String, val zh: String, val en: String) {
    LIFE("life", "生活", "Daily life"),
    WORK("work", "职场", "Workplace"),
    ACADEMIC("academic", "学术", "Academic"),
}

/** 一组对话：A = 对方（店员/领导/导师），B = 你（Eric）。 */
data class Ln(val a: String, val aZh: String, val b: String, val bZh: String, val tip: String = "")

/** 场景词：释义给的是这个场景里的用法，不是词典释义；coll 是固定搭配。 */
data class Wd(val en: String, val zh: String, val coll: String, val ex: String, val exZh: String, val tip: String = "")

/** 功能语块：表达意图 → 英文 → 使用场景。雅思连贯性评分看的就是这些。 */
data class Ck(val zh: String, val en: String, val use: String)

/** 追问练习：part=1/3；skeleton 是回答骨架（关键词式），不是让你背全文。 */
data class Qs(val q: String, val qZh: String, val part: Int, val skeleton: String)

data class Scene(
    val id: String,
    val zh: String,
    val en: String,
    val cat: Cat,
    val goal: String,
    val lines: List<Ln>,
    val words: List<Wd>,
    val chunks: List<Ck>,
    val asks: List<Qs>,
)

object IeltsData {
    /**
     * 词书 id：场景词统一进这个 book，背单词队列和词书页都用它。
     * 不能叫 "ielts"——那个 id 已经被 EnglishStore.EXAMS 里的「雅思标签词表」占了
     * （走 Dict.wordsByTag，5038 个词），两个都用它会串到词典里。
     */
    const val BOOK = "ielts_scene"

    val scenes: List<Scene> = IeltsLife.list + IeltsWork.list + IeltsAcademic.list

    fun scene(id: String): Scene? = scenes.firstOrNull { it.id == id }

    fun byCat(cat: Cat): List<Scene> = scenes.filter { it.cat == cat }

    /** 所有场景词（同一个词在多个场景出现只留一条，例句取第一次出现的场景） */
    val words: List<Wd> = scenes.flatMap { s -> s.words.map { it to s } }
        .distinctBy { it.first.en.lowercase() }
        .map { it.first }

    fun word(key: String): Wd? = words.firstOrNull { it.en.equals(key, ignoreCase = true) }

    /** 背词队列用的键（小写，与 EnglishStore 的约定一致） */
    fun keys(): List<String> = words.map { it.en.lowercase() }

    fun wordCount(): Int = words.size
}
