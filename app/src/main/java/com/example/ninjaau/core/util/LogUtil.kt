package com.example.ninjaau.core.util

import android.content.Context
import android.util.Log

/**
 * 日志工具类
 * 核心修复：增加 init 方法，防止未初始化报错
 */
object LogUtil {
    private const val DEFAULT_TAG = "NinjaAu"
    private var isInitialized = false

    /**
     * 在 Application 中调用
     */
    fun init(context: Context) {
        // 目前仅做标记，后续可扩展 FileLog 等
        isInitialized = true
        i("LogUtil", "日志工具已初始化")
    }

    fun d(tag: String, msg: String) {
        if (isInitialized) Log.d(tag, msg)
        else Log.d(DEFAULT_TAG, "[$tag] $msg (LogUtil not init)")
    }

    fun i(tag: String, msg: String) {
        if (isInitialized) Log.i(tag, msg)
        else Log.i(DEFAULT_TAG, "[$tag] $msg (LogUtil not init)")
    }

    fun w(tag: String, msg: String) {
        if (isInitialized) Log.w(tag, msg)
        else Log.w(DEFAULT_TAG, "[$tag] $msg (LogUtil not init)")
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (isInitialized) Log.e(tag, msg, tr)
        else Log.e(DEFAULT_TAG, "[$tag] $msg (LogUtil not init)", tr)
    }
}
