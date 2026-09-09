package com.atta.mcpanel.overlay

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.atta.mcpanel.R
import kotlin.math.roundToInt

/** ابزار کوچک ساخت المان‌های رابط کاربری پنل. */
object UiKit {

    fun dp(ctx: Context, v: Int): Int =
        (ctx.resources.displayMetrics.density * v).roundToInt()

    fun vibrate(ctx: Context, ms: Long = 25) {
        try {
            val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (vib.hasVibrator()) {
                vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------- //

    enum class ChipKind { PLAIN, ACCENT, DANGER }

    /**
     * یک دکمهٔ چیپ مانند با پس‌زمینهٔ گرد.
     * فقط یک پارامتر تابعی (onClick) دارد تا لامبدا بدون ابهام به کلیک وصل شود.
     */
    fun chip(
        ctx: Context,
        label: String,
        kind: ChipKind = ChipKind.PLAIN,
        hint: String? = null,
        onClick: (() -> Unit)? = null,
    ): TextView {
        val tv = TextView(ctx)
        val density = ctx.resources.displayMetrics.density
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.textSize = 12.5f
        tv.minimumHeight = 0
        tv.includeFontPadding = true
        val pad = (9 * density).roundToInt()
        tv.setPadding(pad, (6 * density).roundToInt(), pad, (6 * density).roundToInt())

        val (normalRes, pressedRes, fgRes) = when (kind) {
            ChipKind.PLAIN -> Triple(R.color.chipBg, R.color.chipBgDown, R.color.chipText)
            ChipKind.ACCENT -> Triple(R.color.accentDeep, R.color.accent, R.color.chipText)
            ChipKind.DANGER -> Triple(R.color.dangerBg, R.color.danger, R.color.dangerText)
        }
        tv.background = roundedBg(
            ContextCompat.getColor(ctx, normalRes),
            ContextCompat.getColor(ctx, pressedRes),
            14 * density,
        )
        tv.setTextColor(ContextCompat.getColor(ctx, fgRes))
        if (onClick != null) {
            tv.isClickable = true
            tv.setOnClickListener { onClick() }
        }
        if (hint != null) tv.contentDescription = hint
        return tv
    }

    /** چیپی با هر دو کلیک کوتاه و بلند (لمس کوتاه/لمس بلند). */
    fun chipToggle(
        ctx: Context,
        label: String,
        kind: ChipKind = ChipKind.PLAIN,
        hint: String? = null,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
    ): TextView {
        val tv = chip(ctx, label, kind, hint)
        if (onClick != null) tv.setOnClickListener { onClick() }
        if (onLongClick != null) {
            tv.isLongClickable = true
            tv.setOnLongClickListener {
                onLongClick()
                true
            }
        }
        tv.isClickable = onClick != null || onLongClick != null
        return tv
    }

    /** نقطهٔ گرد رنگی برای نمایش وضعیت. */
    fun dot(ctx: Context, color: Int, sizeDp: Int = 10): View {
        val v = View(ctx)
        v.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        v.layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp))
        return v
    }

    /** پس‌زمینهٔ گرد دو حالته (عادی/فشرده‌شده). */
    fun roundedBg(normalColor: Int, pressedColor: Int, cornerRadiusPx: Float): StateListDrawable {
        fun shape(color: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = cornerRadiusPx
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(pressedColor))
            addState(intArrayOf(), shape(normalColor))
        }
    }

    /** عنوان بخش‌ها داخل صفحهٔ هر زبانه. */
    fun sectionLabel(ctx: Context, text: String, accent: Boolean = true): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 12.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, if (accent) R.color.accent else R.color.gold))
            setPadding(4, dp(ctx, 12), 4, dp(ctx, 4))
        }

    /** توضیح کوتاه زیر بخش‌ها. */
    fun caption(ctx: Context, text: String, dim: Boolean = false): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 11f
            setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (dim) R.color.textDim else R.color.textSub
                )
            )
            setPadding(4, dp(ctx, 0), 4, dp(ctx, 2))
        }

    fun divider(ctx: Context): View =
        View(ctx).apply {
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.colorStroke))
        }

    fun space(ctx: Context, hDp: Int): View =
        View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, hDp)
            )
        }

    /** یک ردیف چینش چیپ‌ها با عرض یکسان. */
    fun chipRow(ctx: Context, chips: List<TextView>, perRow: Int, into: LinearLayout) {
        chips.chunked(perRow).forEach { group ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                setPadding(2, 0, 2, 0)
            }
            group.forEach { chip ->
                row.addView(
                    chip,
                    LinearLayout.LayoutParams(0, dp(ctx, 46), 1f).apply {
                        val m = dp(ctx, 3)
                        setMargins(m, 0, m, dp(ctx, 6))
                    }
                )
            }
            into.addView(row)
        }
    }

    /** متن‌های کوچک در یک ستون؛ برای ردیف‌های فرم. */
    fun formLabel(ctx: Context, text: String): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 11.5f
            setTextColor(ContextCompat.getColor(ctx, R.color.textSub))
        }
}
