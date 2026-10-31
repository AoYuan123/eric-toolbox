package com.eric.toolbox.modules.pan.verify

import com.eric.toolbox.modules.pan.core.CloudType
import com.eric.toolbox.modules.pan.core.Http
import com.eric.toolbox.modules.pan.core.HttpException
import com.eric.toolbox.modules.pan.model.VerifyState
import org.json.JSONObject

/**
 * 链接存活核验。
 *
 * 只做「这条分享还在不在」，不证明里面是什么内容。
 * 八类网盘有匿名接口可以查，其余类型（迅雷/移动/PikPak/磁力/电驴）
 * 一律返回 UNCHECKED，如实展示「未核验」，不要假装验证过。
 *
 * 重要原则：
 *   - 检测异常（网络失败、接口 500）返回 UNCHECKED，不能算「失效」
 *   - 只有拿到明确失效信号才返回 BAD
 */
object LinkVerifier {

    data class Verdict(val state: VerifyState, val message: String)

    suspend fun verify(url: String, cloud: String? = null, password: String = ""): Verdict {
        val u = url.trim()
        if (u.isEmpty()) return Verdict(VerifyState.UNCHECKED, "空链接")
        val type = CloudType.canon(cloud).let {
            if (it == "others" || it.isEmpty()) CloudType.fromUrl(u) else it
        }
        return try {
            when (type) {
                "quark" -> quark(u, password)
                "aliyun" -> aliyun(u)
                "115" -> n115(u, password)
                "123" -> n123(u)
                "tianyi" -> tianyi(u)
                "baidu" -> baidu(u)
                "lanzou" -> lanzou(u)
                "uc" -> uc(u)
                else -> Verdict(VerifyState.UNCHECKED, "该类型暂无匿名检测")
            }
        } catch (e: HttpException) {
            Verdict(VerifyState.UNCHECKED, "检测异常 HTTP ${e.status}")
        } catch (e: Exception) {
            Verdict(VerifyState.UNCHECKED, "检测异常：${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ------------------------------------------------------------ 各网盘

    private suspend fun quark(url: String, password: String): Verdict {
        val pid = grab("""pan\.quark\.cn/s/([a-f0-9]+)""", url)
        if (pid.isEmpty()) return Verdict(VerifyState.UNCHECKED, "未识别夸克分享 id")

        val body = JSONObject().apply {
            put("pwd_id", pid)
            put("passcode", password)
        }
        val json = Http.postJson(
            "https://drive.quark.cn/1/clouddrive/share/sharepage/token?pr=ucpro&fr=pc",
            body, 25_000,
            mapOf("Referer" to "https://pan.quark.cn/"),
        )
        val msg = json.optString("message")
        val stoken = json.optJSONObject("data")?.optString("stoken").orEmpty()
        return when {
            stoken.isNotEmpty() -> Verdict(VerifyState.OK, "夸克接口实测有效")
            msg.contains("提取码") || msg.contains("访问码") -> Verdict(VerifyState.LOCKED, msg)
            msg.contains("不存在") || msg.contains("已失效") ||
                msg.contains("已取消") || msg.contains("删除") -> Verdict(VerifyState.BAD, msg)
            else -> Verdict(VerifyState.UNCHECKED, msg.ifBlank { "未取得 stoken" })
        }
    }

    private suspend fun aliyun(url: String): Verdict {
        val sid = grab("""(?:alipan\.com|aliyundrive\.com)/s/([A-Za-z0-9]+)""", url)
        if (sid.isEmpty()) return Verdict(VerifyState.UNCHECKED, "未识别阿里分享 id")

        val json = Http.postJson(
            "https://api.aliyundrive.com/adrive/v3/share_link/get_share_by_anonymous",
            JSONObject().put("share_id", sid), 25_000,
            mapOf("Referer" to "https://www.aliyundrive.com/"),
        )
        val code = json.optString("code")
        return when {
            code.contains("ShareLink") -> Verdict(VerifyState.BAD, "分享不存在或已取消")
            json.optBoolean("has_pwd", false) -> Verdict(VerifyState.LOCKED, "需要提取码")
            (json.optJSONArray("file_infos")?.length() ?: 0) > 0 ->
                Verdict(VerifyState.OK, "阿里接口实测有效")
            else -> Verdict(VerifyState.UNCHECKED, "接口返回无法判定")
        }
    }

    private suspend fun n115(url: String, password: String): Verdict {
        val code = grab("""(?:115\.com|115cdn\.com)/s/([A-Za-z0-9]+)""", url)
        if (code.isEmpty()) return Verdict(VerifyState.UNCHECKED, "未识别 115 分享码")
        val rc = grab("""[?&]password=([A-Za-z0-9]+)""", url).ifBlank { password }

        val json = Http.getJson(
            "https://webapi.115.com/share/snap?share_code=$code&receive_code=$rc", 20_000,
        )
        if (json.optBoolean("state", false)) return Verdict(VerifyState.OK, "115 接口实测有效")
        val err = json.optString("error")
        return if (err.contains("访问码") || err.contains("提取码")) {
            Verdict(VerifyState.LOCKED, err)
        } else {
            Verdict(VerifyState.BAD, err.ifBlank { "接口判定不可用" })
        }
    }

    private suspend fun n123(url: String): Verdict {
        val key = grab("""123pan\.com/s/([A-Za-z0-9]+)""", url)
        if (key.isEmpty()) return Verdict(VerifyState.UNCHECKED, "未识别 123 分享 key")

        val json = Http.getJson("https://www.123pan.com/api/share/info?shareKey=$key", 20_000)
        if (json.optString("code") != "0") {
            val msg = json.optString("message").ifBlank { json.optString("msg") }
            return if (msg.contains("密码") || msg.contains("提取")) {
                Verdict(VerifyState.LOCKED, msg)
            } else {
                Verdict(VerifyState.BAD, msg.ifBlank { "接口判定不可用" })
            }
        }
        val inner = json.optJSONObject("data")?.optString("Code")
        return if (inner != null && inner != "" && inner != "0") {
            Verdict(VerifyState.BAD, "分享已失效")
        } else {
            Verdict(VerifyState.OK, "123 接口实测有效")
        }
    }

    private suspend fun tianyi(url: String): Verdict {
        val code = grab("""cloud\.189\.cn/(?:t/|web/share\?code=)([A-Za-z0-9]+)""", url)
        if (code.isEmpty()) return Verdict(VerifyState.UNCHECKED, "未识别天翼分享码")

        val json = Http.postJson(
            "https://cloud.189.cn/api/open/share/getShareInfoByCodeV2",
            JSONObject().put("shareCode", code), 25_000,
        )
        val blob = json.toString()
        return when {
            blob.contains("ShareExpiredError") || blob.contains("SHARE_EXPIRED") || blob.contains("已失效") ->
                Verdict(VerifyState.BAD, "分享已失效")
            json.has("fileInfos") || json.has("shareInfo") ->
                Verdict(VerifyState.OK, "天翼接口实测有效")
            else -> Verdict(VerifyState.UNCHECKED, "接口返回无法判定")
        }
    }

    private suspend fun baidu(url: String): Verdict {
        if (grab("""pan\.baidu\.com/s/([A-Za-z0-9_\-]+)""", url).isEmpty()) {
            return Verdict(VerifyState.UNCHECKED, "未识别百度分享 id")
        }
        val page = Http.getText(url, 25_000)
        return when {
            page.contains("分享的文件已经被取消") || page.contains("分享已过期") ||
                page.contains("你访问的页面不存在") -> Verdict(VerifyState.BAD, "页面判定失效")
            page.contains("请输入提取码") || page.contains("请输入提取密码") ->
                Verdict(VerifyState.LOCKED, "需要提取码")
            page.contains("分享") && page.contains("网盘") -> Verdict(VerifyState.OK, "页面判定可访问")
            else -> Verdict(VerifyState.UNCHECKED, "页面无法明确判定")
        }
    }

    private suspend fun lanzou(url: String): Verdict {
        val page = Http.getText(url, 20_000)
        return if (page.contains("文件取消保存") || page.contains("文件不存在") || page.contains("来晚一步")) {
            Verdict(VerifyState.BAD, "文件已失效")
        } else {
            Verdict(VerifyState.OK, "页面判定可访问")
        }
    }

    private suspend fun uc(url: String): Verdict {
        val page = Http.getText(url, 25_000)
        return when {
            page.contains("分享的内容太火爆") || page.contains("已失效") ||
                page.contains("已经被取消") || page.contains("链接不存在") ->
                Verdict(VerifyState.BAD, "分享失效")
            page.contains("提取码") || page.contains("访问码") -> Verdict(VerifyState.LOCKED, "需要提取码")
            else -> Verdict(VerifyState.UNCHECKED, "页面无法明确判定")
        }
    }

    private fun grab(pattern: String, text: String): String =
        Regex(pattern, RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1).orEmpty()

    // ------------------------------------------------------------ 状态机

    /**
     * 四级状态机的判定逻辑（纯函数，持久化交给调用方）。
     *
     * 规则：
     *   - 连续 2 次「失效」才升级为 BAD，单次失败只算 SUSPECT，防误杀
     *   - UNCHECKED（检测异常）不累计失败次数
     *   - 一次成功立刻清零
     *
     * @param prev 上一次的记录
     * @param now 本次判定结果
     */
    fun nextRecord(prev: VerifyRecord?, now: Verdict, nowMs: Long = System.currentTimeMillis()): VerifyRecord {
        val base = prev ?: VerifyRecord()
        return when (now.state) {
            VerifyState.OK -> base.copy(
                state = VerifyState.OK, failCount = 0, lastCheckedAt = nowMs, summary = now.message,
            )
            VerifyState.BAD -> {
                val fails = base.failCount + 1
                base.copy(
                    state = if (fails >= 2) VerifyState.BAD else VerifyState.SUSPECT,
                    failCount = fails,
                    lastCheckedAt = nowMs,
                    summary = now.message,
                )
            }
            VerifyState.LOCKED -> base.copy(
                // 需提取码说明链接是活的，不算失败
                state = VerifyState.LOCKED, failCount = 0, lastCheckedAt = nowMs, summary = now.message,
            )
            VerifyState.SUSPECT -> base.copy(
                state = VerifyState.SUSPECT,
                lastCheckedAt = nowMs,
                summary = now.message,
            )
            VerifyState.UNCHECKED -> base.copy(
                state = if (base.state == VerifyState.UNCHECKED) VerifyState.UNCHECKED else base.state,
                lastCheckedAt = nowMs,
                summary = now.message,
            )
        }
    }

    /**
     * 是否需要重新检测。
     * 有效 72h / 疑似 30min / 确认失效 12h 之后再复查。
     */
    fun shouldRecheck(rec: VerifyRecord?, nowMs: Long = System.currentTimeMillis(), force: Boolean = false): Boolean {
        if (force || rec == null || rec.lastCheckedAt <= 0L) return true
        val age = nowMs - rec.lastCheckedAt
        return when (rec.state) {
            VerifyState.OK -> age >= 72L * 3600 * 1000
            VerifyState.LOCKED -> age >= 72L * 3600 * 1000
            VerifyState.SUSPECT -> age >= 30L * 60 * 1000
            VerifyState.BAD -> age >= 12L * 3600 * 1000
            VerifyState.UNCHECKED -> true
        }
    }

    data class VerifyRecord(
        val state: VerifyState = VerifyState.UNCHECKED,
        val failCount: Int = 0,
        val lastCheckedAt: Long = 0L,
        val summary: String = "",
    )
}
