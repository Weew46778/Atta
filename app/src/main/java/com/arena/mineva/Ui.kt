package com.arena.mineva

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object Ui {
    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun text(
        context: Context,
        content: String,
        sizeSp: Float = 14f,
        color: Int = Color.WHITE,
        bold: Boolean = false,
        gravity: Int = Gravity.START
    ): TextView = TextView(context).apply {
        text = content
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        this.gravity = gravity
        setPadding(dp(context, 6f), dp(context, 4f), dp(context, 6f), dp(context, 4f))
    }

    fun button(
        context: Context,
        content: String,
        bgColor: Int,
        textColor: Int = Color.WHITE,
        heightDp: Float = 52f,
        onClick: (() -> Unit)? = null
    ): TextView = TextView(context).apply {
        text = content
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(textColor)
        gravity = Gravity.CENTER
        setPadding(dp(context, 16f), 0, dp(context, 16f), 0)
        background = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dp(context, 12f).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(context, heightDp)
        ).apply {
            topMargin = dp(context, 8f)
            bottomMargin = dp(context, 8f)
        }
        isClickable = true
        isFocusable = true
        val click = onClick
        if (click != null) setOnClickListener { click() }
    }

    // Convenience overload used by the app's compact buttons:
    // button(context, content, bgColor, heightDp) / button(context, content, bgColor, heightDp, onClick)
    fun button(
        context: Context,
        content: String,
        bgColor: Int,
        heightDp: Float,
        onClick: (() -> Unit)? = null
    ): TextView = button(context, content, bgColor, Color.WHITE, heightDp, onClick)

    fun card(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = dp(context, 14f)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            setColor(0xFF1C2836.toInt())
            cornerRadius = dp(context, 16f).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(context, 6f)
            bottomMargin = dp(context, 6f)
        }
    }

    fun vertical(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = dp(context, 16f)
        setPadding(pad, pad, pad, pad)
    }

    fun horizontal(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    fun fill(context: Context): LinearLayout = vertical(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    /**
     * Wraps an arbitrary content view in a full-page ScrollView. The inner view is laid out
     * with WRAP_CONTENT height so long screens really scroll to the bottom. This is the
     * default way every multi-section screen should be created.
     */
    fun scrollable(context: Context, content: View): ScrollView {
        val scroll = ScrollView(context)
        val lp = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        content.layoutParams = lp
        scroll.addView(content, lp)
        return scroll
    }

    fun scrollFill(context: Context): Pair<ScrollView, LinearLayout> {
        val root = fill(context)
        return scrollable(context, root) to root
    }
}
