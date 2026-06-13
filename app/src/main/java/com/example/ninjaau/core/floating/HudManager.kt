package com.example.ninjaau.core.floating

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.ninjaau.model.BountyGrade
import com.example.ninjaau.model.GradeGroup

class HudManager(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var hudRoot: LinearLayout? = null
    private var hudContent: LinearLayout? = null
    private var hudLayoutParams: WindowManager.LayoutParams? = null
    private var isVisible = false

    /** 显示顺序：日常高等级 → 日常低等级 → 活动悬赏 */
    private val displayOrder = listOf(
        GradeGroup.SS_PLUS, GradeGroup.SS, GradeGroup.S_GROUP,
        GradeGroup.A_GROUP, GradeGroup.B, GradeGroup.C, GradeGroup.D,
        GradeGroup.NSS_PLUS, GradeGroup.NS, GradeGroup.NA
    )

    init {
        val density = context.resources.displayMetrics.density
        val statusBarRes = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val minY = if (statusBarRes > 0) context.resources.getDimensionPixelSize(statusBarRes) else 60

        val screenWidth = context.resources.displayMetrics.widthPixels
        val maxW = (screenWidth * 0.36).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(0xDD1E1E1E.toInt())
                cornerRadius = 10f * density
            }
            background = bg
            setPadding(
                (12 * density).toInt(), (8 * density).toInt(),
                (12 * density).toInt(), (6 * density).toInt()
            )
        }

        val title = TextView(context).apply {
            text = "◆ 进度"
            setTextColor(0xFF8AB4F8.toInt())
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, (4 * density).toInt())
        }
        root.addView(title)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(content)
        hudContent = content
        hudRoot = root

        hudLayoutParams = WindowManager.LayoutParams(
            maxW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (12 * density).toInt()
            y = minY + (50 * density).toInt()
            alpha = 0.9f
        }
    }

    fun show() {
        if (isVisible) return
        val root = hudRoot ?: return
        val params = hudLayoutParams ?: return
        if (root.parent == null) {
            try {
                windowManager.addView(root, params)
            } catch (_: Exception) { return }
        }
        root.animate().alpha(0.9f).setDuration(200).start()
        root.visibility = View.VISIBLE
        isVisible = true
    }

    fun hide() {
        if (!isVisible) return
        val root = hudRoot ?: return
        root.animate().alpha(0f).setDuration(150).withEndAction {
            if (root.parent != null) {
                try { windowManager.removeView(root) } catch (_: Exception) {}
            }
            root.visibility = View.GONE
        }.start()
        isVisible = false
    }

    fun updateProgress(progress: Map<BountyGrade, Pair<Int, Int>>) {
        val container = hudContent ?: return
        container.removeAllViews()

        if (progress.isEmpty()) {
            container.addView(makeText("暂无"))
            return
        }

        val density = context.resources.displayMetrics.density
        for (group in displayOrder) {
            val members = group.members().filter { progress.containsKey(it) }
            if (members.isEmpty()) continue
            val totalCompleted = members.sumOf { progress[it]?.first ?: 0 }
            val target = members.firstNotNullOfOrNull { progress[it]?.second } ?: continue
            if (target <= 0) continue
            val done = totalCompleted >= target
            val displayName = when (group) {
                GradeGroup.A_GROUP -> "A"
                GradeGroup.S_GROUP -> "S"
                GradeGroup.SS_PLUS -> "SS+"
                GradeGroup.SS -> "SS"
                GradeGroup.NSS_PLUS -> "NSS+"
                GradeGroup.NS -> "NS"
                GradeGroup.NA -> "NA"
                else -> group.name
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (1 * density).toInt(), 0, (1 * density).toInt())
            }
            row.addView(makeText(displayName).apply {
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFFE0E0E0.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(makeText("$totalCompleted/$target").apply {
                setTextColor(if (done) 0xFF4EC9B0.toInt() else 0xFF969696.toInt())
                setTypeface(Typeface.MONOSPACE)
                gravity = Gravity.END
            })
            if (done) {
                row.addView(makeText(" ✓").apply {
                    setTextColor(0xFF4EC9B0.toInt())
                })
            }
            container.addView(row)
        }
    }

    private fun makeText(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(0xFFE0E0E0.toInt())
        }
    }
}
