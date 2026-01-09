package com.example.ninjaau.core.util

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

/**
 * 完善版日志工具类：
 * 1. 支持日志持久化到本地文件（内部存储，无需权限）
 * 2. 支持格式化字符串、异常堆栈完整打印
 * 3. 按天分割日志文件、自动清理旧日志
 * 4. 区分调试/发布环境，灵活控制日志开关
 */
object LogUtil {
    // 基础配置
    private const val DEBUG = true // 全局日志开关（发布版设为false）
    private const val SAVE_TO_FILE = true // 是否保存日志到文件
    private const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024L // 单个日志文件最大5MB
    private const val MAX_LOG_KEEP_DAYS = 7 // 日志文件保留7天
    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private val FILE_NAME_FORMAT = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
    private val writeExecutor = Executors.newSingleThreadExecutor() // 异步写日志，避免阻塞主线程

    // 日志存储路径（内部存储，无需权限，仅APP自身可访问）
    private lateinit var logDir: File

    /**
     * 初始化日志工具（在Application中调用，建议在APP启动时初始化）
     */
    fun init(context: Context) {
        logDir = File(context.filesDir, "ninja_logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        // 启动时清理过期日志
        cleanExpiredLogs()
    }

    // ====================== 基础日志方法（支持格式化字符串） ======================
    fun d(tag: String, message: String, vararg args: Any?) {
        val formattedMsg = formatMessage(message, args)
        if (DEBUG) Log.d(tag ?: "DEFAULT_TAG", formattedMsg)
        if (SAVE_TO_FILE) saveLogToFile("DEBUG", tag ?: "DEFAULT_TAG", formattedMsg)
    }

    fun i(tag: String, message: String, vararg args: Any?) {
        val formattedMsg = formatMessage(message, args)
        if (DEBUG) Log.i(tag ?: "DEFAULT_TAG", formattedMsg)
        if (SAVE_TO_FILE) saveLogToFile("INFO", tag ?: "DEFAULT_TAG", formattedMsg)
    }

    fun w(tag: String, message: String, vararg args: Any?) {
        val formattedMsg = formatMessage(message, args)
        if (DEBUG) Log.w(tag ?: "DEFAULT_TAG", formattedMsg)
        if (SAVE_TO_FILE) saveLogToFile("WARN", tag ?: "DEFAULT_TAG", formattedMsg)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null, vararg args: Any?) {
        val formattedMsg = formatMessage(message, args)
        if (DEBUG) {
            if (throwable != null) {
                Log.e(tag ?: "DEFAULT_TAG", formattedMsg, throwable)
            } else {
                Log.e(tag ?: "DEFAULT_TAG", formattedMsg)
            }
        }
        // 异常日志强制保存到文件（即使DEBUG=false，发布版也能排查崩溃）
        val finalMsg = if (throwable != null) {
            "$formattedMsg\n${Log.getStackTraceString(throwable)}"
        } else {
            formattedMsg
        }
        if (SAVE_TO_FILE) saveLogToFile("ERROR", tag ?: "DEFAULT_TAG", finalMsg)
    }

    // ====================== 私有工具方法 ======================
    /**
     * 格式化字符串（支持%d、%s等占位符）
     */
    private fun formatMessage(message: String, args: Array<out Any?>): String {
        return try {
            if (args.isNotEmpty()) String.format(message, *args) else message
        } catch (e: Exception) {
            "日志格式化失败：$message | 错误：${e.message}"
        }
    }

    /**
     * 异步保存日志到文件
     */
    private fun saveLogToFile(level: String, tag: String, message: String) {
        if (!::logDir.isInitialized) {
            Log.e("LogUtil", "日志工具未初始化，请先调用init(context)")
            return
        }
        writeExecutor.execute {
            try {
                val logFile = getCurrentLogFile()
                // 检查文件大小，超过则新建文件（加后缀）
                val targetFile = if (logFile.length() >= MAX_LOG_FILE_SIZE) {
                    val timeSuffix = SimpleDateFormat("HHmmss", Locale.CHINA).format(Date())
                    File(logDir, "${FILE_NAME_FORMAT.format(Date())}_$timeSuffix.txt")
                } else {
                    logFile
                }

                // 写入日志内容
                val logContent = "${DATE_FORMAT.format(Date())} | $level | $tag | $message\n"
                FileWriter(targetFile, true).use { writer ->
                    writer.write(logContent)
                }
            } catch (e: IOException) {
                Log.e("LogUtil", "保存日志到文件失败：${e.message}", e)
            }
        }
    }

    /**
     * 获取当天的日志文件
     */
    private fun getCurrentLogFile(): File {
        val fileName = "${FILE_NAME_FORMAT.format(Date())}.txt"
        return File(logDir, fileName)
    }

    /**
     * 清理过期的日志文件（保留最近7天）
     */
    private fun cleanExpiredLogs() {
        writeExecutor.execute {
            try {
                val cutoffTime = System.currentTimeMillis() - MAX_LOG_KEEP_DAYS * 24 * 60 * 60 * 1000L
                logDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".txt") && file.lastModified() < cutoffTime) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e("LogUtil", "清理过期日志失败：${e.message}", e)
            }
        }
    }

    /**
     * 获取日志文件目录（供后续查看日志使用）
     */
    fun getLogDirPath(): String {
        return if (::logDir.isInitialized) logDir.absolutePath else ""
    }
}