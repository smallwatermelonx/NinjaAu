package com.example.ninjaau.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 抢悬赏功能设置页（精简版，仅保留核心业务）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardGrabSettingsUI(
    onSaveSettings: () -> Unit // 保存设置的回调
) {
    // ========== 核心状态管理（仅保留业务需要的） ==========
    // 基础设置
    var isEnableLog by remember { mutableStateOf(false) } // 开启日志
    var isAutoRepair by remember { mutableStateOf(true) } // 开启自动修复（默认勾选）

    // 抢悬赏参数
    var failCount by remember { mutableStateOf("1") } // 失败次数
    var failCountExpanded by remember { mutableStateOf(false) } // 下拉框展开状态
    val failCountOptions = listOf("1", "2", "3", "5")
    var clickFreq by remember { mutableStateOf("100") } // 点击频率(ms)

    // 战斗相关（核心保留，默认勾选）
    var prepareTime by remember { mutableStateOf("30") } // 准备界面时间(s)
    var beforeSkillDelay by remember { mutableStateOf("30") } // 释放大招前延迟(ms)
    var skillInterval by remember { mutableStateOf("1500") } // 大招间隔(ms)
    var skillClickCount by remember { mutableStateOf("3") } // 大招点击次数
    val skillCountOptions = listOf("1", "2", "3", "5")
    var skillCountExpanded by remember { mutableStateOf(false) } // 大招次数下拉状态
    var isScriptJump by remember { mutableStateOf(true) } // 脚本跳（默认勾选）
    var isStartSlide by remember { mutableStateOf(true) } // 开局下滑步（默认勾选）
    var isNoSkillJump by remember { mutableStateOf(true) } // 无大招跳（默认勾选）
    var isPreSkill by remember { mutableStateOf(false) } // 提前开大
    var isWeiLiSkill by remember { mutableStateOf(false) } // 卫鲤大招

    // 副本过滤（仅保留核心）
    var isRing by remember { mutableStateOf(false) } // 响铃
    var isScreenshot by remember { mutableStateOf(false) } // 截图

    // 品质/区域设置（默认勾选指定项）
    var isAcceptGod by remember { mutableStateOf(false) } // 接受神品邀请
    var isAcceptPerfect by remember { mutableStateOf(false) } // 接受绝品邀请
    var isDreamGod by remember { mutableStateOf(false) } // 追梦神品
    var isDreamPerfect by remember { mutableStateOf(false) } // 追梦绝品
    var isDreamRare by remember { mutableStateOf(false) } // 追梦珍品
    var isDreamNormal by remember { mutableStateOf(false) } // 追梦凡品
    var isGod by remember { mutableStateOf(false) } // 神品
    var isPerfect by remember { mutableStateOf(false) } // 绝品
    var isRare by remember { mutableStateOf(false) } // 珍品
    var isNormal by remember { mutableStateOf(false) } // 凡品
    var isCloud by remember { mutableStateOf(true) } // 云之国（默认勾选）
    var isSea by remember { mutableStateOf(true) } // 海之国（默认勾选）
    var isFire by remember { mutableStateOf(true) } // 炎之国（默认勾选）
    var isThunder by remember { mutableStateOf(true) } // 雷王山（默认勾选）
    var isAuspicious by remember { mutableStateOf(true) } // 祥瑞（默认勾选）
    var isOpportunity by remember { mutableStateOf(true) } // 良机（默认勾选）
    var isCollision by remember { mutableStateOf(true) } // 撞衫（默认勾选）
    var isKoi by remember { mutableStateOf(true) } // 锦鲤（默认勾选）

    // 等级设置（默认勾选：S、S+、A+、B、C、D）
    var isReverseSSPlus by remember { mutableStateOf(false) } // 逆SS+
    var isSSPlus by remember { mutableStateOf(true) } // S+（默认勾选）
    var isSSPlusInvite by remember { mutableStateOf(false) } // S+邀请
    var isSS by remember { mutableStateOf(false) } // SS
    var isSSInvite by remember { mutableStateOf(false) } // SS邀请
    var isSPlus by remember { mutableStateOf(true) } // S+（默认勾选）
    var isSPlusInvite by remember { mutableStateOf(false) } // S+邀请
    var isS by remember { mutableStateOf(true) } // S（默认勾选）
    var isSInvite by remember { mutableStateOf(false) } // S邀请
    var isDreamS by remember { mutableStateOf(false) } // 追梦S
    var isReverseS by remember { mutableStateOf(false) } // 逆S
    var isReverseSInvite by remember { mutableStateOf(false) } // 逆S邀请
    var isDreamReverseS by remember { mutableStateOf(false) } // 追梦逆S
    var isReverseA by remember { mutableStateOf(false) } // 逆A
    var isReverseAInvite by remember { mutableStateOf(false) } // 逆A邀请
    var isDreamReverseA by remember { mutableStateOf(false) } // 追梦逆A
    var isAPlus by remember { mutableStateOf(true) } // A+（默认勾选）
    var isAPlusInvite by remember { mutableStateOf(false) } // A+邀请
    var isDreamA by remember { mutableStateOf(false) } // 追梦A
    var isA by remember { mutableStateOf(false) } // A
    var isAInvite by remember { mutableStateOf(false) } // A邀请
    var isDreamBCD by remember { mutableStateOf(false) } // 追梦BCD
    var isB by remember { mutableStateOf(true) } // B（默认勾选）
    var isBInvite by remember { mutableStateOf(false) } // B邀请
    var isC by remember { mutableStateOf(true) } // C（默认勾选）
    var isCInvite by remember { mutableStateOf(false) } // C邀请
    var isD by remember { mutableStateOf(true) } // D（默认勾选）
    var isDInvite by remember { mutableStateOf(false) } // D邀请

    // 家族boss
    var isFamilyBossInvite by remember { mutableStateOf(false) } // 家族boss邀请

    // ========== 精简后布局 ==========
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // 常用深黑/暗黑背景色值
            .padding(16.dp)
    ) {
        // 抢悬赏核心内容区域（支持滚动）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. 基础设置
            CheckboxRow(label = "开启日志", isChecked = isEnableLog, onCheckedChange = { isEnableLog = it })
            CheckboxRow(label = "开启自动修复", isChecked = isAutoRepair, onCheckedChange = { isAutoRepair = it })

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "-------------------------悬赏设置-------------------------",
                color = Color.DarkGray,
                fontSize = 12.sp
            )
            Text(
                text = "【说明1】后面的邀请，是指接受邀请的意思(目前只会接S)\n【说明2】游戏画面必须选极佳",
                color = Color.DarkGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            // 2. 抢悬赏参数
            // 失败次数下拉
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "失败", color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(4.dp))
                ExposedDropdownMenuBox(
                    expanded = failCountExpanded,
                    onExpandedChange = { failCountExpanded = it },
                    modifier = Modifier.width(60.dp)
                ) {
                    OutlinedTextField(
                        value = failCount,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = failCountExpanded,
                        onDismissRequest = { failCountExpanded = false }
                    ) {
                        failCountOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, fontSize = 14.sp) },
                                onClick = {
                                    failCount = option
                                    failCountExpanded = false
                                },
                                enabled = option != failCount
                            )
                        }
                    }
                }
                Text(text = "次，放弃该悬赏", color = Color.White, fontSize = 14.sp)
            }

            // 点击频率
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "点击频率（ms）", color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = clickFreq,
                    onValueChange = { clickFreq = it },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp)
                )
                Text(text = " 根据自己的设备自己调整(推荐100)", color = Color.White, fontSize = 14.sp)
            }

            // 3. 战斗设置（核心保留）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "准备界面", color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = prepareTime,
                    onValueChange = { prepareTime = it },
                    modifier = Modifier.width(60.dp),
                    singleLine = true
                )
                Text(text = "s，不准备退出任务", color = Color.White, fontSize = 14.sp)
            }

            CheckboxRow(
                label = "释放大招前 ${beforeSkillDelay} ms，小跳1次",
                isChecked = true,
                onCheckedChange = {}
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "每间隔", color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = skillInterval,
                    onValueChange = { skillInterval = it },
                    modifier = Modifier.width(80.dp),
                    singleLine = true
                )
                Text(text = "ms，点击1次大招，共点击", color = Color.White, fontSize = 14.sp)
                ExposedDropdownMenuBox(
                    expanded = skillCountExpanded,
                    onExpandedChange = { skillCountExpanded = it },
                    modifier = Modifier.width(60.dp)
                ) {
                    OutlinedTextField(
                        value = skillClickCount,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = skillCountExpanded,
                        onDismissRequest = { skillCountExpanded = false }
                    ) {
                        skillCountOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    skillClickCount = option
                                    skillCountExpanded = false
                                }
                            )
                        }
                    }
                }
                Text(text = "次", color = Color.White, fontSize = 14.sp)
            }

            CheckboxRow(label = "脚本跳（释放大招期间跳）", isChecked = isScriptJump, onCheckedChange = { isScriptJump = it })
            CheckboxRow(label = "开局下滑步", isChecked = isStartSlide, onCheckedChange = { isStartSlide = it })
            CheckboxRow(label = "无大招跳（没有大招的时候或者大招放完跳）", isChecked = isNoSkillJump, onCheckedChange = { isNoSkillJump = it })

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isPreSkill,
                    onCheckedChange = { isPreSkill = it },
                    colors = CheckboxDefaults.colors(checkedColor = Color.White)
                )
                Text(
                    text = "提前开大，出现警告后 ${beforeSkillDelay} ms后开大。（抢MVP）",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
            CheckboxRow(label = "卫鲤大招（会点中间那个技能）", isChecked = isWeiLiSkill, onCheckedChange = { isWeiLiSkill = it })

            // 4. 副本过滤（精简）
            CheckboxRow(label = "响铃(等级>80)", isChecked = isRing, onCheckedChange = { isRing = it })
            CheckboxRow(label = "截图(等级>=A+)", isChecked = isScreenshot, onCheckedChange = { isScreenshot = it })

            // 5. 品质/区域设置（精简）
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CheckboxItem(label = "接受神品邀请", isChecked = isAcceptGod, onCheckedChange = { isAcceptGod = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "接受绝品邀请", isChecked = isAcceptPerfect, onCheckedChange = { isAcceptPerfect = it }, modifier = Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CheckboxItem(label = "追梦神品", isChecked = isDreamGod, onCheckedChange = { isDreamGod = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "追梦绝品", isChecked = isDreamPerfect, onCheckedChange = { isDreamPerfect = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "追梦珍品", isChecked = isDreamRare, onCheckedChange = { isDreamRare = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "追梦凡品", isChecked = isDreamNormal, onCheckedChange = { isDreamNormal = it }, modifier = Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CheckboxItem(label = "云之国", isChecked = isCloud, onCheckedChange = { isCloud = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "海之国", isChecked = isSea, onCheckedChange = { isSea = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "炎之国", isChecked = isFire, onCheckedChange = { isFire = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "雷王山", isChecked = isThunder, onCheckedChange = { isThunder = it }, modifier = Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CheckboxItem(label = "祥瑞", isChecked = isAuspicious, onCheckedChange = { isAuspicious = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "良机", isChecked = isOpportunity, onCheckedChange = { isOpportunity = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "撞衫", isChecked = isCollision, onCheckedChange = { isCollision = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "锦鲤", isChecked = isKoi, onCheckedChange = { isKoi = it }, modifier = Modifier.weight(1f))
            }

            // 6. 等级设置（仅保留核心，默认勾选S、S+、A+、B、C、D）
            CheckboxRow(label = "逆SS+", isChecked = isReverseSSPlus, onCheckedChange = { isReverseSSPlus = it })
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CheckboxItem(label = "SS+", isChecked = isSSPlus, onCheckedChange = { isSSPlus = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "SS+邀请", isChecked = isSSPlusInvite, onCheckedChange = { isSSPlusInvite = it }, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CheckboxItem(label = "S+", isChecked = isSPlus, onCheckedChange = { isSPlus = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "S+邀请", isChecked = isSPlusInvite, onCheckedChange = { isSPlusInvite = it }, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CheckboxItem(label = "S", isChecked = isS, onCheckedChange = { isS = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "S邀请", isChecked = isSInvite, onCheckedChange = { isSInvite = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "追梦S", isChecked = isDreamS, onCheckedChange = { isDreamS = it }, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CheckboxItem(label = "A+", isChecked = isAPlus, onCheckedChange = { isAPlus = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "A+邀请", isChecked = isAPlusInvite, onCheckedChange = { isAPlusInvite = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "追梦A", isChecked = isDreamA, onCheckedChange = { isDreamA = it }, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CheckboxItem(label = "B", isChecked = isB, onCheckedChange = { isB = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "B邀请", isChecked = isBInvite, onCheckedChange = { isBInvite = it }, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CheckboxItem(label = "C", isChecked = isC, onCheckedChange = { isC = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "C邀请", isChecked = isCInvite, onCheckedChange = { isCInvite = it }, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CheckboxItem(label = "D", isChecked = isD, onCheckedChange = { isD = it }, modifier = Modifier.weight(1f))
                CheckboxItem(label = "D邀请", isChecked = isDInvite, onCheckedChange = { isDInvite = it }, modifier = Modifier.weight(1f))
            }

            // 7. 家族boss
            CheckboxRow(label = "家族boss邀请", isChecked = isFamilyBossInvite, onCheckedChange = { isFamilyBossInvite = it })

            // 保存按钮（带边框）
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = onSaveSettings,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                border = BorderStroke(2.dp, Color.LightGray),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF2196F3),
                    contentColor = Color.White
                )
            ) {
                Text(text = "保存设置", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * 复用组件：单行复选框（占满一行）
 */
@Composable
private fun CheckboxRow(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = Color.White)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, color = Color.White, fontSize = 14.sp)
    }
    Spacer(modifier = Modifier.height(4.dp))
}

/**
 * 复用组件：横向复选框项（用于一行多列）
 */
@Composable
private fun CheckboxItem(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = Color.White),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = Color.White, fontSize = 12.sp)
    }
}