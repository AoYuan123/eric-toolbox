package com.eric.toolbox.modules.pan.engine

import com.eric.toolbox.modules.pan.model.EngineResult
import com.eric.toolbox.modules.pan.model.SearchOptions

/**
 * 数据源统一接口。
 * 加新源只需要实现这个接口，然后在 PanSearch 里注册。
 */
interface PanEngine {
    /** 引擎名，用于错误提示与来源标记 */
    val name: String

    /**
     * 执行搜索。
     * 约定：不要抛异常，失败时返回 EngineResult(error = "原因")。
     * 这样上层可以「单源失败不中断」。
     */
    suspend fun search(keyword: String, opts: SearchOptions): EngineResult
}
