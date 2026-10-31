package com.eric.toolbox.modules.pan.core

/**
 * 0 结果时的关键词变体。
 * 中文片名常有多种写法（有没有空格、全角半角、带不带「全集」「4K」），
 * 换一种写法往往就能搜到。
 */
object KeywordVariants {

    private val DECOR = Regex("""(全集|合集|4k|1080p|720p|高清|国粤|双语)""", RegexOption.IGNORE_CASE)

    /** 最多返回 3 个变体，按优先级排列；命中即停，不要一次全跑 */
    fun build(kw: String?): List<String> {
        val base = (kw ?: "").trim()
        if (base.isEmpty()) return emptyList()

        val out = LinkedHashSet<String>()

        // 1. 去掉所有空格
        val noSpace = base.replace(Regex("""\s+"""), "")
        if (noSpace.isNotEmpty() && noSpace != base) out.add(noSpace)

        // 2. 全角转半角
        val half = toHalfWidth(noSpace)
        if (half.isNotEmpty() && half != base && half != noSpace) out.add(half)

        // 3. 去掉修饰词
        val bare = DECOR.replace(base, "").trim()
        if (bare.isNotEmpty() && bare != base) out.add(bare)

        return out.take(3)
    }

    private fun toHalfWidth(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when {
                    ch == '\u3000' -> ' '
                    ch.code in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()
                    else -> ch
                },
            )
        }
        return sb.toString()
    }
}
