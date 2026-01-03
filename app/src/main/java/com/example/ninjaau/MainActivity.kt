package com.example.ninjaau

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ninjaau.core.AutoRestartService
import com.example.ninjaau.ui.theme.NinjaAuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 应用启动后立即启动后台服务（无需界面，可省略setContentView）
        val serviceIntent = Intent(this, AutoRestartService::class.java)
        startService(serviceIntent)

        // 可选：启动服务后关闭界面（纯后台运行）
        finish()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NinjaAuTheme {
        Greeting("Android")
    }
}