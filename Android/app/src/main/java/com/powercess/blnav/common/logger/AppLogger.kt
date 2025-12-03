package com.powercess.blnav.common.logger

import android.util.Log

/**
 * 应用日志工具
 *
 * 统一的日志管理，便于后续替换为其他日志框架（如Timber、Firebase等）
 */
object AppLogger {

    /**
     * 调试级别日志
     */
    fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    /**
     * 信息级别日志
     */
    fun info(tag: String, message: String) {
        Log.i(tag, message)
    }

    /**
     * 警告级别日志
     */
    fun warn(tag: String, message: String) {
        Log.w(tag, message)
    }

    /**
     * 错误级别日志
     */
    fun error(tag: String, message: String, exception: Exception? = null) {
        Log.e(tag, message, exception)
    }

    /**
     * 详细级别日志
     */
    fun verbose(tag: String, message: String) {
        Log.v(tag, message)
    }
}

