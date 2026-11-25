package com.NASJEProduction.NASJEIDE.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.NASJEProduction.NASJEIDE.R
import com.NASJEProduction.NASJEIDE.model.IdeLanguage

class NasjePalette(val isDark: Boolean) {
    val bgDeep: Int = if (isDark) 0xFF060A14.toInt() else 0xFFF1F5F9.toInt()
    val bgPrimary: Int = if (isDark) 0xFF0B1120.toInt() else 0xFFF8FAFC.toInt()
    val surface: Int = if (isDark) 0xFF111A2E.toInt() else 0xFFFFFFFF.toInt()
    val surfaceElevated: Int = if (isDark) 0xFF16223D.toInt() else 0xFFE2E8F0.toInt()
    val surfaceBorder: Int = if (isDark) 0xFF23355A.toInt() else 0xFFCBD5E1.toInt()
    val topBarBg: Int = if (isDark) 0xFF0D1528.toInt() else 0xFFFFFFFF.toInt()
    val editorBg: Int = if (isDark) 0xFF080D1A.toInt() else 0xFFFFFFFF.toInt()
    val gutterBg: Int = if (isDark) 0xFF0D1426.toInt() else 0xFFF1F5F9.toInt()

    val blue: Int = 0xFF3B82F6.toInt()
    val violet: Int = 0xFF8B5CF6.toInt()
    val cyan: Int = 0xFF38BDF8.toInt()
    val green: Int = 0xFF22C55E.toInt()
    val greenDark: Int = 0xFF16A34A.toInt()
    val amber: Int = 0xFFF59E0B.toInt()
    val red: Int = 0xFFEF4444.toInt()

    val textPrimary: Int = if (isDark) 0xFFF8FAFC.toInt() else 0xFF0F172A.toInt()
    val textSecondary: Int = if (isDark) 0xFF94A3B8.toInt() else 0xFF475569.toInt()
    val textMuted: Int = if (isDark) 0xFF64748B.toInt() else 0xFF64748B.toInt()
    val divider: Int = if (isDark) 0xFF1E293B.toInt() else 0xFFE2E8F0.toInt()
    val ripple: Int = if (isDark) 0x3338BDF8 else 0x223B82F6
}

object FontKit {
    private var displayFont: Typeface? = null
    private var bodyFont: Typeface? = null
    private var monoFont: Typeface? = null

    fun display(context: Context): Typeface {
        displayFont?.let { return it }
        val loaded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.resources.getFont(R.font.space_grotesk) }.getOrNull()
        } else null
        return (loaded ?: Typeface.DEFAULT_BOLD).also { displayFont = it }
    }

    fun body(context: Context): Typeface {
        bodyFont?.let { return it }
        val loaded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.resources.getFont(R.font.plus_jakarta_sans) }.getOrNull()
        } else null
        return (loaded ?: Typeface.SANS_SERIF).also { bodyFont = it }
    }

    fun mono(context: Context): Typeface {
        monoFont?.let { return it }
        val loaded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.resources.getFont(R.font.jetbrains_mono) }.getOrNull()
        } else null
        return (loaded ?: Typeface.MONOSPACE).also { monoFont = it }
    }

    fun editorTypeface(context: Context, familyName: String): Typeface {
        return when (familyName) {
            "Monospace" -> Typeface.MONOSPACE
            "Sans Serif" -> body(context)
            else -> mono(context)
        }
    }
}

enum class NasjeIcon {
    MENU,
    FOLDER,
    PLAY,
    SAVE,
    MORE,
    SEARCH,
    SORT,
    PLUS,
    BACK,
    CLOSE,
    CHECK,
    CHECK_CIRCLE,
    ERROR,
    WARNING,
    SETTINGS,
    UNDO,
    REDO,
    BUILD,
    DOWNLOAD,
    INSTALL,
    SHARE,
    COPY,
    MOVE,
    DELETE,
    EDIT,
    LAYERS,
    CHEVRON_DOWN,
    CHEVRON_RIGHT,
    FILE,
    PASTE,
    REFRESH
}

class VectorIconDrawable(
    private val icon: NasjeIcon,
    private val tintColor: Int,
    private val sizePx: Int
) : Drawable() {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tintColor
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tintColor
        style = Paint.Style.FILL
    }

    override fun getIntrinsicWidth(): Int = sizePx
    override fun getIntrinsicHeight(): Int = sizePx

    override fun draw(canvas: Canvas) {
        val w = bounds.width().takeIf { it > 0 }?.toFloat() ?: sizePx.toFloat()
        val h = bounds.height().takeIf { it > 0 }?.toFloat() ?: sizePx.toFloat()
        val s = minOf(w, h)
        val ox = bounds.left + (w - s) / 2f
        val oy = bounds.top + (h - s) / 2f
        strokePaint.strokeWidth = (s * 0.095f).coerceAtLeast(1.8f)

        fun x(p: Float) = ox + s * p
        fun y(p: Float) = oy + s * p

        when (icon) {
            NasjeIcon.MENU -> {
                canvas.drawLine(x(0.18f), y(0.28f), x(0.82f), y(0.28f), strokePaint)
                canvas.drawLine(x(0.18f), y(0.50f), x(0.82f), y(0.50f), strokePaint)
                canvas.drawLine(x(0.18f), y(0.72f), x(0.82f), y(0.72f), strokePaint)
            }
            NasjeIcon.FOLDER -> {
                val p = Path().apply {
                    moveTo(x(0.14f), y(0.25f))
                    lineTo(x(0.42f), y(0.25f))
                    lineTo(x(0.50f), y(0.35f))
                    lineTo(x(0.86f), y(0.35f))
                    lineTo(x(0.86f), y(0.78f))
                    lineTo(x(0.14f), y(0.78f))
                    close()
                }
                canvas.drawPath(p, strokePaint)
            }
            NasjeIcon.PLAY -> {
                val p = Path().apply {
                    moveTo(x(0.28f), y(0.20f))
                    lineTo(x(0.80f), y(0.50f))
                    lineTo(x(0.28f), y(0.80f))
                    close()
                }
                canvas.drawPath(p, fillPaint)
            }
            NasjeIcon.SAVE -> {
                val p = Path().apply {
                    moveTo(x(0.18f), y(0.18f))
                    lineTo(x(0.72f), y(0.18f))
                    lineTo(x(0.82f), y(0.28f))
                    lineTo(x(0.82f), y(0.82f))
                    lineTo(x(0.18f), y(0.82f))
                    close()
                }
                canvas.drawPath(p, strokePaint)
                canvas.drawRect(x(0.32f), y(0.18f), x(0.64f), y(0.40f), strokePaint)
                canvas.drawRect(x(0.30f), y(0.56f), x(0.70f), y(0.82f), strokePaint)
            }
            NasjeIcon.MORE -> {
                val r = s * 0.065f
                canvas.drawCircle(x(0.50f), y(0.24f), r, fillPaint)
                canvas.drawCircle(x(0.50f), y(0.50f), r, fillPaint)
                canvas.drawCircle(x(0.50f), y(0.76f), r, fillPaint)
            }
            NasjeIcon.SEARCH -> {
                canvas.drawCircle(x(0.44f), y(0.44f), s * 0.24f, strokePaint)
                canvas.drawLine(x(0.62f), y(0.62f), x(0.82f), y(0.82f), strokePaint)
            }
            NasjeIcon.SORT -> {
                canvas.drawLine(x(0.18f), y(0.30f), x(0.82f), y(0.30f), strokePaint)
                canvas.drawLine(x(0.18f), y(0.50f), x(0.62f), y(0.50f), strokePaint)
                canvas.drawLine(x(0.18f), y(0.70f), x(0.42f), y(0.70f), strokePaint)
            }
            NasjeIcon.PLUS -> {
                canvas.drawLine(x(0.50f), y(0.20f), x(0.50f), y(0.80f), strokePaint)
                canvas.drawLine(x(0.20f), y(0.50f), x(0.80f), y(0.50f), strokePaint)
            }
            NasjeIcon.BACK -> {
                canvas.drawLine(x(0.78f), y(0.50f), x(0.22f), y(0.50f), strokePaint)
                val p = Path().apply {
                    moveTo(x(0.44f), y(0.26f))
                    lineTo(x(0.22f), y(0.50f))
                    lineTo(x(0.44f), y(0.74f))
                }
                canvas.drawPath(p, strokePaint)
            }
            NasjeIcon.CLOSE -> {
                canvas.drawLine(x(0.24f), y(0.24f), x(0.76f), y(0.76f), strokePaint)
                canvas.drawLine(x(0.76f), y(0.24f), x(0.24f), y(0.76f), strokePaint)
            }
            NasjeIcon.CHECK -> {
                val p = Path().apply {
                    moveTo(x(0.20f), y(0.52f))
                    lineTo(x(0.42f), y(0.74f))
                    lineTo(x(0.82f), y(0.28f))
                }
                canvas.drawPath(p, strokePaint)
            }
            NasjeIcon.CHECK_CIRCLE -> {
                canvas.drawCircle(x(0.50f), y(0.50f), s * 0.34f, strokePaint)
                val p = Path().apply {
                    moveTo(x(0.32f), y(0.51f))
                    lineTo(x(0.45f), y(0.64f))
                    lineTo(x(0.68f), y(0.37f))
                }
                canvas.drawPath(p, strokePaint)
            }
            NasjeIcon.ERROR -> {
                canvas.drawCircle(x(0.50f), y(0.50f), s * 0.34f, strokePaint)
                canvas.drawLine(x(0.50f), y(0.32f), x(0.50f), y(0.54f), strokePaint)
                canvas.drawCircle(x(0.50f), y(0.68f), s * 0.045f, fillPaint)
            }
            NasjeIcon.WARNING -> {
                val p = Path().apply {
                    moveTo(x(0.50f), y(0.18f))
                    lineTo(x(0.84f), y(0.78f))
                    lineTo(x(0.16f), y(0.78f))
                    close()
                }
                canvas.drawPath(p, strokePaint)
                canvas.drawLine(x(0.50f), y(0.38f), x(0.50f), y(0.56f), strokePaint)
                canvas.drawCircle(x(0.50f), y(0.68f), s * 0.04f, fillPaint)
            }
            NasjeIcon.SETTINGS -> {
                canvas.drawCircle(x(0.50f), y(0.50f), s * 0.16f, strokePaint)
                canvas.drawCircle(x(0.50f), y(0.50f), s * 0.30f, strokePaint)
                for (deg in 0 until 360 step 45) {
                    val rad = Math.toRadians(deg.toDouble())
                    val x1 = x(0.50f) + (s * 0.28f * Math.cos(rad)).toFloat()
                    val y1 = y(0.50f) + (s * 0.28f * Math.sin(rad)).toFloat()
                    val x2 = x(0.50f) + (s * 0.38f * Math.cos(rad)).toFloat()
                    val y2 = y(0.50f) + (s * 0.38f * Math.sin(rad)).toFloat()
                    canvas.drawLine(x1, y1, x2, y2, strokePaint)
                }
            }
            NasjeIcon.UNDO -> {
                val arc = RectF(x(0.24f), y(0.36f), x(0.78f), y(0.78f))
                canvas.drawArc(arc, -90f, 180f, false, strokePaint)
                canvas.drawLine(x(0.26f), y(0.36f), x(0.52f), y(0.36f), strokePaint)
                val p = Path().apply {
                    moveTo(x(0.36f), y(0.22f))
                    lineTo(x(0.22f), y(0.36f))
                    lineTo(x(0.36f), y(0.50f))
                }
                canvas.drawPath(p, strokePaint)
            }
            NasjeIcon.REDO -> {
                val arc = RectF(x(0.22f), y(0.36f), x(0.76f), y(0.78f))
                canvas.drawArc(arc, -90f, -180f, false, strokePaint)
                canvas.drawLine(x(0.48f), y(0.36f), x(0.74f), y(0.36f), strokePaint)
                val p = Path().apply {
                    moveTo(x(0.64f), y(0.22f))
                    lineTo(x(0.78f), y(0.36f))
                    lineTo(x(0.64f), y(0.50f))
                }
                canvas.drawPath(p, strokePaint)
            }
            NasjeIcon.BUILD -> {
                canvas.drawLine(x(0.24f), y(0.76f), x(0.68f), y(0.32f), strokePaint)
                val head = Path().apply {
                    moveTo(x(0.52f), y(0.18f))
                    lineTo(x(0.82f), y(0.34f))
                    lineTo(x(0.70f), y(0.48f))
                    lineTo(x(0.44f), y(0.28f))
                    close()
                }
                canvas.drawPath(head, strokePaint)
            }
            NasjeIcon.DOWNLOAD -> {
                canvas.drawLine(x(0.50f), y(0.18f), x(0.50f), y(0.64f), strokePaint)
                val arrow = Path().apply {
                    moveTo(x(0.30f), y(0.46f))
                    lineTo(x(0.50f), y(0.66f))
                    lineTo(x(0.70f), y(0.46f))
                }
                canvas.drawPath(arrow, strokePaint)
                canvas.drawLine(x(0.20f), y(0.80f), x(0.80f), y(0.80f), strokePaint)
            }
            NasjeIcon.INSTALL -> {
                canvas.drawRect(x(0.22f), y(0.20f), x(0.78f), y(0.80f), strokePaint)
                canvas.drawLine(x(0.50f), y(0.32f), x(0.50f), y(0.64f), strokePaint)
                val arrow = Path().apply {
                    moveTo(x(0.36f), y(0.50f))
                    lineTo(x(0.50f), y(0.65f))
                    lineTo(x(0.64f), y(0.50f))
                }
                canvas.drawPath(arrow, strokePaint)
            }
            NasjeIcon.SHARE -> {
                val r = s * 0.08f
                canvas.drawCircle(x(0.72f), y(0.24f), r, fillPaint)
                canvas.drawCircle(x(0.26f), y(0.50f), r, fillPaint)
                canvas.drawCircle(x(0.72f), y(0.76f), r, fillPaint)
                canvas.drawLine(x(0.32f), y(0.47f), x(0.66f), y(0.27f), strokePaint)
                canvas.drawLine(x(0.32f), y(0.53f), x(0.66f), y(0.73f), strokePaint)
            }
            NasjeIcon.COPY -> {
                canvas.drawRect(x(0.34f), y(0.32f), x(0.80f), y(0.82f), strokePaint)
                val back = Path().apply {
                    moveTo(x(0.66f), y(0.32f))
                    lineTo(x(0.66f), y(0.18f))
                    lineTo(x(0.20f), y(0.18f))
                    lineTo(x(0.20f), y(0.68f))
                    lineTo(x(0.34f), y(0.68f))
                }
                canvas.drawPath(back, strokePaint)
            }
            NasjeIcon.MOVE -> {
                canvas.drawLine(x(0.22f), y(0.50f), x(0.78f), y(0.50f), strokePaint)
                val arrow = Path().apply {
                    moveTo(x(0.58f), y(0.28f))
                    lineTo(x(0.78f), y(0.50f))
                    lineTo(x(0.58f), y(0.72f))
                }
                canvas.drawPath(arrow, strokePaint)
            }
            NasjeIcon.DELETE -> {
                canvas.drawLine(x(0.20f), y(0.28f), x(0.80f), y(0.28f), strokePaint)
                canvas.drawLine(x(0.38f), y(0.20f), x(0.62f), y(0.20f), strokePaint)
                canvas.drawRect(x(0.28f), y(0.28f), x(0.72f), y(0.82f), strokePaint)
                canvas.drawLine(x(0.42f), y(0.42f), x(0.42f), y(0.70f), strokePaint)
                canvas.drawLine(x(0.58f), y(0.42f), x(0.58f), y(0.70f), strokePaint)
            }
            NasjeIcon.EDIT -> {
                val p = Path().apply {
                    moveTo(x(0.20f), y(0.80f))
                    lineTo(x(0.26f), y(0.58f))
                    lineTo(x(0.68f), y(0.18f))
                    lineTo(x(0.82f), y(0.32f))
                    lineTo(x(0.40f), y(0.74f))
                    close()
                }
                canvas.drawPath(p, strokePaint)
            }
            NasjeIcon.LAYERS -> {
                val top = Path().apply {
                    moveTo(x(0.50f), y(0.18f))
                    lineTo(x(0.84f), y(0.36f))
                    lineTo(x(0.50f), y(0.54f))
                    lineTo(x(0.16f), y(0.36f))
                    close()
                }
                canvas.drawPath(top, strokePaint)
                val mid = Path().apply {
                    moveTo(x(0.16f), y(0.52f))
                    lineTo(x(0.50f), y(0.70f))
                    lineTo(x(0.84f), y(0.52f))
                }
                canvas.drawPath(mid, strokePaint)
                val bot = Path().apply {
                    moveTo(x(0.16f), y(0.66f))
                    lineTo(x(0.50f), y(0.84f))
                    lineTo(x(0.84f), y(0.66f))
                }
                canvas.drawPath(bot, strokePaint)
            }
            NasjeIcon.CHEVRON_DOWN -> {
                val p = Path().apply {
                    moveTo(x(0.24f), y(0.36f))
                    lineTo(x(0.50f), y(0.64f))
                    lineTo(x(0.76f), y(0.36f))
                }
                canvas.drawPath(p, strokePaint)
            }
            NasjeIcon.CHEVRON_RIGHT -> {
                val p = Path().apply {
                    moveTo(x(0.36f), y(0.24f))
                    lineTo(x(0.64f), y(0.50f))
                    lineTo(x(0.36f), y(0.76f))
                }
                canvas.drawPath(p, strokePaint)
            }
            NasjeIcon.FILE -> {
                val p = Path().apply {
                    moveTo(x(0.24f), y(0.16f))
                    lineTo(x(0.58f), y(0.16f))
                    lineTo(x(0.76f), y(0.34f))
                    lineTo(x(0.76f), y(0.84f))
                    lineTo(x(0.24f), y(0.84f))
                    close()
                }
                canvas.drawPath(p, strokePaint)
                canvas.drawLine(x(0.58f), y(0.16f), x(0.58f), y(0.34f), strokePaint)
                canvas.drawLine(x(0.58f), y(0.34f), x(0.76f), y(0.34f), strokePaint)
            }
            NasjeIcon.PASTE -> {
                canvas.drawRect(x(0.22f), y(0.24f), x(0.78f), y(0.84f), strokePaint)
                canvas.drawRect(x(0.38f), y(0.14f), x(0.62f), y(0.30f), strokePaint)
            }
            NasjeIcon.REFRESH -> {
                val arc = RectF(x(0.22f), y(0.22f), x(0.78f), y(0.78f))
                canvas.drawArc(arc, -45f, 290f, false, strokePaint)
                val arrow = Path().apply {
                    moveTo(x(0.66f), y(0.18f))
                    lineTo(x(0.78f), y(0.32f))
                    lineTo(x(0.60f), y(0.34f))
                }
                canvas.drawPath(arrow, strokePaint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        strokePaint.alpha = alpha
        fillPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        strokePaint.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

object UiKit {

    fun Context.dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    fun Context.dpF(value: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )

    fun roundedBox(
        context: Context,
        fillColor: Int,
        strokeColor: Int = Color.TRANSPARENT,
        strokeDp: Int = 1,
        radiusDp: Int = 12
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = context.dpF(radiusDp.toFloat())
        setColor(fillColor)
        if (strokeColor != Color.TRANSPARENT && strokeDp > 0) {
            setStroke(context.dp(strokeDp), strokeColor)
        }
    }

    fun gradientBox(
        context: Context,
        startColor: Int,
        endColor: Int,
        strokeColor: Int = Color.TRANSPARENT,
        strokeDp: Int = 0,
        radiusDp: Int = 14
    ): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(startColor, endColor)
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = context.dpF(radiusDp.toFloat())
        if (strokeColor != Color.TRANSPARENT && strokeDp > 0) {
            setStroke(context.dp(strokeDp), strokeColor)
        }
    }

    fun ripple(content: Drawable, rippleColor: Int): Drawable =
        RippleDrawable(ColorStateList.valueOf(rippleColor), content, null)

    fun makeText(
        context: Context,
        text: CharSequence = "",
        sizeSp: Float = 14f,
        color: Int,
        bold: Boolean = false,
        displayFont: Boolean = false,
        mono: Boolean = false,
        maxLines: Int = Int.MAX_VALUE
    ): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        typeface = when {
            mono -> FontKit.mono(context)
            displayFont -> FontKit.display(context)
            else -> FontKit.body(context)
        }
        if (bold && !displayFont) {
            setTypeface(typeface, Typeface.BOLD)
        }
        if (maxLines != Int.MAX_VALUE) {
            this.maxLines = maxLines
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    fun makeCard(
        context: Context,
        palette: NasjePalette,
        elevated: Boolean = false,
        borderColor: Int = palette.surfaceBorder,
        radiusDp: Int = 14,
        padH: Int = 14,
        padV: Int = 14
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBox(
            context = context,
            fillColor = if (elevated) palette.surfaceElevated else palette.surface,
            strokeColor = borderColor,
            strokeDp = 1,
            radiusDp = radiusDp
        )
        setPadding(context.dp(padH), context.dp(padV), context.dp(padH), context.dp(padV))
    }

    fun makeActionButton(
        context: Context,
        label: String,
        bgColor: Int,
        textColor: Int = 0xFFFFFFFF.toInt(),
        strokeColor: Int = Color.TRANSPARENT,
        gradientEndColor: Int? = null,
        icon: NasjeIcon? = null,
        radiusDp: Int = 12,
        heightDp: Int = 48,
        padHDp: Int = 14,
        textSizeSp: Float = 13f,
        tag: String? = null,
        onClick: () -> Unit
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        this.tag = tag
        contentDescription = label
        minimumHeight = context.dp(heightDp)
        setPadding(context.dp(padHDp), context.dp(8), context.dp(padHDp), context.dp(8))
        val bg = if (gradientEndColor != null) {
            gradientBox(context, bgColor, gradientEndColor, strokeColor, if (strokeColor != Color.TRANSPARENT) 1 else 0, radiusDp)
        } else {
            roundedBox(context, bgColor, strokeColor, if (strokeColor != Color.TRANSPARENT) 1 else 0, radiusDp)
        }
        background = ripple(bg, 0x33FFFFFF)

        if (icon != null) {
            addView(
                ImageView(context).apply {
                    setImageDrawable(VectorIconDrawable(icon, textColor, context.dp(17)))
                },
                LinearLayout.LayoutParams(context.dp(17), context.dp(17)).apply {
                    marginEnd = context.dp(6)
                }
            )
        }
        addView(
            makeText(context, label, sizeSp = textSizeSp, color = textColor, bold = true, maxLines = 1)
        )
        setOnClickListener { onClick() }
    }

    fun makeIconButton(
        context: Context,
        icon: NasjeIcon,
        tint: Int,
        contentDesc: String,
        bgColor: Int = Color.TRANSPARENT,
        borderColor: Int = Color.TRANSPARENT,
        sizeDp: Int = 48,
        iconDp: Int = 20,
        radiusDp: Int = 10,
        tag: String? = null,
        onClick: () -> Unit
    ): ImageButton = ImageButton(context).apply {
        this.tag = tag
        contentDescription = contentDesc
        scaleType = ImageView.ScaleType.CENTER
        setImageDrawable(VectorIconDrawable(icon, tint, context.dp(iconDp)))
        val base = roundedBox(context, bgColor, borderColor, if (borderColor != Color.TRANSPARENT) 1 else 0, radiusDp)
        background = ripple(base, 0x3338BDF8)
        minimumWidth = context.dp(48)
        minimumHeight = context.dp(48)
        layoutParams = LinearLayout.LayoutParams(context.dp(sizeDp), context.dp(sizeDp))
        setOnClickListener { onClick() }
    }

    fun makeChip(
        context: Context,
        palette: NasjePalette,
        label: String,
        selected: Boolean,
        accentColor: Int = palette.blue,
        tag: String? = null,
        onClick: () -> Unit
    ): TextView = makeText(
        context = context,
        text = label,
        sizeSp = 12.5f,
        color = if (selected) accentColor else palette.textSecondary,
        bold = selected,
        maxLines = 1
    ).apply {
        this.tag = tag
        isClickable = true
        isFocusable = true
        gravity = Gravity.CENTER
        minimumHeight = context.dp(38)
        setPadding(context.dp(12), context.dp(7), context.dp(12), context.dp(7))
        val fill = if (selected) withAlpha(accentColor, 0.18f) else palette.surface
        val stroke = if (selected) accentColor else palette.surfaceBorder
        background = ripple(roundedBox(context, fill, stroke, 1, 10), palette.ripple)
        setOnClickListener { onClick() }
    }

    fun makeInput(
        context: Context,
        palette: NasjePalette,
        hint: String,
        initialValue: String = "",
        mono: Boolean = false,
        singleLine: Boolean = true,
        tag: String? = null
    ): EditText = EditText(context).apply {
        this.tag = tag
        this.hint = hint
        setText(initialValue)
        setTextColor(palette.textPrimary)
        setHintTextColor(palette.textMuted)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = if (mono) FontKit.mono(context) else FontKit.body(context)
        this.isSingleLine = singleLine
        minimumHeight = context.dp(46)
        setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        background = roundedBox(context, palette.editorBg, palette.surfaceBorder, 1, 10)
    }

    fun makeLanguageBadge(
        context: Context,
        language: IdeLanguage,
        compact: Boolean = false
    ): TextView {
        val code = when (language) {
            IdeLanguage.JAVA -> "J"
            IdeLanguage.KOTLIN -> "Kt"
            IdeLanguage.CPP -> "C++"
            IdeLanguage.C -> "C"
            IdeLanguage.XML -> "XML"
            IdeLanguage.GRADLE -> "Gr"
            IdeLanguage.KOTLIN_DSL -> "Kts"
            IdeLanguage.HTML -> "H5"
            IdeLanguage.CSS -> "CSS"
            IdeLanguage.JAVASCRIPT -> "JS"
            IdeLanguage.JSON -> "{}"
            IdeLanguage.PROPERTIES -> "Cfg"
            IdeLanguage.MARKDOWN -> "MD"
            IdeLanguage.TEXT -> "Txt"
        }
        val boxDp = if (compact) 36 else 44
        return TextView(context).apply {
            text = code
            gravity = Gravity.CENTER
            setTextColor(language.accentColorInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 11f else 13f)
            typeface = FontKit.mono(context)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBox(
                context = context,
                fillColor = withAlpha(language.accentColorInt, 0.16f),
                strokeColor = withAlpha(language.accentColorInt, 0.55f),
                strokeDp = 1,
                radiusDp = 12
            )
            layoutParams = LinearLayout.LayoutParams(context.dp(boxDp), context.dp(boxDp))
        }
    }

    fun makeTopBar(
        context: Context,
        palette: NasjePalette,
        title: String,
        subtitle: String? = null,
        onBack: () -> Unit,
        rightAction: View? = null
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(palette.topBarBg)
        setPadding(context.dp(6), context.dp(6), context.dp(10), context.dp(6))

        addView(makeIconButton(context, NasjeIcon.BACK, palette.textPrimary, "Retour", onClick = onBack))
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(4), 0, context.dp(6), 0)
                addView(makeText(context, title, 16f, palette.textPrimary, bold = true, displayFont = true, maxLines = 1))
                if (!subtitle.isNullOrBlank()) {
                    addView(makeText(context, subtitle, 12f, palette.textSecondary, maxLines = 1))
                }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        if (rightAction != null) {
            addView(rightAction)
        }
    }

    fun makeDivider(context: Context, palette: NasjePalette): View = View(context).apply {
        setBackgroundColor(palette.divider)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(1))
    }

    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    fun dpParams(
        width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    ): LinearLayout.LayoutParams = LinearLayout.LayoutParams(width, height)
}
