package com.example.ninjaau.core.recognition

import android.content.Context
import android.graphics.Bitmap
import com.example.ninjaau.core.util.AssetUtil
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.OpenCVUtil
import com.example.ninjaau.model.BountyGrade
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.opencv.core.Mat

/**
 * 场景识别层 — 将 Bitmap 映射为 ScreenState + 坐标
 * 模板路径与阈值在此集中管理，ScreenState 枚举本身不耦合识别配置
 *
 * 模板缓存：首次加载后常驻内存，避免重复 I/O。
 * 按阶段检测：每个检测只检相关状态，不遍历全表。
 */
class SceneDetector(private val context: Context) {
    private val TAG = "SceneDetector"

    private data class TemplateEntry(val path: String, val threshold: Float = 0.8f)

    /** 等级匹配结果（含相似度分数，用于最佳匹配策略） */
    data class GradeMatch(
        val grade: BountyGrade,
        val similarity: Float,
        val centerX: Float,
        val centerY: Float
    )

    /** 模板测试结果 */
    data class TemplateTestResult(
        val name: String,
        val similarity: Float,
        val threshold: Float,
        val passed: Boolean,
        val centerX: Float?,
        val centerY: Float?
    )

    /** 测试时的屏幕裁剪方式 — 与实际识别保持一致 */
    enum class TestCrop {
        /** 全屏匹配 */
        FULL,
        /** 左侧 1/3（悬赏列表等级图标区域） */
        LEFT_THIRD,
        /** 上方 1/8（队伍房间等级图标区域） */
        TOP_EIGHTH
    }

    /** 按节点分组的模板定义 */
    enum class NodeTemplateGroup(
        val displayName: String,
        val states: List<ScreenState>,
        val testLevelIcons: Boolean,
        val testGradeIcons: Boolean,
        /** 指定目录时，测试该目录下所有 PNG（替代 states 列表） */
        val templateDir: String? = null,
        /** 测试时的裁剪方式（与实际识别一致） */
        val testCrop: TestCrop = TestCrop.FULL
    ) {
        LOBBY("大厅导航", listOf(
            ScreenState.CHAT_ICON, ScreenState.RECRUIT_LIST_SCREEN
        ), false, false),
        RECRUIT_LIST("悬赏列表扫描", listOf(
            ScreenState.RECRUIT_TAB
        ), false, true,
            testCrop = TestCrop.LEFT_THIRD),
        BOUNTY_DETAIL("悬赏详情/队伍房间", listOf(
            ScreenState.READY_BUTTON, ScreenState.DAILY_LIMIT
        ), true, false,
            testCrop = TestCrop.TOP_EIGHTH),
        BATTLE_LOADING("战斗加载", listOf(ScreenState.BATTLE_LOADING), false, false),
        FIGHT("战斗", listOf(
            ScreenState.SLIDE_BUTTON, ScreenState.ULTIMATE_SKILL, ScreenState.LV_ICON,
            ScreenState.SETTLEMENT_POPUP, ScreenState.CONFIRM_BUTTON,
            ScreenState.JUMP_BUTTON, ScreenState.SCROLL_UP,
            ScreenState.BLOOD_CURSE
        ), false, false),
        SETTLEMENT("结算领奖", listOf(
            ScreenState.SETTLEMENT_POPUP, ScreenState.CONFIRM_BUTTON, ScreenState.CHAT_ICON
        ), false, false),
        DEFEAT("战斗失败", listOf(ScreenState.DEFEAT_SCREEN, ScreenState.DEFEAT_BACK_BUTTON, ScreenState.ASSIST_BUTTON), false, false),
        INVITATION("组队邀请弹窗", listOf(
            ScreenState.TEAM_INVITATION, ScreenState.INVITE_REJECT
        ), false, false),
        PERSONAL_BOUNTY_ENTRY("个人悬赏入口", listOf(
            ScreenState.PERSONAL_BOUNTY_ENTRY, ScreenState.CHAT_ICON
        ), false, false),
        PERSONAL_BOUNTY_LIST("个人悬赏列表", listOf(), false, false,
            templateDir = "templates/private_bounty_list",
            testCrop = TestCrop.FULL),
        PERSONAL_BOUNTY_DETAIL("个人悬赏详情", listOf(), false, false,
            templateDir = "templates/private_bounty_detail",
            testCrop = TestCrop.TOP_EIGHTH),
    }

    /**
     * 按节点分组测试模板匹配 — 与实际识别使用完全相同的管线
     * @param screen 当前屏幕截图
     * @param group 要测试的节点模板组
     * @return 按相似度降序排列的测试结果
     */
    fun testNodeTemplates(screen: Bitmap, group: NodeTemplateGroup): List<TemplateTestResult> {
        val results = mutableListOf<TemplateTestResult>()

        // 转 Mat → 按 group.testCrop 裁剪（与实际识别一致）
        val screenMat = OpenCVUtil.bitmapToMat(screen)
        val testMat = when (group.testCrop) {
            TestCrop.LEFT_THIRD -> cropLeftThird(screenMat)
            TestCrop.TOP_EIGHTH -> {
                val h = screenMat.rows() / 8
                Mat(screenMat, org.opencv.core.Rect(0, 0, screenMat.cols(), h))
            }
            TestCrop.FULL -> screenMat
        }

        try {
            // 目录模式：加载目录下所有 PNG，用 Mat 匹配
            if (group.templateDir != null) {
                return testDirectoryTemplatesMat(testMat, group.templateDir, group.displayName)
            }

            // 1. 测试 ScreenState 模板（Mat 匹配，与实际一致）
            for (state in group.states) {
                val entry = templates[state] ?: continue
                val template = getTemplate(state)
                if (template == null) {
                    results.add(TemplateTestResult(state.description, 0f, entry.threshold, false, null, null))
                    continue
                }
                val result = TemplateMatcher.matchWithMat(testMat, template, entry.threshold)
                results.add(TemplateTestResult(
                    name = state.description,
                    similarity = result.similarity,
                    threshold = entry.threshold,
                    passed = result.isMatched,
                    centerX = if (result.isMatched) result.centerX else null,
                    centerY = if (result.isMatched) result.centerY else null
                ))
            }

            // 2. 测试等级图标（levelIcon）— 裁剪区域内匹配
            if (group.testLevelIcons) {
                for (grade in BountyGrade.entries) {
                    val paths = grade.levelIconPaths()
                    for (path in paths) {
                        val level = path.removePrefix("templates/team_room/lv").removeSuffix(".png").toIntOrNull() ?: continue
                        val cacheKey = "${grade.key}_$level"
                        val cached = levelIconCache[cacheKey]
                        val template = if (cached != null && !cached.isRecycled) cached else {
                            val loaded = AssetUtil.loadBitmapFromAssets(context, path) ?: continue
                            levelIconCache[cacheKey] = loaded
                            loaded
                        }
                        val result = TemplateMatcher.matchWithMat(testMat, template, 0.92f)
                        results.add(TemplateTestResult(
                            name = "等级 ${grade.displayName}(lv$level)",
                            similarity = result.similarity,
                            threshold = 0.92f,
                            passed = result.isMatched,
                            centerX = if (result.isMatched) result.centerX else null,
                            centerY = if (result.isMatched) result.centerY else null
                        ))
                    }
                }
            }

            // 3. 测试招募列表等级图标（gradeIcon）— 裁剪区域内匹配
            if (group.testGradeIcons) {
                for (grade in BountyGrade.entries) {
                    val cached = gradeIconCache[grade]
                    val template = if (cached != null && !cached.isRecycled) cached else {
                        val loaded = AssetUtil.loadBitmapFromAssets(context, grade.gradeIconPath()) ?: continue
                        gradeIconCache[grade] = loaded
                        loaded
                    }
                    val result = TemplateMatcher.matchWithMat(testMat, template, 0.85f)
                    results.add(TemplateTestResult(
                        name = "招募 ${grade.displayName}",
                        similarity = result.similarity,
                        threshold = 0.85f,
                        passed = result.isMatched,
                        centerX = if (result.isMatched) result.centerX else null,
                        centerY = if (result.isMatched) result.centerY else null
                    ))
                }
            }
        } finally {
            if (testMat !== screenMat) testMat.release()
            screenMat.release()
        }

        return results.sortedByDescending { it.similarity }
    }

    /** 加载指定 assets 目录下所有 PNG，用 Mat 匹配（与实际识别管线一致） */
    private fun testDirectoryTemplatesMat(testMat: org.opencv.core.Mat, dir: String, groupDisplayName: String): List<TemplateTestResult> {
        val results = mutableListOf<TemplateTestResult>()
        try {
            val files = context.assets.list(dir) ?: emptyArray()
            val pngFiles = files.filter { it.endsWith(".png", ignoreCase = true) }.sorted()
            if (pngFiles.isEmpty()) {
                LogUtil.w(TAG, "testDirectoryTemplatesMat: $dir 下无 PNG 文件")
                return results
            }
            for (fileName in pngFiles) {
                val template = AssetUtil.loadBitmapFromAssets(context, "$dir/$fileName")
                if (template == null) {
                    results.add(TemplateTestResult(fileName, 0f, DEFAULT_THRESHOLD, false, null, null))
                    continue
                }
                val result = TemplateMatcher.matchWithMat(testMat, template, DEFAULT_THRESHOLD)
                results.add(TemplateTestResult(
                    name = fileName.removeSuffix(".png"),
                    similarity = result.similarity,
                    threshold = DEFAULT_THRESHOLD,
                    passed = result.isMatched,
                    centerX = if (result.isMatched) result.centerX else null,
                    centerY = if (result.isMatched) result.centerY else null
                ))
                template.recycle()
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "testDirectoryTemplatesMat 失败: $dir", e)
        }
        return results.sortedByDescending { it.similarity }
    }

    private val templates: Map<ScreenState, TemplateEntry> = mapOf(
        // ── 大厅 ──
        ScreenState.CHAT_ICON to TemplateEntry("templates/lobby/lobby_chat.png", 0.75f),
        // ── 聊天/招募 ──
        ScreenState.RECRUIT_TAB to TemplateEntry("templates/chat/team_recruit.png"),
        ScreenState.OUT_OF_RANGE_RECRUIT to TemplateEntry("templates/bounty_list/out_of_range.png", 0.7f),
        ScreenState.RECRUIT_LIST_SCREEN to TemplateEntry("templates/bounty_list/team_recruit_black.png", 0.75f),
        // ── 入队 ──
        ScreenState.READY_BUTTON to TemplateEntry("templates/team_room/prepare.png"),
        ScreenState.EXIT_CONFIRM to TemplateEntry("templates/team_room/confirm.png", 0.65f),
        ScreenState.DAILY_LIMIT to TemplateEntry("templates/team_room/daily_limit.png"),
        // ── 战斗 ──
        ScreenState.BATTLE_LOADING to TemplateEntry("templates/battle_loading/smile.png"),
        ScreenState.SLIDE_BUTTON to TemplateEntry("templates/fight/slide.png"),
        ScreenState.LV_ICON to TemplateEntry("templates/fight/lv.png"),
        ScreenState.JUMP_BUTTON to TemplateEntry("templates/fight/jump.png"),
        ScreenState.SCROLL_UP to TemplateEntry("templates/fight/scroll_up.png"),
        ScreenState.ULTIMATE_SKILL to TemplateEntry("templates/fight/role/shihara/r_shihara.png", 0.6f),
        ScreenState.BLOOD_CURSE to TemplateEntry("templates/fight/role/shihara/blood_curse.png", 0.85f),
        ScreenState.DEFEAT_SCREEN to TemplateEntry("templates/defeat/defeat.png", 0.8f),
        ScreenState.DEFEAT_BACK_BUTTON to TemplateEntry("templates/defeat/back_button.png", 0.8f),
        ScreenState.DEFEAT_CONFIRM to TemplateEntry("templates/defeat/confirm.png", 0.8f),
        ScreenState.DEFEAT_SKIP to TemplateEntry("templates/defeat/defeat_skip.png", 0.8f),
        ScreenState.ASSIST_BUTTON to TemplateEntry("templates/defeat/assist_button.png", 0.7f),
        // ── 结算 ──
        ScreenState.SETTLEMENT_POPUP to TemplateEntry("templates/settlement/black.png", 0.7f),
        ScreenState.CONFIRM_BUTTON to TemplateEntry("templates/settlement/confirm.png"),
        // ── 通用 ──
        ScreenState.BACK_BUTTON to TemplateEntry("templates/other/backward.png", 0.5f),
        // ── 组队邀请弹窗（任意节点可触发） ──
        ScreenState.TEAM_INVITATION to TemplateEntry("templates/invitation/team_invitation.png", 0.75f),
        ScreenState.INVITE_REJECT to TemplateEntry("templates/invitation/reject_btn.png", 0.75f),
        // ── 个人悬赏 ──
        ScreenState.PERSONAL_BOUNTY_ENTRY to TemplateEntry("templates/lobby/private_bounty.png", 0.78f),
        ScreenState.PERSONAL_BOUNTY_LIST_SCREEN to TemplateEntry("templates/bounty_list_personal/bounty_shop.png", 0.85f),
        ScreenState.PERSONAL_BOUNTY_DETAIL_SCREEN to TemplateEntry("templates/private_bounty_detail/team_invitation.png", 0.85f),
        ScreenState.PERSONAL_BOUNTY_SEND_MSG to TemplateEntry("templates/private_bounty_detail/send_message.png", 0.85f),
        ScreenState.PERSONAL_BOUNTY_GO to TemplateEntry("templates/private_bounty_detail/go.png", 0.85f),
    )

    companion object {
        private const val DEFAULT_THRESHOLD = 0.8f
    }

    // ── 模板缓存（线程安全） ──
    private val templateCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    private val templateMatCache = java.util.concurrent.ConcurrentHashMap<String, Mat>()
    private val gradeIconCache = java.util.concurrent.ConcurrentHashMap<BountyGrade, Bitmap>()
    private val gradeIconMatCache = java.util.concurrent.ConcurrentHashMap<BountyGrade, Mat>()
    private val personalGradeIconCache = java.util.concurrent.ConcurrentHashMap<BountyGrade, Bitmap>()
    private val levelIconCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    private data class GradeResult(val grade: BountyGrade, val similarity: Float, val centerX: Float, val centerY: Float)

    fun getTemplate(state: ScreenState): Bitmap? {
        val entry = templates[state] ?: return null
        val cached = templateCache[entry.path]
        if (cached != null && !cached.isRecycled) return cached
        val loaded = AssetUtil.loadBitmapFromAssets(context, entry.path) ?: return null
        templateCache[entry.path] = loaded
        return loaded
    }

    /** 获取模板的 Mat 缓存（用于灰度匹配等需要 Mat 的场景） */
    fun getTemplateMat(state: ScreenState): Mat? {
        val entry = templates[state] ?: return null
        val cached = templateMatCache[entry.path]
        if (cached != null && !cached.empty()) return cached
        val bitmap = getTemplate(state) ?: return null
        val mat = OpenCVUtil.bitmapToMat(bitmap)
        templateMatCache[entry.path] = mat
        return mat
    }

    // ── 公开检测方法 ──

    /** 对指定状态做模板匹配，返回命中坐标（中心点）或 null */
    fun matchTemplate(screen: Bitmap, state: ScreenState): Pair<Float, Float>? {
        if (state == ScreenState.UNKNOWN) return null
        val entry = templates[state]
        if (entry == null) {
            LogUtil.w(TAG, "⚠ 跳过 $state: 无模板配置")
            return null
        }
        val template = getTemplate(state)
        if (template == null) {
            LogUtil.w(TAG, "⚠ $state: 模板图片读取失败 -> ${entry.path}")
            return null
        }
        val result = TemplateMatcher.match(screen, template, entry.threshold)
        if (result.isMatched) {
            LogUtil.i(TAG, "$state: 相似度 ${String.format("%.2f", result.similarity)} ≥ ${entry.threshold}")
            return Pair(result.centerX, result.centerY)
        }else {
            LogUtil.d(TAG, "$state: 最高相似度 = ${String.format("%.2f", result.similarity)} (阈值=${entry.threshold})")
        }
        return null
    }

    // ── Mat 预转换方法（避免重复 Bitmap→Mat） ──

    /** 将 screen Bitmap 转为 Mat，调用方用完需 release */
    fun screenToMat(screen: Bitmap): Mat {
        OpenCVUtil.initOpenCV()
        return OpenCVUtil.bitmapToMat(screen)
    }

    /** 裁剪 Mat 左下角 1/3 区域（超出范围标识所在区域），调用方用完需 release */
    fun cropBottomLeft(mat: Mat): Mat {
        val w = mat.cols() / 3
        val h = mat.rows() / 3
        val y = mat.rows() - h
        return Mat(mat, org.opencv.core.Rect(0, y, w, h))
    }

    /** 裁剪 Mat 左半边下方 1/5 区域（超出范围标识所在区域），调用方用完需 release */
    fun cropBottomLeftFifth(mat: Mat): Mat {
        val w = mat.cols() / 2
        val h = mat.rows() / 5
        val y = mat.rows() - h
        return Mat(mat, org.opencv.core.Rect(0, y, w, h))
    }

    /** 裁剪 Mat 左侧 1/3 区域（等级图标所在区域），高度不动，调用方用完需 release */
    fun cropLeftThird(mat: Mat): Mat {
        val w = mat.cols() / 3
        return Mat(mat, org.opencv.core.Rect(0, 0, w, mat.rows()))
    }

    /** 裁剪 Mat 左下 1/3 区域（左侧1/3 × 下方1/3），调用方用完需 release */
    fun cropBottomLeftThird(mat: Mat): Mat {
        val w = mat.cols() / 3
        val h = mat.rows() / 3
        val y = mat.rows() - h
        return Mat(mat, org.opencv.core.Rect(0, y, w, h))
    }

    /** 裁剪 Mat 下方 1/5 中间 1/3 区域（确认按钮所在区域），调用方用完需 release */
    fun cropBottomMiddleFifth(mat: Mat): Mat {
        val w = mat.cols() / 3
        val x = mat.cols() / 3
        val h = mat.rows() / 5
        val y = mat.rows() - h
        return Mat(mat, org.opencv.core.Rect(x, y, w, h))
    }

    /** 裁剪 Mat 左侧第 3 个十分之一区域（x=20%~30%，等级图标所在区域），高度不动，调用方用完需 release */
    fun cropLeftMidTenth(mat: Mat): Mat {
        val w = mat.cols()
        val x = w * 2 / 10
        val segW = w / 10
        return Mat(mat, org.opencv.core.Rect(x, 0, segW, mat.rows()))
    }

    /** 裁剪 Mat 左半边下方 1/4 区域（下滑按钮所在区域），调用方用完需 release */
    fun cropBottomLeftQuarter(mat: Mat): Mat {
        val w = mat.cols() / 2
        val h = mat.rows() / 4
        val y = mat.rows() - h
        return Mat(mat, org.opencv.core.Rect(0, y, w, h))
    }

    /** 裁剪 Mat 右半边下方 1/4 区域（跳跃按钮所在区域），调用方用完需 release */
    fun cropBottomRightQuarter(mat: Mat): Mat {
        val w = mat.cols() / 2
        val x = mat.cols() / 2
        val h = mat.rows() / 4
        val y = mat.rows() - h
        return Mat(mat, org.opencv.core.Rect(x, y, w, h))
    }

    /** 裁剪 Mat 右半边下方 1/2 区域（退出确认弹窗所在区域），调用方用完需 release */
    fun cropBottomRightHalf(mat: Mat): Mat {
        val w = mat.cols() / 2
        val x = mat.cols() / 2
        val h = mat.rows() / 2
        val y = mat.rows() / 2
        return Mat(mat, org.opencv.core.Rect(x, y, w, h))
    }

    /** 裁剪 Mat 左侧 1/4 宽度、下方 1/4 高度（下滑按钮精确区域），调用方用完需 release */
    fun cropBottomLeftFourth(mat: Mat): Mat {
        val w = mat.cols() / 4
        val h = mat.rows() / 4
        val y = mat.rows() - h
        return Mat(mat, org.opencv.core.Rect(0, y, w, h))
    }

    /** 裁剪 Mat 左侧 1/2 宽度、下方 1/4 高度（血咒技能所在区域），调用方用完需 release */
    fun cropBottomLeftHalf(mat: Mat): Mat {
        val w = mat.cols() / 2
        val h = mat.rows() / 4
        val y = mat.rows() - h
        return Mat(mat, org.opencv.core.Rect(0, y, w, h))
    }

    /** 裁剪 Mat 右侧 1/4 宽度、下方 1/4 高度（跳跃按钮精确区域），调用方用完需 release */
    fun cropBottomRightFourth(mat: Mat): Mat {
        val w = mat.cols() / 4
        val x = mat.cols() * 3 / 4
        val h = mat.rows() / 4
        val y = mat.rows() - h
        return Mat(mat, org.opencv.core.Rect(x, y, w, h))
    }

    /** 裁剪 Mat 左侧 1/6 宽度、中间 1/3 高度（大招按钮所在区域），调用方用完需 release */
    fun cropLeftSixth(mat: Mat): Mat {
        val w = mat.cols() / 6
        val h = mat.rows() / 3
        val y = mat.rows() / 3
        return Mat(mat, org.opencv.core.Rect(0, y, w, h))
    }

    /** 裁剪 Mat 下方中心 1/9 区域（失败界面返回按钮所在区域），调用方用完需 release */
    fun cropBottomCenterNinth(mat: Mat): Mat {
        val w = mat.cols() / 3
        val x = mat.cols() / 3
        val h = mat.rows() / 9
        val y = mat.rows() - h
        return Mat(mat, org.opencv.core.Rect(x, y, w, h))
    }

    /** 裁剪 Mat 顶部中间 1/2 宽度、1/8 高度（组队招募页签所在区域），调用方用完需 release */
    fun cropTopCenterQuarter(mat: Mat): Mat {
        val w = mat.cols() / 2
        val x = mat.cols() / 4
        val h = mat.rows() / 8
        return Mat(mat, org.opencv.core.Rect(x, 0, w, h))
    }

    /** 裁剪 Mat 左半边上 1/8 区域（Boss Lv图标所在区域），调用方用完需 release */
    fun cropTopLeftEighth(mat: Mat): Mat {
        val w = mat.cols() / 2
        val h = mat.rows() / 8
        return Mat(mat, org.opencv.core.Rect(0, 0, w, h))
    }

    /** 裁剪 Mat 左侧 1/10 区域（聊天按钮所在区域），高度不动，调用方用完需 release */
    fun cropLeftTenth(mat: Mat): Mat {
        val w = mat.cols() / 10
        return Mat(mat, org.opencv.core.Rect(0, 0, w, mat.rows()))
    }

    /** 裁剪 Mat 上方 1/10 区域（招募页签所在区域），全宽，调用方用完需 release */
    fun cropTopTenth(mat: Mat): Mat {
        val h = mat.rows() / 10
        return Mat(mat, org.opencv.core.Rect(0, 0, mat.cols(), h))
    }

    /** 裁剪 Mat 上方 1/4 区域（加载标识所在区域），全宽，调用方用完需 release */
    fun cropTopQuarter(mat: Mat): Mat {
        val h = mat.rows() / 4
        return Mat(mat, org.opencv.core.Rect(0, 0, mat.cols(), h))
    }

    /** 裁剪 Mat 上方 1/5 区域（上限标识所在区域），全宽，调用方用完需 release */
    fun cropTopFifth(mat: Mat): Mat {
        val h = mat.rows() / 5
        return Mat(mat, org.opencv.core.Rect(0, 0, mat.cols(), h))
    }

    /** 裁剪 Mat 上方 1/10 高度、中间 1/3 宽度（等级图标所在区域），调用方用完需 release */
    fun cropTopMiddleTenth(mat: Mat): Mat {
        val h = mat.rows() / 10
        val w = mat.cols() / 3
        val x = mat.cols() / 3
        return Mat(mat, org.opencv.core.Rect(x, 0, w, h))
    }

    /** 裁剪 Mat 上方约 9%~27% 高度、中间约 28%~58% 宽度（个人悬赏等级文字所在区域），调用方用完需 release */
    fun cropPersonalBountyGradeArea(mat: Mat): Mat {
        val x = (mat.cols() * 0.28).toInt()
        val w = (mat.cols() * 0.30).toInt()
        val y = (mat.rows() * 0.09).toInt()
        val h = (mat.rows() * 0.18).toInt()
        return Mat(mat, org.opencv.core.Rect(x, y, w, h))
    }

    /** 裁剪 Mat 右侧约 50%~82% 宽度、中间约 40%~75% 高度（大厅悬赏令入口所在区域），调用方用完需 release */
    fun cropLobbyPersonalBountyEntry(mat: Mat): Mat {
        val x = (mat.cols() * 0.50).toInt()
        val w = (mat.cols() * 0.32).toInt()
        val y = (mat.rows() * 0.40).toInt()
        val h = (mat.rows() * 0.35).toInt()
        return Mat(mat, org.opencv.core.Rect(x, y, w, h))
    }

    /** 裁剪 Mat 右侧约 53%~75% 宽度、下方约 87%~97% 高度（组队邀请按钮所在区域），调用方用完需 release */
    fun cropPersonalBountyTeamInvite(mat: Mat): Mat {
        val x = (mat.cols() * 0.53).toInt()
        val w = (mat.cols() * 0.22).toInt()
        val y = (mat.rows() * 0.87).toInt()
        val h = (mat.rows() * 0.10).toInt()
        return Mat(mat, org.opencv.core.Rect(x, y, w, h))
    }

    /** 裁剪 Mat 中间约 30%~70% 宽度、下方约 72%~95% 高度（发送消息按钮所在区域），调用方用完需 release */
    fun cropPersonalBountySendMessage(mat: Mat): Mat {
        val x = (mat.cols() * 0.30).toInt()
        val w = (mat.cols() * 0.40).toInt()
        val y = (mat.rows() * 0.72).toInt()
        val h = (mat.rows() * 0.23).toInt()
        return Mat(mat, org.opencv.core.Rect(x, y, w, h))
    }

    /** 裁剪 Mat 右侧约 74%~98% 宽度、下方约 82%~98% 高度（出发按钮所在区域），调用方用完需 release */
    fun cropPersonalBountyGo(mat: Mat): Mat {
        val x = (mat.cols() * 0.74).toInt()
        val w = (mat.cols() * 0.24).toInt()
        val y = (mat.rows() * 0.82).toInt()
        val h = (mat.rows() * 0.16).toInt()
        return Mat(mat, org.opencv.core.Rect(x, y, w, h))
    }

    /** 使用预转换的 screen Mat 匹配指定状态，返回命中坐标或 null */
    fun matchTemplateMat(screenMat: Mat, state: ScreenState): Pair<Float, Float>? {
        if (state == ScreenState.UNKNOWN) return null
        val entry = templates[state] ?: return null
        val template = getTemplate(state) ?: return null
        val result = TemplateMatcher.matchWithMat(screenMat, template, entry.threshold)
        if (result.isMatched) {
            LogUtil.i(TAG, "$state: 相似度 ${String.format("%.2f", result.similarity)} ≥ ${entry.threshold}")
            return Pair(result.centerX, result.centerY)
        }
        LogUtil.d(TAG, "$state: 相似度 ${String.format("%.2f", result.similarity)} < ${entry.threshold} (未匹配)")
        return null
    }

    /** 使用预转换的 screen Mat 并行搜索等级图标 — 选最佳匹配（相似度最高） */
    suspend fun matchAnyGradeMat(screenMat: Mat, grades: List<BountyGrade>): Pair<BountyGrade, Pair<Float, Float>>? {
        data class GradeTemplate(val grade: BountyGrade, val templateMat: Mat, val w: Int, val h: Int)

        val templates = grades.mapNotNull { grade ->
            val cachedMat = gradeIconMatCache[grade]
            val mat = if (cachedMat != null && !cachedMat.empty()) cachedMat else {
                val cachedBmp = gradeIconCache[grade]
                val bitmap = if (cachedBmp != null && !cachedBmp.isRecycled) cachedBmp else {
                    val loaded = AssetUtil.loadBitmapFromAssets(context, grade.gradeIconPath()) ?: return@mapNotNull null
                    gradeIconCache[grade] = loaded
                    loaded
                }
                val m = OpenCVUtil.bitmapToMat(bitmap)
                gradeIconMatCache[grade] = m
                m
            }
            GradeTemplate(grade, mat, mat.cols(), mat.rows())
        }

        return coroutineScope {
            val results = templates.map { t ->
                async {
                    val threshold = when {
                        t.grade == BountyGrade.NS || t.grade == BountyGrade.NA -> 0.90f
                        t.grade == BountyGrade.S || t.grade == BountyGrade.S_PLUS || t.grade == BountyGrade.SS_PLUS -> 0.92f
                        else -> 0.85f
                    }
                    val result = TemplateMatcher.matchMatWithMatGrayscale(screenMat, t.templateMat, threshold, t.w, t.h)
                    if (result.isMatched) {
                        LogUtil.i(TAG, "matchAnyGradeMat: ${t.grade.displayName} 相似度=${String.format("%.3f", result.similarity)} ✓")
                        GradeResult(t.grade, result.similarity, result.centerX, result.centerY)
                    } else null
                }
            }.awaitAll().filterNotNull()

            if (results.isNotEmpty()) {
                val matches = results.map { GradeMatch(it.grade, it.similarity, it.centerX, it.centerY) }.toMutableList()
                val best = bestMatch(matches, "matchAnyGradeMat")
                if (best != null) Pair(best.grade, Pair(best.centerX, best.centerY)) else null
            } else {
                LogUtil.d(TAG, "matchAnyGradeMat: ${grades.size}个等级均未匹配")
                null
            }
        }
    }

    /** Mat 版等级图标匹配 — 避免每个等级重复 Bitmap→Mat 转换 */
    fun matchLevelIconMat(screenMat: Mat, grade: BountyGrade): GradeMatch? {
        val paths = grade.levelIconPaths()
        if (paths.isEmpty()) return null

        var bestMatch: GradeMatch? = null
        for (path in paths) {
            val level = path.removePrefix("templates/team_room/lv").removeSuffix(".png").toIntOrNull() ?: continue
            val cacheKey = "${grade.key}_$level"
            val cached = levelIconCache[cacheKey]
            val template = if (cached != null && !cached.isRecycled) cached else {
                val loaded = AssetUtil.loadBitmapFromAssets(context, path) ?: run {
                    LogUtil.e(TAG, "等级图标模板加载失败: $path")
                    return@run null
                }
                if (loaded != null) levelIconCache[cacheKey] = loaded
                loaded
            } ?: continue
            val result = TemplateMatcher.matchWithMat(screenMat, template, 0.85f)
            LogUtil.i(TAG, "等级图标 ${grade.displayName}(lv$level): 相似度=${String.format("%.3f", result.similarity)} 阈值=0.850 ${if (result.isMatched) "✓" else "✗"}")
            if (result.isMatched) {
                val match = GradeMatch(grade, result.similarity, result.centerX, result.centerY)
                if (bestMatch == null || result.similarity > bestMatch!!.similarity) {
                    bestMatch = match
                }
            }
        }
        if (bestMatch == null) {
            LogUtil.d(TAG, "等级图标 ${grade.displayName}: 所有级别变体均未匹配")
        }
        return bestMatch
    }

    /** Mat 版多等级并行匹配 — 一次 Mat 转换，所有等级并行匹配 */
    suspend fun matchAnyLevelIconMat(screenMat: Mat, grades: List<BountyGrade>): GradeMatch? {
        LogUtil.i(TAG, "matchAnyLevelIconMat: 检查 ${grades.joinToString { "${it.displayName}(lv${it.level})" }}")
        return coroutineScope {
            val matches = grades.map { grade ->
                async { matchLevelIconMat(screenMat, grade) }
            }.awaitAll().filterNotNull()
            bestMatch(matches.toMutableList(), "matchAnyLevelIconMat")
        }
    }

    /** 在截图中搜索个人悬赏等级图标 — 裁剪等级区域后匹配，大幅提升速度 */
    fun matchAnyPersonalGradeIcon(screen: Bitmap, grades: List<BountyGrade>): GradeMatch? {
        var screenMat: Mat? = null
        var gradeAreaMat: Mat? = null
        try {
            screenMat = OpenCVUtil.bitmapToMat(screen)
            gradeAreaMat = cropPersonalBountyGradeArea(screenMat)
            val cropX = (screenMat.cols() * 0.28).toFloat()
            val cropY = (screenMat.rows() * 0.09).toFloat()

            for (grade in grades) {
                val cached = personalGradeIconCache[grade]
                val template = if (cached != null && !cached.isRecycled) cached else {
                    val loaded = AssetUtil.loadBitmapFromAssets(context, grade.personalGradeIconPath()) ?: continue
                    personalGradeIconCache[grade] = loaded
                    loaded
                }
                val result = TemplateMatcher.matchWithMat(gradeAreaMat, template, 0.85f)
                if (result.isMatched) {
                    val match = GradeMatch(grade, result.similarity, result.centerX + cropX, result.centerY + cropY)
                    LogUtil.d(TAG, "matchAnyPersonalGradeIcon → 选中 ${grade.displayName} 相似度=${String.format("%.2f", result.similarity)}")
                    return match
                }
            }
        } finally {
            gradeAreaMat?.release()
            screenMat?.release()
        }
        LogUtil.d(TAG, "matchAnyPersonalGradeIcon: 无匹配")
        return null
    }

    /** 从多个 GradeMatch 中选出最佳匹配，含冲突检测 */
    private fun bestMatch(matches: MutableList<GradeMatch>, tag: String): GradeMatch? {
        if (matches.isEmpty()) {
            LogUtil.d(TAG, "$tag: 无匹配")
            return null
        }
        // 相似度优先，相近时取屏幕下方（Y更大）的 — 新悬赏从下方出现
        matches.sortWith(compareByDescending<GradeMatch> { it.similarity }.thenByDescending { it.centerY })
        val best = matches.first()
        if (matches.size > 1) {
            val summary = matches.joinToString { "${it.grade.displayName}=${String.format("%.2f", it.similarity)}(y=${String.format("%.0f", it.centerY)})" }
            val gap = best.similarity - matches[1].similarity
            LogUtil.w(TAG, "$tag: 多个等级匹配 - $summary，最佳=${best.grade.displayName}，差距=${String.format("%.3f", gap)}")
            if (gap < 0.02f) {
                LogUtil.w(TAG, "$tag: 警告! 差距仅 ${String.format("%.3f", gap)}，匹配可能不可靠")
            }
        }
        LogUtil.i(TAG, "$tag → 选中 ${best.grade.displayName} 相似度=${String.format("%.2f", best.similarity)}")
        return best
    }

}
