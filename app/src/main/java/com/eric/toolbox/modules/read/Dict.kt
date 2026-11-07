package com.eric.toolbox.modules.read

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * 离线英汉词典：ECDICT（github.com/skywind3000/ECDICT，MIT 许可，许可证见 assets/licenses/ECDICT-LICENSE.txt）精简版。
 * 11-07 从 ecdict.csv 挑出 BNC/当代语料词频前 6 万、柯林斯星级、牛津 3000 或考试大纲（中考到 GRE）里的单词：
 * 58,226 词 + 39,047 个变形→原形对照，SQLite 5.4 MB，gzip 后 2.5 MB 放在 assets，第一次查词时解压到 App 目录。
 */
object Dict {
    data class Entry(val word: String, val phonetic: String, val meaning: String, val lemmaOf: String?, val tags: String)

    private const val ASSET = "ecdict-mini.db.gz"
    private const val VERSION = 1
    @Volatile private var db: SQLiteDatabase? = null

    @Synchronized
    private fun open(ctx: Context): SQLiteDatabase {
        db?.let { return it }
        val f = File(ctx.filesDir, "read/ecdict-v$VERSION.db")
        if (!f.isFile) {
            f.parentFile?.mkdirs()
            f.parentFile?.listFiles { x -> x.name.startsWith("ecdict-") }?.forEach { it.delete() }
            val tmp = File(f.parentFile, "ecdict.tmp")
            // 打包时 AAPT 会把 assets 里的 .gz 自动解压并去掉后缀（11-07 v1.0.35 实测 APK 里是 assets/ecdict-mini.db），两种都兼容
            val input = runCatching { ctx.assets.open("ecdict-mini.db") }.getOrNull() ?: GZIPInputStream(ctx.assets.open(ASSET))
            input.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            if (!tmp.renameTo(f)) throw java.io.IOException("词典解压失败")
        }
        return SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY).also { db = it }
    }

    private fun row(d: SQLiteDatabase, w: String): Array<String>? =
        d.rawQuery("select o,p,t,g from d where w=?", arrayOf(w)).use { c -> if (c.moveToFirst()) Array(4) { c.getString(it) ?: "" } else null }

    private fun lemma(d: SQLiteDatabase, w: String): String? =
        d.rawQuery("select w from x where f=?", arrayOf(w)).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    /** 查词：先查原词，再查词形对照表，最后按规则还原（-ed/-ing/-s 等）。查不到返回 null。 */
    fun lookup(ctx: Context, raw: String): Entry? {
        val w = ReadLogic.cleanWord(raw) ?: return null
        val d = open(ctx)
        row(d, w)?.let { r -> return Entry(r[0], r[1], r[2], lemma(d, w), r[3]) }
        lemma(d, w)?.let { l -> row(d, l)?.let { r -> return Entry(r[0], r[1], r[2], l, r[3]) } }
        for (c in ReadLogic.stemCandidates(w).drop(1)) {
            row(d, c)?.let { r -> return Entry(r[0], r[1], r[2], c, r[3]) }
            lemma(d, c)?.let { l -> row(d, l)?.let { r -> return Entry(r[0], r[1], r[2], l, r[3]) } }
        }
        return null
    }

    /** ECDICT 的考试标签转成中文（zk 中考、gk 高考、cet4、cet6、ky 考研、toefl、ielts、gre）。 */
    fun tagText(tags: String): String = tags.split(' ').mapNotNull {
        when (it) { "zk" -> "中考"; "gk" -> "高考"; "cet4" -> "四级"; "cet6" -> "六级"; "ky" -> "考研"; "toefl" -> "托福"; "ielts" -> "雅思"; "gre" -> "GRE"; else -> null }
    }.joinToString(" · ")
}
