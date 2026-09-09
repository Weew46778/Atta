package com.atta.mcpanel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.atta.mcpanel.core.AppPrefs
import com.atta.mcpanel.overlay.OverlayService
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * لانچر Atta: بازی ماینکرفت را اجرا می‌کند و پنل اپراتور را روی آن روشن می‌کند.
 */
class MainActivity : AppCompatActivity() {

    private class GameInfo(
        val packageName: String,
        val title: String,
        val note: String,
        val webUrl: String? = null,
        val playId: String? = null,
    )

    private class RowViews(
        val stateTv: TextView,
        val btn: MaterialButton,
    )

    private val games = listOf(
        GameInfo(
            packageName = "com.mojang.minecraftpe",
            title = "ماینکرفت — نسخهٔ موبایل (بدراک)",
            note = "نسخهٔ رسمی اندروید از گوگل‌پلی",
            playId = "com.mojang.minecraftpe",
        ),
        GameInfo(
            packageName = "net.kdt.pojavlaunch",
            title = "ماینکرفت جاوا — PojavLauncher",
            note = "نسخهٔ جاوا روی اندروید (متن‌باز و رایگان)",
            webUrl = "https://github.com/PojavLauncherTeam/PojavLauncher/releases",
        ),
    )

    private lateinit var prefs: AppPrefs
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvPermission: TextView
    private lateinit var overlayActions: LinearLayout
    private lateinit var gameRowsContainer: LinearLayout
    private lateinit var swOverlay: SwitchMaterial
    private lateinit var swAutoPanel: SwitchMaterial

    private val rows = mutableMapOf<String, RowViews>()
    private var suppressSwitch = false
    private var pendingNotificationStart = false

    private val notificationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { _ ->
            pendingNotificationStart = false
            // چه اجازه داده شود چه نه، پنل باید روشن شود (فقط اعلان مخفی می‌ماند)
            if (Settings.canDrawOverlays(this) && !OverlayService.isRunning()) {
                OverlayService.start(this)
            }
            refreshOverlayState()
        }

    // ------------------------------------------------------------------ //

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = AppPrefs(this)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvPermission = findViewById(R.id.tvPermission)
        overlayActions = findViewById(R.id.overlayActions)
        gameRowsContainer = findViewById(R.id.gameRows)
        swOverlay = findViewById(R.id.swOverlay)
        swAutoPanel = findViewById(R.id.swAutoPanel)

        buildGameRows()

        swAutoPanel.isChecked = prefs.autoPanelOnLaunch
        swAutoPanel.setOnCheckedChangeListener { _, checked ->
            prefs.autoPanelOnLaunch = checked
        }

        swOverlay.setOnCheckedChangeListener { _, checked ->
            if (suppressSwitch) return@setOnCheckedChangeListener
            if (checked) requestStartOverlay() else OverlayService.stop(this)
        }

        refreshOverlayState()
    }

    override fun onResume() {
        super.onResume()
        refreshOverlayState()
        refreshGameRows()
    }

    // ------------------------------------------------------------------ //
    // ردیف‌های بازی
    // ------------------------------------------------------------------ //

    private fun isInstalled(pkg: String): Boolean =
        packageManager.getLaunchIntentForPackage(pkg) != null

    private fun buildGameRows() {
        gameRowsContainer.removeAllViews()
        rows.clear()
        games.forEach { game ->
            val v = layoutInflater.inflate(R.layout.row_game, gameRowsContainer, false)
            v.findViewById<TextView>(R.id.tvGameName).text = game.title
            v.findViewById<TextView>(R.id.tvGameHint).text = game.note
            val stateTv = v.findViewById<TextView>(R.id.tvGameState)
            val btn = v.findViewById<MaterialButton>(R.id.btnGameAction)
            btn.setOnClickListener { onGameAction(game) }
            rows[game.packageName] = RowViews(stateTv, btn)
            gameRowsContainer.addView(
                v,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(8), 0, 0)
                }
            )
        }
        refreshGameRows()
    }

    private fun refreshGameRows() {
        games.forEach { game ->
            val rv = rows[game.packageName] ?: return@forEach
            val installed = isInstalled(game.packageName)
            rv.btn.text = if (installed) "اجرا" else "نصب"
            rv.stateTv.text = if (installed) "نصب است ✓" else "روی این دستگاه نیست"
            rv.stateTv.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (installed) R.color.accent else R.color.gold
                )
            )
        }
    }

    private fun onGameAction(game: GameInfo) {
        if (isInstalled(game.packageName)) {
            launchGame(game)
        } else {
            openInstallPage(game)
        }
    }

    private fun launchGame(game: GameInfo) {
        val launch = packageManager.getLaunchIntentForPackage(game.packageName) ?: return
        if (prefs.autoPanelOnLaunch) {
            if (Settings.canDrawOverlays(this)) {
                if (!OverlayService.isRunning()) OverlayService.start(this)
            } else {
                toast("پنل روشن نشد: اول «اجازهٔ نمایش روی برنامه‌ها» را بده، بعد دوباره اجرا کن")
            }
        }
        startActivity(launch)
    }

    private fun openInstallPage(game: GameInfo) {
        if (!game.webUrl.isNullOrEmpty()) {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(game.webUrl)))
            }.onFailure { toast("مرورگری برای باز کردن لینک پیدا نشد") }
        } else if (!game.playId.isNullOrEmpty()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${game.playId}"))
            runCatching { startActivity(intent) }.onFailure {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=${game.playId}")
                    )
                )
            }
        }
    }

    // ------------------------------------------------------------------ //
    // وضعیت پنل
    // ------------------------------------------------------------------ //

    private fun canOverlay(): Boolean = Settings.canDrawOverlays(this)

    private fun refreshOverlayState() {
        suppressSwitch = true
        val running = OverlayService.isRunning()

        swOverlay.isChecked = running
        tvOverlayStatus.text = if (running) "پنل: روشن" else "پنل: خاموش"
        tvOverlayStatus.setTextColor(
            ContextCompat.getColor(this, if (running) R.color.accent else R.color.textDim)
        )

        if (canOverlay()) {
            tvPermission.text = "✓ اجازهٔ نمایش روی سایر برنامه‌ها داده شده است"
            tvPermission.setTextColor(ContextCompat.getColor(this, R.color.accent))
        } else {
            tvPermission.text = "اجازهٔ نمایش روی سایر برنامه‌ها داده نشده است — برای پنل شناور لازم است"
            tvPermission.setTextColor(ContextCompat.getColor(this, R.color.danger))
        }

        overlayActions.removeAllViews()
        if (!canOverlay()) {
            val btn = MaterialButton(this).apply {
                text = "اعطای اجازهٔ نمایش روی برنامه‌ها"
                isAllCaps = false
                textSize = 13f
                minHeight = 0
                insetTop = 0
                insetBottom = 0
                setOnClickListener { goManageOverlayPermission() }
            }
            overlayActions.addView(
                btn,
                LinearLayout.LayoutParams(0, dp(44), 1f)
            )
        } else {
            val btnState = MaterialButton(this).apply {
                text = if (running) "خاموش کردن پنل" else "روشن کردن پنل"
                isAllCaps = false
                textSize = 13f
                minHeight = 0
                insetTop = 0
                insetBottom = 0
                setOnClickListener {
                    if (running) OverlayService.stop(this@MainActivity)
                    else requestStartOverlay()
                }
            }
            overlayActions.addView(
                btnState,
                LinearLayout.LayoutParams(0, dp(44), 1f)
            )
            val btnTile = MaterialButton(this).apply {
                text = "افزودن کاشی سریع"
                isAllCaps = false
                textSize = 13f
                minHeight = 0
                insetTop = 0
                insetBottom = 0
                setOnClickListener {
                    Toast.makeText(
                        this@MainActivity,
                        "از بالای صفحه دو بار پایین بکش → ویرایش کاشی‌ها → «پنل اپراتور» را بکش و رها کن",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            overlayActions.addView(
                btnTile,
                LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(dp(8), 0, 0, 0)
                }
            )
        }
        suppressSwitch = false
    }

    private fun requestStartOverlay() {
        if (!canOverlay()) {
            swOverlay.isChecked = false
            goManageOverlayPermission()
            return
        }
        startOverlayServiceIfAllowed()
    }

    private fun startOverlayServiceIfAllowed() {
        if (!canOverlay()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (!pendingNotificationStart) {
                pendingNotificationStart = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            return
        }
        OverlayService.start(this)
        refreshOverlayState()
    }

    private fun goManageOverlayPermission() {
        val i = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(i) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun dp(v: Int): Int = (resources.displayMetrics.density * v).toInt()
}
