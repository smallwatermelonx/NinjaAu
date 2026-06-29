package com.example.ninjaau.core.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志工具类
 * 日志同时输出到 logcat 和文件（logs/ 目录，按日期分文件）
 * 文件路径：/sdcard/Android/data/com.example.ninjaau/files/logs/nau_2026-06-25.log
 * 拉取：adb pull /sdcard/Android/data/com.example.ninjaau/files/logs/
 */
object LogUtil {
    private const val DEFAULT_TAG = "NinjaAu"
    private var isInitialized = false
    private var logDir: File? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun init(context: Context) {
        isInitialized = true
        logDir = File(context.getExternalFilesDir(null), "logs").also { it.mkdirs() }
        i("LogUtil", "日志工具已初始化，日志目录: ${logDir?.absolutePath}")
    }

    fun d(tag: String, msg: String) {
        if (isInitialized) Log.d(tag, msg)
        else Log.d(DEFAULT_TAG, "[$tag] $msg (LogUtil not init)")
        writeFile("D", tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (isInitialized) Log.i(tag, msg)
        else Log.i(DEFAULT_TAG, "[$tag] $msg (LogUtil not init)")
        writeFile("I", tag, msg)
    }

    fun w(tag: String, msg: String) {
        if (isInitialized) Log.w(tag, msg)
        else Log.w(DEFAULT_TAG, "[$tag] $msg (LogUtil not init)")
        writeFile("W", tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (isInitialized) Log.e(tag, msg, tr)
        else Log.e(DEFAULT_TAG, "[$tag] $msg (LogUtil not init)", tr)
        val fullMsg = if (tr != null) "$msg\n${Log.getStackTraceString(tr)}" else msg
        writeFile("E", tag, fullMsg)
    }

    @Synchronized
    private fun writeFile(level: String, tag: String, msg: String) {
        val dir = logDir ?: return
        try {
            val date = Date()
            val fileName = "nau_${fileDateFormat.format(date)}.log"
            val file = File(dir, fileName)
            FileWriter(file, true).use { writer ->
                writer.write("${dateFormat.format(date)} $level/$tag: $msg\n")
            }
        } catch (_: Exception) {}
    }
}
