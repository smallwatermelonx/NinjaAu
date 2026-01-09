package com.example.ninjaau

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ninjaau.core.accessibility.AccessibilityChecker
import com.example.ninjaau.core.accessibility.NinjaAccessibilityService
import com.example.ninjaau.core.appcontrol.AdbController
import com.example.ninjaau.core.appcontrol.AppAutoRestartService // 修正服务类名
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.ui.theme.NinjaAuTheme

class MainActivity : ComponentActivity() {
    // 替换成你实际的忍三包名（必须填对！）
    private val NINJA_PACKAGE_NAME = "com.pandadagames.ninja.global" // 示例，需替换为真实包名

    // 关键：持有弹窗引用，用于后续关闭
    private var accessibilityDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 第一步：检测无障碍服务是否开启，未开启则弹窗提醒
        checkAccessibilityService()
        // 初始化日志工具
        LogUtil.init(this)
        setContent {
            NinjaAuTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Compose布局：添加3个测试按钮
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // MainActivity中按钮1的点击事件（只需补充context参数）
                        Button(onClick = {
                            // 传入this@MainActivity作为context
                            val isSuccess = AdbController.launchApp(this@MainActivity, NINJA_PACKAGE_NAME)
                            showToast(if (isSuccess) "启动忍三成功" else "启动忍三失败")
                        }) {
                            Text(text = "启动忍三游戏")
                        }

                        // 按钮2：启动监控服务（间距10dp）
                        Button(
                            onClick = {
                                AdbController.startAppMonitorService(this@MainActivity, NINJA_PACKAGE_NAME)
                                showToast("监控服务已启动（查看状态栏通知）")
                            },
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Text(text = "启动监控服务")
                        }

                        // 按钮3：停止监控服务
                        Button(
                            onClick = {
                                AdbController.stopAppMonitorService(this@MainActivity)
                                showToast("监控服务已停止")
                            },
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Text(text = "停止监控服务")
                        }

                        // 新增：测试无障碍点击（需先开启服务）
                        Button(
                            onClick = {
                                testAccessibilityClick()
                            },
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Text(text = "测试无障碍点击")
                        }
                    }
                }
            }
        }
    }

    /**
     * 检测无障碍服务状态，未开启则弹窗引导（新增弹窗引用管理）
     */
    private fun checkAccessibilityService() {
        // 先关闭已存在的弹窗（避免重复弹窗）
        accessibilityDialog?.dismiss()

        if (!AccessibilityChecker.isNinjaAccessibilityEnabled(this)) {
            // 创建弹窗并赋值给引用
            accessibilityDialog = AlertDialog.Builder(this)
                .setTitle("需要开启无障碍权限")
                .setMessage("忍三自动化功能需要依赖无障碍服务才能实现自动点击、界面识别等操作，请前往系统设置开启权限。")
                .setPositiveButton("去开启") { _, _ ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
                .setNegativeButton("取消") { _, _ ->
                    // 取消时清空引用
                    accessibilityDialog = null
                }
                .setCancelable(true) // 改为可取消（避免用户无法操作）
                .setOnDismissListener {
                    // 弹窗消失时清空引用
                    accessibilityDialog = null
                }
                .show()
        } else {
            // 服务已开启：关闭弹窗+提示
            accessibilityDialog?.dismiss()
            accessibilityDialog = null
            showToast("无障碍服务已开启，可正常使用自动化功能")
        }
    }

    /**
     * 测试无障碍点击（示例：查找“开始游戏”按钮并点击）
     */
    private fun testAccessibilityClick() {
        if (!AccessibilityChecker.isNinjaAccessibilityEnabled(this)) {
            showToast("请先开启无障碍服务")
            checkAccessibilityService() // 重新弹窗提醒
            return
        }

        // 调用无障碍服务的点击方法
        val accessibilityService = NinjaAccessibilityService.getInstance()
        val startBtn = accessibilityService?.findNodeByText("开始游戏")
        val isClickSuccess = accessibilityService?.clickNode(startBtn) ?: false

        showToast(if (isClickSuccess) "点击“开始游戏”成功" else "未找到“开始游戏”按钮")
    }

    /**
     * 用户从设置页面返回时，重新检测服务状态
     */
    override fun onResume() {
        super.onResume()
        checkAccessibilityService()
    }

    /**
     * 页面销毁时：确保弹窗关闭（避免内存泄漏）
     */
    override fun onDestroy() {
        super.onDestroy()
        accessibilityDialog?.dismiss()
        accessibilityDialog = null
    }

    // 封装Toast提示（Compose中调用Context的工具方法）
    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}