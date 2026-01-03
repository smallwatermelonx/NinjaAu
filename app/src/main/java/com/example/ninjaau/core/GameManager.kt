package com.example.ninjaau.core

import com.example.ninjaau.core.adb.AdbController
import com.example.ninjaau.core.util.LogUtil

class GameManager {
    private val TAG = "GameManager"
    // 忍者必须死3的包名（需替换为实际值）
    private val GAME_PACKAGE_NAME = "com.pandadagames.ninja.global"
    // 游戏主Activity（通常是.MainActivity，若不确定可通过adb logcat查看）
    private val GAME_MAIN_ACTIVITY = ".MainActivity"

    // 启动游戏（通过ADB命令）
    fun launchGame(): Boolean {
        LogUtil.d(TAG, "通过ADB启动游戏: $GAME_PACKAGE_NAME")
        return AdbController.launchApp(GAME_PACKAGE_NAME, GAME_MAIN_ACTIVITY)
    }

    // 停止游戏（通过ADB命令）
    fun stopGame(): Boolean {
        LogUtil.d(TAG, "通过ADB停止游戏: $GAME_PACKAGE_NAME")
        return AdbController.stopApp(GAME_PACKAGE_NAME)
    }

    // 检测游戏是否运行（通过ADB命令）
    fun isGameRunning(): Boolean {
        val isRunning = AdbController.isAppRunning(GAME_PACKAGE_NAME)
        LogUtil.d(TAG, "游戏运行状态: $isRunning")
        return isRunning
    }
}
