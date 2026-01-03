package com.example.ninjaau.core.util

import android.util.Log

object LogUtil {
    // 是否开启日志（发布时可设为false关闭）
    private const val DEBUG = true

    // 打印调试信息
    fun d(tag: String, message: String) {
        if (DEBUG) {
            Log.d(tag, message)
        }
    }

    // 打印错误信息
    fun e(tag: String, message: String) {
        if (DEBUG) {
            Log.e(tag, message)
        }
    }

    // 打印警告信息
    fun w(tag: String, message: String) {
        if (DEBUG) {
            Log.w(tag, message)
        }
    }

    // 打印普通信息
    fun i(tag: String, message: String) {
        if (DEBUG) {
            Log.i(tag, message)
        }
    }
}
