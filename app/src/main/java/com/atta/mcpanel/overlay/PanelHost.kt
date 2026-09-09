package com.atta.mcpanel.overlay

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.core.content.ContextCompat
import com.atta.mcpanel.R
import com.atta.mcpanel.core.AppPrefs
import com.atta.mcpanel.core.PanelWidthMode
import com.atta.mcpanel.overlay.tabs.ControlsTab
import com.atta.mcpanel.overlay.tabs.ServerTab
import com.atta.mcpanel.overlay.tabs.SettingsTab
import com.atta.mcpanel.overlay.tabs.WorldTab
import com.atta.mcpanel.rcon.RconConnection
import com.atta.mcpanel.rcon.RconEvent
import kotlin.math.roundToInt
import kotlin.math.min

/**
 * میزبان پنل شناور: پنجرهٔ overlay، دستگیرهٔ کشویی لبهٔ چپ،
 * زبانه‌های انیمیشنی، پیام‌های کوتاه (toast داخلی) و اتصال RCON.
 * همه‌چیز در نخ اصلی UI اجرا می‌شود.
 */
class PanelHost(private val service: OverlayService) {

    private val ctx: Context = service
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    val prefs = AppPrefs(ctx)

    /** دسترسی به Context سرویس برای زبانه‌ها. */
    val context: Context
        get() = ctx

    // پنجره و ویوها -------------------------------------------------------
    private var rootView: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var panelView: View? = null
    private var handleView: View? = null
    private var flipper: ViewFlipper? = null
    private var tvTitle: TextView? = null
    private var flashView: TextView? = null
    private var open = false
    private var animating = false
    private var dragging = false
    private var flashJob: Runnable? = null

    // RCON ----------------------------------------------------------------
    private var rcon: RconConnection? = null
    private val stateListeners = mutableListOf<() -> Unit>()
    private val consoleListeners = mutableListOf<(String) -> Unit>()

    // =====================================================================
    // پنجره
    // =====================================================================

    fun attach() {
        if (rootView != null) return
        val root = FrameLayout(ctx).apply {
            // پنجره همیشه LTR است: دستگیره باید در لبهٔ چپ بماند،
            // حتی روی دستگاه‌های فارسی/عربی (RTL)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        rootView = root

        val handle = LayoutInflater.from(ctx).inflate(R.layout.overlay_handle, root, false)
        handleView = handle
        handle.setOnTouchListener(handleTouchListener)
        root.addView(
            handle,
            FrameLayout.LayoutParams(
                UiKit.dp(ctx, 56),
                UiKit.dp(ctx, 130),
                Gravity.START or Gravity.CENTER_VERTICAL,
            )
        )

        val panel = LayoutInflater.from(ctx).inflate(R.layout.panel_overlay, root, false)
        panelView = panel
        // پنل لمس را می‌خورد تا به بازی پشت آن نرسد
        panel.isClickable = true

        tvTitle = panel.findViewById(R.id.tvPanelTitle)
        flipper = panel.findViewById(R.id.flipper)
        panel.findViewById<TextView>(R.id.btnClosePanel).setOnClickListener { closePanel() }
        panel.findViewById<LinearLayout>(R.id.tabStrip).let { strip ->
            buildTabs(strip)
        }

        val panelW = panelWidthPx()
        panel.translationX = -panelW.toFloat()
        root.addView(panel, FrameLayout.LayoutParams(panelW, FrameLayout.LayoutParams.MATCH_PARENT))

        // تعیین اندازهٔ دستگیره پس از اولین چیدمان
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!dragging && !animating) positionHandle()
        }

        // اگر هنگام تایپ، کاربر جای دیگری (مثلاً خودِ بازی) را لمس کرد،
        // پنجره دوباره «عبوردهنده» شود تا بازی بی‌وقفه ادامه یابد
        root.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
            if (!hasFocus) releaseConsoleFocus()
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseWindowFlags(),
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 0
        lp.y = 0
        params = lp
        runCatching { wm.addView(root, lp) }.onFailure {
            params = null
            rootView = null
            service.stopSelf()
            return
        }
        panel.alpha = prefs.overlayAlpha
        root.post { positionHandle() }
        flash("برای باز کردن پنل از لبهٔ چپ بکش", "نوار سبز را لمس کن")
    }

    fun detach() {
        releaseConsoleFocus()
        runCatching { rootView?.let { wm.removeViewImmediate(it) } }
        rconDisconnect()
        main.removeCallbacksAndMessages(null)
        stateListeners.clear()
        consoleListeners.clear()
        rootView = null
        panelView = null
        handleView = null
        flipper = null
        open = false
    }

    private fun baseWindowFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun panelWidthPx(): Int {
        val w = ctx.resources.displayMetrics.widthPixels
        val d = ctx.resources.displayMetrics.density
        return when (prefs.panelWidthMode) {
            PanelWidthMode.NARROW -> min(w, (310 * d).roundToInt())
            PanelWidthMode.NORMAL -> min(w, (396 * d).roundToInt())
            PanelWidthMode.WIDE -> w
        }
    }

    /** تغییر عرض پنل از تنظیمات. */
    fun setPanelWidthMode(mode: PanelWidthMode) {
        prefs.panelWidthMode = mode
        applyPanelMetrics(panelView ?: return)
    }

    private fun applyPanelMetrics(panel: View) {
        val lp = panel.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.width = panelWidthPx()
        panel.layoutParams = lp
        panel.translationX = if (open) 0f else -panelWidthPx().toFloat()
        panel.alpha = prefs.overlayAlpha
    }

    fun setPanelAlpha(alpha: Float) {
        prefs.overlayAlpha = alpha
        panelView?.alpha = alpha
    }

    // =====================================================================
    // دستگیرهٔ کناری و باز/بسته‌کردن
    // =====================================================================

    private val handleTouchListener = View.OnTouchListener { v, ev ->
        val slop = ViewConfiguration.get(ctx).scaledTouchSlop.toFloat()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragDownX = ev.rawX
                dragDownY = ev.rawY
                dragBaseY = v.translationY
                startedDrag = false
                dragging = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - dragDownX
                val dy = ev.rawY - dragDownY
                if (!startedDrag) {
                    when {
                        !open && Math.abs(dx) > slop * 1.3f && Math.abs(dx) > Math.abs(dy) && dx > 0 -> {
                            startedDrag = true
                            openPanel()
                        }
                        Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx) -> {
                            startedDrag = true
                            dragging = true
                        }
                    }
                } else if (dragging) {
                    val h = v.height
                    val H = rootView?.height ?: return@OnTouchListener true
                    val maxT = Math.max(0f, (H - h) / 2f)
                    v.translationY = (dragBaseY + dy).coerceIn(-maxT, maxT)
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging && ev.actionMasked == MotionEvent.ACTION_UP) {
                    val h = v.height
                    val H = rootView?.height ?: 0
                    val range = (H - h).toFloat()
                    if (range > 0) {
                        val top = range / 2f + v.translationY
                        prefs.handleFraction = (top / range).coerceIn(0f, 1f)
                    }
                    dragging = false
                } else if (!startedDrag && !open) {
                    openPanel()
                }
                startedDrag = false
                true
            }
            else -> false
        }
    }

    private var dragDownX = 0f
    private var dragDownY = 0f
    private var dragBaseY = 0f
    private var startedDrag = false

    /** جای دستگیره بر اساس درصد ذخیره‌شده. */
    private fun positionHandle() {
        val h = handleView ?: return
        val H = rootView?.height ?: return
        if (H <= 0 || h.height <= 0) return
        val range = (H - h.height).toFloat()
        h.translationY = range * (prefs.handleFraction - 0.5f)
    }

    fun isOpen(): Boolean = open

    fun openPanel() {
        if (open || animating) return
        val panel = panelView ?: return
        open = true
        animating = true
        handleView?.visibility = View.GONE
        panel.visibility = View.VISIBLE
        panel.translationX = -panel.width.toFloat()
        if (prefs.vibrationOn) UiKit.vibrate(ctx, 18)
        val anim = ValueAnimator.ofFloat(-panel.width.toFloat(), 0f).apply {
            duration = 320
            interpolator = OvershootInterpolator(1.04f)
            addUpdateListener { a -> panel.translationX = a.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    animating = false
                }
            })
        }
        anim.start()
    }

    fun closePanel() {
        if (!open || animating) return
        val panel = panelView ?: return
        animating = true
        releaseConsoleFocus()
        val anim = ValueAnimator.ofFloat(panel.translationX, -panel.width.toFloat()).apply {
            duration = 260
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { a -> panel.translationX = a.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    open = false
                    animating = false
                    handleView?.visibility = View.VISIBLE
                    positionHandle()
                }
            })
        }
        anim.start()
    }

    // =====================================================================
    // زبانه‌ها
    // =====================================================================

    private val tabTitles = listOf("ابزارها", "سرور", "دنیا", "تنظیمات")
    private val tabButtons = mutableListOf<Pair<TextView, View>>() // (برچسب، خط زیر آن)
    private var currentTab = 0

    private fun buildTabs(strip: LinearLayout) {
        flipper?.let { f ->
            val pages = listOf(
                ControlsTab.build(this),
                ServerTab.build(this),
                WorldTab.build(this),
                SettingsTab.build(this),
            )
            pages.forEach {
                f.addView(
                    it,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
            f.setDisplayedChild(0)
        }
        tabTitles.forEachIndexed { index, title ->
            val item = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setPadding(UiKit.dp(ctx, 6), UiKit.dp(ctx, 3), UiKit.dp(ctx, 6), 0)
                setOnClickListener {
                    switchTab(index)
                }
            }
            val label = TextView(ctx).apply {
                text = title
                textSize = 12.5f
                setTypeface(typeface, Typeface.BOLD)
            }
            val underline = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(UiKit.dp(ctx, 26), UiKit.dp(ctx, 3))
                setBackgroundColor(ContextCompat.getColor(ctx, R.color.accent))
            }
            item.addView(label)
            item.addView(underline)
            tabButtons.add(label to underline)
            strip.addView(
                item,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
        }
        updateTabVisuals()
    }

    fun switchTab(index: Int) {
        val f = flipper ?: return
        if (index == currentTab || index < 0 || index >= f.childCount) return
        val forward = index > currentTab
        f.setInAnimation(ctx, if (forward) R.anim.tab_in else R.anim.tab_in_back)
        f.setOutAnimation(ctx, if (forward) R.anim.tab_out else R.anim.tab_out_back)
        f.setDisplayedChild(index)
        currentTab = index
        updateTabVisuals()
    }

    private fun updateTabVisuals() {
        tabButtons.forEachIndexed { index, (label, underline) ->
            val selected = index == currentTab
            label.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (selected) R.color.textMain else R.color.textDim
                )
            )
            underline.visibility = if (selected) View.VISIBLE else View.INVISIBLE
        }
        tvTitle?.text = tabTitles[currentTab]
    }

    // =====================================================================
    // پیام‌های کوتاه روی صفحه (بدون گرفتن لمس)
    // =====================================================================

    fun flash(title: String, detail: String? = null) {
        val root = rootView ?: return
        val tv = flashView ?: TextView(ctx).also { v ->
            v.setBackgroundResource(R.drawable.bg_flash)
            v.setTextColor(Color.rgb(238, 242, 246))
            v.textSize = 12.5f
            v.setLineSpacing(2f, 1f)
            v.includeFontPadding = true
            val p = UiKit.dp(ctx, 12)
            v.setPadding(p, UiKit.dp(ctx, 9), p, UiKit.dp(ctx, 9))
            v.maxWidth = UiKit.dp(ctx, 340)
            v.isClickable = false
            flashView = v
            root.addView(
                v,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL or Gravity.END,
                ).apply {
                    marginEnd = UiKit.dp(ctx, 26)
                }
            )
        }
        tv.text = if (detail.isNullOrBlank()) title else "$title\n$detail"
        tv.visibility = View.VISIBLE
        tv.animate().cancel()
        tv.alpha = 0f
        tv.animate().alpha(1f).setDuration(140).start()
        flashJob?.let { main.removeCallbacks(it) }
        flashJob = Runnable {
            tv.animate().alpha(0f).setDuration(400)
                .withEndAction { tv.visibility = View.GONE }
                .start()
        }
        main.postDelayed(flashJob, 2600)
    }

    // =====================================================================
    // ورودی متن داخل پنل (باز کردن کیبورد هنگام لمس)
    // =====================================================================

    private var activeInput: EditText? = null
    private var keyboardOpen = false

    /** ساخت EditText با استایل پنل + مدیریت فوکوس/کیبورد. */
    fun newInput(hint: String, inputType: Int, prefill: String? = null): EditText {
        val et = EditText(ctx)
        et.hint = hint
        et.inputType = inputType
        et.isSingleLine = true
        et.textSize = 13.5f
        et.setTextColor(ContextCompat.getColor(ctx, R.color.textMain))
        et.setHintTextColor(ContextCompat.getColor(ctx, R.color.textDim))
        et.setPadding(UiKit.dp(ctx, 12), 0, UiKit.dp(ctx, 12), 0)
        val d = ctx.resources.displayMetrics.density
        et.background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(ctx, R.color.inputBg))
            cornerRadius = 14 * d
            setStroke((1 * d).toInt(), ContextCompat.getColor(ctx, R.color.colorStroke2))
        }
        if (prefill != null) et.setText(prefill)
        et.setOnTouchListener { v, _ ->
            // پنجره به‌طور پیش‌فرض NOT_FOCUSABLE است تا بازی متوقف نشود؛
            // هنگام لمسِ فیلد، پنجره «قابل ویرایش» می‌شود تا کیبورد باز شود.
            makeWindowEditable()
            v.post {
                v.requestFocus()
                openPanel()
                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
            }
            true
        }
        et.setOnFocusChangeListener { v, has ->
            if (has) {
                activeInput = et
                openPanel()
                makeWindowEditable()
                v.postDelayed({
                    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }, 140)
            } else if (activeInput === et) {
                activeInput = null
                restoreWindowPassThrough()
            }
        }
        return et
    }

    private fun makeWindowEditable() {
        val p = params ?: return
        if (keyboardOpen) return
        keyboardOpen = true
        // پنجره «قابل فوکوس» می‌شود تا کیبورد باز شود و پنجره بالای کیبورد جمع شود
        p.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        runCatching { wm.updateViewLayout(rootView, p) }
    }

    private fun restoreWindowPassThrough() {
        val p = params ?: return
        if (!keyboardOpen) return
        keyboardOpen = false
        p.flags = baseWindowFlags()
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        runCatching { wm.updateViewLayout(rootView, p) }
    }

    private fun releaseConsoleFocus() {
        val et = activeInput
        activeInput = null
        et?.clearFocus()
        restoreWindowPassThrough()
    }

    // =====================================================================
    // RCON: اتصال به کنسول سرور
    // =====================================================================

    fun isRconConnected(): Boolean = rcon?.isOpen == true

    /** بین «شروع اتصال» و «برقراری کامل» هستیم؟ */
    val rconAttempting: Boolean
        get() = rcon != null && rcon?.isOpen != true

    fun registerStateListener(fn: () -> Unit) {
        stateListeners.add(fn)
    }

    fun registerConsoleListener(fn: (String) -> Unit) {
        consoleListeners.add(fn)
    }

    private fun notifyState() {
        stateListeners.forEach { it() }
    }

    /** اتصال با مشخصات ذخیره‌شده در تنظیمات. */
    fun rconConnect(): Boolean {
        val host = prefs.rconHost.trim()
        if (host.isEmpty()) {
            flash("آدرس سرور وارد نشده", "ابتدا در زبانهٔ «سرور» آدرس را بنویس و ذخیره کن")
            switchTab(1)
            return false
        }
        if (rcon?.isOpen == true) rcon?.stop()
        rcon = RconConnection(
            host = host,
            port = prefs.rconPort,
            password = prefs.rconPassword,
            onEvent = { ev -> main.post { handleRconEvent(ev) } },
        )
        rcon?.start()
        return true
    }

    fun rconDisconnect() {
        val c = rcon
        rcon = null
        c?.stop()
        notifyState()
    }

    private fun handleRconEvent(ev: RconEvent) {
        when (ev) {
            is RconEvent.Connecting -> flash("در حال اتصال به کنسول سرور…", prefs.rconHost)
            is RconEvent.Auth ->
                if (ev.ok) {
                    if (prefs.vibrationOn) UiKit.vibrate(ctx, 30)
                    flash("✓ به کنسول سرور وصل شدی")
                } else {
                    flash("رد شد: ${ev.message}", "رمز یا آدرس RCON را در «تنظیمات» بررسی کن")
                }
            is RconEvent.Output -> consoleListeners.forEach { it(ev.text) }
            is RconEvent.Closed ->
                consoleListeners.forEach { it("⛔ ${ev.reason}") }
        }
        notifyState()
    }

    /** ارسال مستقیم به کنسول سرور (فقط وقتی وصل است). */
    fun sendConsole(command: String) {
        if (command.trim().isEmpty()) return
        val r = rcon
        if (r != null && r.isOpen) {
            val ok = r.send(command)
            if (!ok) flash("ارسال ناموفق بود", "اتصال را دوباره برقرار کن")
        } else {
            flash("به سرور وصل نیست", "از زبانهٔ «سرور» اتصال RCON را روشن کن")
            switchTab(1)
        }
    }

    /**
     * دستورهای سریع: اگر به RCON وصل باشیم می‌فرستد؛
     * وگرنه اگر «چت‌پذیر» باشد (مثل تکنفره/چیت) در کلیپ‌بورد کپی می‌کند.
     */
    fun quickCommand(raw: String, chatEligible: Boolean) {
        if (raw.trim().isEmpty()) return
        val r = rcon
        if (r != null && r.isOpen) {
            val ok = r.send(raw)
            flash(if (ok) "✓ دستور ارسال شد" else "خطا در ارسال", raw)
        } else if (chatEligible) {
            copyText(raw)
            flash("📋 کپی شد — در چت بازی Paste کن", raw)
        } else {
            flash("این دستور به سرورِ شخصی وصل می‌شود", "ابتدا در زبانهٔ «سرور» اتصال RCON را وصل کن")
            switchTab(1)
        }
    }

    fun copyText(raw: String) {
        val text = if (raw.startsWith("/")) raw else "/$raw"
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("atta", text))
    }

    /** جایگزینی {p} با نام بازیکن هدف. */
    fun resolve(template: String): String =
        template.replace("{p}", prefs.playerName.trim().ifEmpty { "@s" })

    fun vibrate() {
        if (prefs.vibrationOn) UiKit.vibrate(ctx)
    }

    // =====================================================================
    // دیالوگ‌های کوچک
    // =====================================================================

    /** دیالوگ اتصال RCON (برای زبانهٔ سرور/دنیا و تنظیمات). */
    fun showRconDialog(title: String, autoConnectAfterSave: Boolean) {
        val density = ctx.resources.displayMetrics.density
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (8 * density).toInt(), (18 * density).toInt(), 0)
        }
        val hostEt = dialogInput("مثلاً 192.168.1.10 یا localhost", prefs.rconHost)
        val portEt = dialogInput("پورت RCON", prefs.rconPort.toString())
        portEt.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        val passEt = dialogInput("رمز RCON (rcon.password)", prefs.rconPassword)
        passEt.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        box.addView(dialogLabel("آدرس سرور"))
        box.addView(hostEt)
        box.addView(dialogLabel("پورت"))
        box.addView(portEt)
        box.addView(dialogLabel("رمز"))
        box.addView(passEt)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage("اتصال RCON به سرورِ خودت — برای سرورهای جاوا و بدراک روی همون دستگاه یا شبکهٔ داخلی")
            .setView(box)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره") { _, _ ->
                prefs.rconHost = hostEt.text.toString()
                prefs.rconPort = portEt.text.toString().toIntOrNull() ?: 25575
                prefs.rconPassword = passEt.text.toString()
                if (autoConnectAfterSave) rconConnect()
            }
            .show()
    }

    /** دیالوگ پرسیدن یک متن (برای دستورهایی که نام بازیکن می‌خواهند). */
    fun promptText(title: String, hint: String, prefill: String? = null, onResult: (String) -> Unit) {
        val et = dialogInput(hint, prefill)
        val density = ctx.resources.displayMetrics.density
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (6 * density).toInt(), (18 * density).toInt(), 0)
        }
        wrap.addView(et)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(wrap)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ادامه") { _, _ ->
                val t = et.text.toString().trim()
                if (t.isNotEmpty()) onResult(t)
            }
            .show()
    }

    /** دیالوگ تأیید برای کارهای خطرناک (استاپ سرور و…). */
    fun confirm(title: String, message: String, onYes: () -> Unit) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("نه، منصرف شدم", null)
            .setPositiveButton("بله، انجام بده") { _, _ -> onYes() }
            .show()
    }

    private fun dialogInput(hint: String, prefill: String?): EditText {
        val et = EditText(ctx)
        et.hint = hint
        et.isSingleLine = true
        et.textSize = 14f
        et.setTextColor(ContextCompat.getColor(ctx, R.color.textMain))
        et.setHintTextColor(ContextCompat.getColor(ctx, R.color.textDim))
        val density = ctx.resources.displayMetrics.density
        et.background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(ctx, R.color.inputBg))
            cornerRadius = 14 * density
            setStroke((1 * density).toInt(), ContextCompat.getColor(ctx, R.color.colorStroke2))
        }
        et.setPadding(
            (12 * density).toInt(), (4 * density).toInt(),
            (12 * density).toInt(), (4 * density).toInt()
        )
        if (prefill != null) et.setText(prefill)
        return et
    }

    private fun dialogLabel(text: String): TextView =
        UiKit.formLabel(ctx, text).apply {
            setPadding(0, UiKit.dp(ctx, 12), 0, UiKit.dp(ctx, 4))
        }
}
