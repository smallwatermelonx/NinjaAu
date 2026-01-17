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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 抢悬赏功能设置页（优化布局+默认勾选调整）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardGrabSettingsUI(
    onSaveSettings: () -> Unit // 保存设置的回调
) {
    // ========== 核心状态管理（已调整默认值） ==========
    // 基础设置
    var isEnableLog by remember { mutableStateOf(false) } // 开启日志（默认不勾选）
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
    var isPreSkill by remember { mutableStateOf(false) } // 提前开大（默认不勾选）
    var isWeiLiSkill by remember { mutableStateOf(false) } // 卫鲤大招（默认不勾选）

    // 副本过滤（精简）
    var isRing by remember { mutableStateOf(false) } // 响铃（默认不勾选）
    var isScreenshot by remember { mutableStateOf(false) } // 截图（默认不勾选）

    // 品质/区域设置（默认勾选指定项）
    var isAcceptGod by remember { mutableStateOf(false) } // 接受神品邀请（默认不勾选）
    var isAcceptPerfect by remember { mutableStateOf(false) } // 接受绝品邀请（默认不勾选）
    var isDreamGod by remember { mutableStateOf(false) } // 追梦神品（默认不勾选）
    var isDreamPerfect by remember { mutableStateOf(false) } // 追梦绝品（默认不勾选）
    var isDreamRare by remember { mutableStateOf(false) } // 追梦珍品（默认不勾选）
    var isDreamNormal by remember { mutableStateOf(false) } // 追梦凡品（默认不勾选）
    var isCloud by remember { mutableStateOf(true) } // 云之国（默认勾选）
    var isSea by remember { mutableStateOf(true) } // 海之国（默认勾选）
    var isFire by remember { mutableStateOf(true) } // 炎之国（默认勾选）
    var isThunder by remember { mutableStateOf(true) } // 雷王山（默认勾选）
    var isAuspicious by remember { mutableStateOf(false) } // 祥瑞
    var isOpportunity by remember { mutableStateOf(false) } // 良机
    var isCollision by remember { mutableStateOf(false) } // 撞衫
    var isKoi by remember { mutableStateOf(false) } // 锦鲤

    // 等级设置（核心等级默认勾选，邀请/追梦默认不勾选）
    var isReverseSSPlus by remember { mutableStateOf(false) } // 逆SS+（默认不勾选）
    var isSSPlus by remember { mutableStateOf(false) } // SS+（默认勾选）
    var isSSPlusInvite by remember { mutableStateOf(false) } // SS+邀请（默认不勾选）
    var isSPlus by remember { mutableStateOf(false) } // S+（默认勾选）
    var isSPlusInvite by remember { mutableStateOf(false) } // S+邀请（默认不勾选）
    var isS by remember { mutableStateOf(false) } // S（默认勾选）
    var isSInvite by remember { mutableStateOf(false) } // S邀请（默认不勾选）
    var isDreamS by remember { mutableStateOf(false) } // 追梦S（默认不勾选）
    var isAPlus by remember { mutableStateOf(true) } // A+（默认勾选）
    var isAPlusInvite by remember { mutableStateOf(false) } // A+邀请（默认不勾选）
    var isDreamA by remember { mutableStateOf(false) } // 追梦A（默认不勾选）
    var isB by remember { mutableStateOf(true) } // B（默认勾选）
    var isBInvite by remember { mutableStateOf(false) } // B邀请（默认不勾选）
    var isC by remember { mutableStateOf(true) } // C（默认勾选）
    var isCInvite by remember { mutableStateOf(false) } // C邀请（默认不勾选）
    var isD by remember { mutableStateOf(true) } // D（默认勾选）
    var isDInvite by remember { mutableStateOf(false) } // D邀请（默认不勾选）

    // 家族boss
    var isFamilyBossInvite by remember { mutableStateOf(false) } // 家族boss邀请（默认不勾选）

    // ========== 优化后布局 ==========
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // 深黑背景
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
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
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

            // ========== 5. 藏宝图设置（优化布局：标题居中放大 + 选项分四块左对齐） ==========
            // 标题：居中放大，改为“藏宝图”
            Text(
                text = "藏宝图",
                color = Color.White, // 白色更醒目
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
            )

            // 选项：分4块左对齐，每行4个选项，宽度均匀
            Column(modifier = Modifier.fillMaxWidth()) {
                // 第1行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CheckboxItem(
                        label = "接受神品邀请",
                        isChecked = isAcceptGod,
                        onCheckedChange = { isAcceptGod = it },
                        modifier = Modifier.weight(1f)
                    )
                    CheckboxItem(
                        label = "追梦神品",
                        isChecked = isDreamGod,
                        onCheckedChange = { isDreamGod = it },
                        modifier = Modifier.weight(1f)
                    )
                    CheckboxItem(
                        label = "云之国",
                        isChecked = isCloud,
                        onCheckedChange = { isCloud = it },
                        modifier = Modifier.weight(1f)
                    )
                    CheckboxItem(
                        label = "祥瑞",
                        isChecked = isAuspicious,
                        onCheckedChange = { isAuspicious = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 第2行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CheckboxItem(
                        label = "接受绝品邀请",
                        isChecked = isAcceptPerfect,
                        onCheckedChange = { isAcceptPerfect = it },
                        modifier = Modifier.weight(1f)
                    )
                    CheckboxItem(
                        label = "追梦绝品",
                        isChecked = isDreamPerfect,
                        onCheckedChange = { isDreamPerfect = it },
                        modifier = Modifier.weight(1f)
                    )
                    CheckboxItem(
                        label = "海之国",
                        isChecked = isSea,
                        onCheckedChange = { isSea = it },
                        modifier = Modifier.weight(1f)
                    )
                    CheckboxItem(
                        label = "良机",
                        isChecked = isOpportunity,
                        onCheckedChange = { isOpportunity = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 第3行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CheckboxItem(
                        label = "追梦珍品",
                        isChecked = isDreamRare,
                        onCheckedChange = { isDreamRare = it },
                        modifier = Modifier.weight(1f)
                    )
                    CheckboxItem(
                        label = "追梦凡品",
                        isChecked = isDreamNormal,
                        onCheckedChange = { isDreamNormal = it },
                        modifier = Modifier.weight(1f)
                    )
                    CheckboxItem(
                        label = "炎之国",
                        isChecked = isFire,
                        onCheckedChange = { isFire = it },
                        modifier = Modifier.weight(1f)
                    )
                    CheckboxItem(
                        label = "撞衫",
                        isChecked = isCollision,
                        onCheckedChange = { isCollision = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 第4行（剩余项居中对齐）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.weight(1f))
                    CheckboxItem(
                        label = "雷王山",
                        isChecked = isThunder,
                        onCheckedChange = { isThunder = it },
                        modifier = Modifier.weight(1f)
                    )
                    CheckboxItem(
                        label = "锦鲤",
                        isChecked = isKoi,
                        onCheckedChange = { isKoi = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ========== 6. 逆袭悬赏 + 日常悬赏（左对齐布局） ==========
            // 逆袭悬赏标题（居中放大）
            Text(
                text = "逆袭悬赏",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
            )

            // 逆袭悬赏选项（左对齐，左侧留16dp间距）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp), // 左侧统一基础间距
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 第一份：占1/3宽度，内部左对齐
                Row(
                    modifier = Modifier.weight(1f), // 均分宽度
                    horizontalArrangement = Arrangement.Start // 内部左对齐
                ) {
                    CheckboxItem(label = "逆SS+", isChecked = isReverseSSPlus, onCheckedChange = { isReverseSSPlus = it })
                }

                // 第二份：占1/3宽度，内部左对齐
                Row(
                    modifier = Modifier.weight(1f), // 均分宽度
                    horizontalArrangement = Arrangement.Start // 内部左对齐
                ) {
                    CheckboxItem(label = "逆S", isChecked = false, onCheckedChange = {})
                }

                // 第三份：占1/3宽度，内部左对齐
                Row(
                    modifier = Modifier.weight(1f), // 均分宽度
                    horizontalArrangement = Arrangement.Start // 内部左对齐
                ) {
                    CheckboxItem(label = "逆A", isChecked = false, onCheckedChange = {})
                }
            }

            // 日常悬赏标题（居中放大）
            Text(
                text = "日常悬赏",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
            )

            // 日常悬赏选项（全部左对齐）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 第一份：占1/2宽度，内部左对齐
                Row(
                    modifier = Modifier.weight(1f), // 均分宽度（两份中的第一份）
                    horizontalArrangement = Arrangement.Start // 内部左对齐
                ) {
                    CheckboxItem(label = "SS+", isChecked = isSSPlus, onCheckedChange = { isSSPlus = it })
                }

                // 第二份：占1/2宽度，内部左对齐
                Row(
                    modifier = Modifier.weight(1f), // 均分宽度（两份中的第二份）
                    horizontalArrangement = Arrangement.Start // 内部左对齐
                ) {
                    CheckboxItem(label = "SS", isChecked = false, onCheckedChange = {})
                }
            }

            // SS、S+、S 单独一行（左对齐）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 第1份（占1/2宽度）：内部左对齐
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start
                ) {
                    CheckboxItem(label = "S+", isChecked = isSPlus, onCheckedChange = { isSPlus = it })
                }
                // 第2份（占1/2宽度）：内部左对齐
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start
                ) {
                    CheckboxItem(label = "S", isChecked = isS, onCheckedChange = { isS = it })
                }
            }

            // A+、A、B、C、D 单独一行（左对齐）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 第1份（占1/5宽度）：内部左对齐
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start
                ) {
                    CheckboxItem(label = "A+", isChecked = isAPlus, onCheckedChange = { isAPlus = it })
                }
                // 第2份（占1/5宽度）：内部左对齐
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start
                ) {
                    CheckboxItem(label = "A", isChecked = false, onCheckedChange = {})
                }
                // 第3份（占1/5宽度）：内部左对齐
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start
                ) {
                    CheckboxItem(label = "B", isChecked = isB, onCheckedChange = { isB = it })
                }
                // 第4份（占1/5宽度）：内部左对齐
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start
                ) {
                    CheckboxItem(label = "C", isChecked = isC, onCheckedChange = { isC = it })
                }
                // 第5份（占1/5宽度）：内部左对齐
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start
                ) {
                    CheckboxItem(label = "D", isChecked = isD, onCheckedChange = { isD = it })
                }
            }

            // ========== 追梦&邀请（分类布局，和日常悬赏风格统一） ==========
// 主标题：居中放大加粗，和其他分类标题风格一致
            Text(
                text = "追梦&邀请",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
            )

// 第一大类：邀请（子标题+细分选项）
            Text(
                text = "邀请（默认不勾选）",
                color = Color.LightGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 16.dp)
            )

// 邀请-第一行：SS+邀请、SS邀请（平均两份，左对齐）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                    CheckboxItem(label = "SS+邀请", isChecked = isSSPlusInvite, onCheckedChange = { isSSPlusInvite = it })
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                    CheckboxItem(label = "SS邀请", isChecked = false, onCheckedChange = {}) // 补充SS邀请
                }
            }

// 邀请-第二行：S+邀请、S邀请（平均两份，左对齐）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                    CheckboxItem(label = "S+邀请", isChecked = isSPlusInvite, onCheckedChange = { isSPlusInvite = it })
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                    CheckboxItem(label = "S邀请", isChecked = isSInvite, onCheckedChange = { isSInvite = it })
                }
            }

// 邀请-第三行：A+邀请（单独一行，左对齐）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckboxItem(label = "A+邀请", isChecked = isAPlusInvite, onCheckedChange = { isAPlusInvite = it })
            }

// 邀请-第四行：A邀请、B邀请（平均两份，左对齐）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                    CheckboxItem(label = "A邀请", isChecked = false, onCheckedChange = {}) // 补充A邀请
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                    CheckboxItem(label = "B邀请", isChecked = isBInvite, onCheckedChange = { isBInvite = it })
                }
            }

// 邀请-第五行：C邀请、D邀请（平均两份，左对齐）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                    CheckboxItem(label = "C邀请", isChecked = isCInvite, onCheckedChange = { isCInvite = it })
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                    CheckboxItem(label = "D邀请", isChecked = isDInvite, onCheckedChange = { isDInvite = it })
                }
            }

// 第二大类：追梦（子标题+细分选项）
            Text(
                text = "追梦（默认不勾选）",
                color = Color.LightGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 16.dp)
            )

// 追梦-第一行：追梦S、追梦A（平均两份，左对齐）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                    CheckboxItem(label = "追梦S", isChecked = isDreamS, onCheckedChange = { isDreamS = it })
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                    CheckboxItem(label = "追梦A", isChecked = isDreamA, onCheckedChange = { isDreamA = it })
                }
            }

            // 7. 家族boss
            CheckboxRow(label = "家族boss邀请", isChecked = isFamilyBossInvite, onCheckedChange = { isFamilyBossInvite = it })

            // 保存按钮（带边框）
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = onSaveSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
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