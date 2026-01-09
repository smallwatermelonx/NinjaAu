package com.example.ninjaau.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import java.io.*
import java.nio.charset.StandardCharsets

/**
 * 文件操作工具类（适配Android 10+沙盒存储，优先内部存储，无需权限）
 * 核心用途：
 * 1. 识别模块：读取uimap目录下的模板图片、保存识别结果截图
 * 2. 配置管理：读写脚本配置文件（比如勾选的功能、监控间隔等）
 * 3. 通用操作：文件夹创建、文件删除、存在性判断等
 */
object FileUtil {
    private const val TAG = "FileUtil"

    // ====================== 核心路径管理（避免硬编码，统一管理） ======================
    /**
     * 获取APP内部存储根目录（无需权限，仅本APP可访问）
     */
    fun getInternalRootDir(context: Context): File {
        return context.filesDir // /data/data/com.example.ninjaau/files
    }

    /**
     * 获取模板图片目录（存放uimap里的匹配图片，比如悬赏按钮、开始游戏按钮截图）
     */
    fun getTemplateDir(context: Context): File {
        val templateDir = File(getInternalRootDir(context), "uimap")
        createDir(templateDir)
        return templateDir
    }

    /**
     * 获取配置文件目录（存放脚本的配置项，比如功能勾选状态、监控间隔等）
     */
    fun getConfigDir(context: Context): File {
        val configDir = File(getInternalRootDir(context), "config")
        createDir(configDir)
        return configDir
    }

    /**
     * 获取截图保存目录（识别模块的截图/匹配结果）
     */
    fun getScreenshotDir(context: Context): File {
        val screenshotDir = File(getInternalRootDir(context), "screenshots")
        createDir(screenshotDir)
        return screenshotDir
    }

    // ====================== 文件夹操作 ======================
    /**
     * 创建文件夹（多级目录自动创建）
     */
    fun createDir(dir: File): Boolean {
        return try {
            if (!dir.exists()) {
                dir.mkdirs() // 多级目录创建
                LogUtil.d(TAG, "文件夹创建成功：${dir.absolutePath}")
            }
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "创建文件夹失败：${dir.absolutePath}", e)
            false
        }
    }

    /**
     * 判断文件夹是否存在
     */
    fun isDirExist(dirPath: String): Boolean {
        val dir = File(dirPath)
        return dir.exists() && dir.isDirectory
    }

    /**
     * 列出目录下的所有文件（按后缀过滤，比如只取.png图片）
     */
    fun listFilesBySuffix(dir: File, suffix: String): List<File> {
        val fileList = mutableListOf<File>()
        try {
            if (!dir.exists() || !dir.isDirectory) {
                LogUtil.w(TAG, "目录不存在或不是文件夹：${dir.absolutePath}")
                return fileList
            }
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(suffix, ignoreCase = true)) {
                    fileList.add(file)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "列出目录文件失败：${dir.absolutePath}", e)
        }
        return fileList
    }

    // ====================== 文件操作 ======================
    /**
     * 判断文件是否存在
     */
    fun isFileExist(filePath: String): Boolean {
        val file = File(filePath)
        return file.exists() && file.isFile
    }

    /**
     * 删除文件/文件夹
     */
    fun deleteFile(file: File): Boolean {
        return try {
            if (file.isDirectory) {
                // 递归删除文件夹
                file.listFiles()?.forEach { childFile ->
                    deleteFile(childFile)
                }
            }
            val isDeleted = file.delete()
            if (isDeleted) {
                LogUtil.d(TAG, "删除文件成功：${file.absolutePath}")
            } else {
                LogUtil.w(TAG, "删除文件失败：${file.absolutePath}")
            }
            isDeleted
        } catch (e: Exception) {
            LogUtil.e(TAG, "删除文件异常：${file.absolutePath}", e)
            false
        }
    }

    /**
     * 获取文件大小（字节）
     */
    fun getFileSize(file: File): Long {
        return if (file.exists() && file.isFile) file.length() else 0L
    }

    /**
     * 获取文件名（不带后缀）
     */
    fun getFileNameWithoutExtension(file: File): String {
        val fileName = file.name
        val lastDotIndex = fileName.lastIndexOf(".")
        return if (lastDotIndex == -1) fileName else fileName.substring(0, lastDotIndex)
    }

    // ====================== 文本文件读写（配置文件专用） ======================
    /**
     * 读取文本文件内容（默认UTF-8编码）
     */
    fun readTextFromFile(file: File): String {
        if (!isFileExist(file.absolutePath)) {
            LogUtil.w(TAG, "读取文本文件失败：文件不存在 ${file.absolutePath}")
            return ""
        }
        return try {
            val sb = StringBuilder()
            BufferedReader(InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }
            }
            val content = sb.toString().trim()
            LogUtil.d(TAG, "读取文本文件成功：${file.absolutePath}，大小：${content.length} 字符")
            content
        } catch (e: Exception) {
            LogUtil.e(TAG, "读取文本文件异常：${file.absolutePath}", e)
            ""
        }
    }

    /**
     * 写入文本内容到文件（覆盖/追加模式，默认UTF-8）
     */
    fun writeTextToFile(file: File, content: String, append: Boolean = false): Boolean {
        // 先创建父目录
        createDir(file.parentFile ?: return false)
        return try {
            BufferedWriter(OutputStreamWriter(FileOutputStream(file, append), StandardCharsets.UTF_8)).use { writer ->
                writer.write(content)
            }
            LogUtil.d(TAG, "写入文本文件成功：${file.absolutePath}，大小：${content.length} 字符")
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "写入文本文件异常：${file.absolutePath}", e)
            false
        }
    }

    // ====================== 字节文件读写（图片/二进制文件专用） ======================
    /**
     * 读取文件为字节数组（模板图片读取核心方法）
     */
    fun readBytesFromFile(file: File): ByteArray? {
        if (!isFileExist(file.absolutePath)) {
            LogUtil.w(TAG, "读取字节文件失败：文件不存在 ${file.absolutePath}")
            return null
        }
        return try {
            FileInputStream(file).use { inputStream ->
                val bytes = inputStream.readBytes()
                LogUtil.d(TAG, "读取字节文件成功：${file.absolutePath}，大小：${bytes.size} 字节")
                bytes
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "读取字节文件异常：${file.absolutePath}", e)
            null
        }
    }

    /**
     * 写入字节数组到文件（保存图片/二进制数据）
     */
    fun writeBytesToFile(file: File, bytes: ByteArray): Boolean {
        // 先创建父目录
        createDir(file.parentFile ?: return false)
        return try {
            FileOutputStream(file).use { outputStream ->
                outputStream.write(bytes)
            }
            LogUtil.d(TAG, "写入字节文件成功：${file.absolutePath}，大小：${bytes.size} 字节")
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "写入字节文件异常：${file.absolutePath}", e)
            false
        }
    }

    // ====================== 图片文件专用方法（识别模块核心） ======================
    /**
     * 从文件读取Bitmap（模板图片加载）
     */
    fun readBitmapFromFile(file: File): Bitmap? {
        val bytes = readBytesFromFile(file) ?: return null
        return try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            LogUtil.d(TAG, "读取图片文件成功：${file.absolutePath}，尺寸：${bitmap.width}x${bitmap.height}")
            bitmap
        } catch (e: Exception) {
            LogUtil.e(TAG, "解码图片文件异常：${file.absolutePath}", e)
            null
        }
    }

    /**
     * 保存Bitmap到文件（识别结果截图保存）
     */
    fun saveBitmapToFile(bitmap: Bitmap, file: File, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG, quality: Int = 100): Boolean {
        // 先创建父目录
        createDir(file.parentFile ?: return false)
        return try {
            FileOutputStream(file).use { outputStream ->
                val success = bitmap.compress(format, quality, outputStream)
                if (success) {
                    LogUtil.d(TAG, "保存图片文件成功：${file.absolutePath}，格式：${format.name}，质量：$quality")
                } else {
                    LogUtil.w(TAG, "保存图片文件失败：压缩Bitmap失败 ${file.absolutePath}")
                }
                success
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "保存图片文件异常：${file.absolutePath}", e)
            false
        }
    }
}