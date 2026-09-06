package com.arena.mineva

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.server.CatalogItem
import com.arena.mineva.server.PackageCatalog
import com.arena.mineva.server.PackageCatalogUpdater
import com.arena.mineva.server.PackageDeployer
import com.arena.mineva.server.PackageLinkValidator
import com.arena.mineva.server.ServerConfig
import com.arena.mineva.server.ServerTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PackageManagerActivity : AppCompatActivity() {

    private val tts by lazy { TextToSpeechManager(this) }
    private val catalog by lazy { PackageCatalog(this) }
    private val deployer by lazy { PackageDeployer() }

    private lateinit var body: LinearLayout
    private lateinit var remoteSection: LinearLayout
    private lateinit var progress: LinearLayout
    private var category = "plugin"
    private var serverConfig: ServerConfig? = null
    private var remotePassword: EditText? = null
    private var remoteKeyPass: EditText? = null
    private var remoteFieldsAdded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts.init { tts.speak("بخش مدیریت پکیجها. می‌توانی پکیج را با چک سلامت لینک دانلود و روی سرور نصب کنی.") }

        serverConfig = ServerConfig.fromJson(AppPrefs.lastServerConfigJson)
        val root = Ui.fill(this)
        root.addView(Ui.text(this, "📦 مدیریت پکیجها", 22f, 0xFF1F8F8F.toInt(), bold = true))
        root.addView(
            Ui.text(
                this,
                "پلاگین، ماد، مودپک، شیدر و تکستچر. برای آیتم‌های با لینک مستقیم، دانلود و نصب روی سرور انجام می‌شود.",
                13f,
                0xFF9FB2C2.toInt()
            )
        )
        root.addView(
            Ui.button(this, "🔄 به‌روزرسانی خودکار فهرست + چک سازگاری", 0xFF2E9BFF.toInt(), 48f) {
                refreshCatalog()
            }
        )
        remoteSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(remoteSection)

        val scroll = ScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        progress = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(progress)

        setupRemoteFields()
        setContentView(root)
        refreshList()
    }

    private fun setupRemoteFields() {
        val cfg = serverConfig
        if (cfg == null || cfg.target != ServerTarget.VPS) return
        if (remoteFieldsAdded) return
        remoteFieldsAdded = true
        remoteSection.addView(Ui.text(this, "هدف نصب: VPS — ${cfg.host}", 14f, 0xFF2E70B8.toInt(), bold = true))
        remotePassword = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            hint = "رمز عبور SSH (اگر با رمز وصل می‌شوی)"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9FB2C2.toInt())
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = Ui.card(this@PackageManagerActivity).background
            setPadding(Ui.dp(this@PackageManagerActivity, 12f), Ui.dp(this@PackageManagerActivity, 10f), Ui.dp(this@PackageManagerActivity, 12f), Ui.dp(this@PackageManagerActivity, 10f))
        }
        remoteKeyPass = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            hint = "رمز کلید SSH (اگر کلید encrypted است)"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9FB2C2.toInt())
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = Ui.card(this@PackageManagerActivity).background
            setPadding(Ui.dp(this@PackageManagerActivity, 12f), Ui.dp(this@PackageManagerActivity, 10f), Ui.dp(this@PackageManagerActivity, 12f), Ui.dp(this@PackageManagerActivity, 10f))
        }
        remoteSection.addView(remotePassword)
        remoteSection.addView(remoteKeyPass)
    }

    private fun renderCategoryButtons() {
        body.addView(Ui.text(this, "دسته‌ها", 16f, 0xFFF1F5F9.toInt(), bold = true))
        val row = Ui.horizontal(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val names = listOf(
            "plugin" to "پلاگین",
            "mod" to "ماد",
            "modpack" to "مودپک",
            "resourcepack" to "شیدر/تکسچر"
        )
        names.forEach { (key, label) ->
            val btn = Ui.button(this, label, if (category == key) 0xFF2E9BFF.toInt() else 0xFF1C2836.toInt(), 42f).apply {
                layoutParams = LinearLayout.LayoutParams(0, Ui.dp(this@PackageManagerActivity, 42), 1f)
                setPadding(Ui.dp(this@PackageManagerActivity, 4f), 0, Ui.dp(this@PackageManagerActivity, 4f), 0)
            }
            btn.setOnClickListener {
                category = key
                refreshList()
            }
            row.addView(btn)
        }
        body.addView(row)
    }

    private fun refreshCatalog() {
        val version = serverConfig?.version ?: "1.21"
        appendProgress("در حال به‌روزرسانی فهرست و چک سازگاری...")
        lifecycleScope.launch(Dispatchers.IO) {
            val result = PackageCatalogUpdater.updateAll(
                this@PackageManagerActivity,
                serverVersion = version,
                limit = 20,
                onProgress = { s -> withContext(Dispatchers.Main) { appendProgress(s) } }
            )
            withContext(Dispatchers.Main) {
                catalog.reload()
                appendProgress(result.summary())
                tts.speak("فهرست پکیج‌ها به‌روز شد. ${result.updated.size} مورد جدید.")
                refreshList()
            }
        }
    }

    private fun refreshList() {
        body.removeAllViews()
        renderCategoryButtons()
        val items = when (category) {
            "modpack" -> catalog.byCategory("modpack")
            "resourcepack" -> catalog.byCategory("resourcepack") + catalog.byCategory("shader") + catalog.byCategory("texturepack")
            else -> catalog.byCategory(category)
        }
        if (items.isEmpty()) {
            body.addView(Ui.text(this, "موردی در این دسته نیست.", 13f, 0xFF9FB2C2.toInt()))
            return
        }
        items.forEach { renderItem(it) }
        body.addView(
            Ui.text(this, "آیتم‌هایی که لینک مستقیم دارند قابل نصب خودکار روی سرور هستند؛ بقیه فقط منبع مرجع‌اند.", 11f, 0xFF9FB2C2.toInt())
        )
    }

    private fun renderItem(item: CatalogItem) {
        val card = Ui.card(this)
        val installed = AppPrefs.packageInstalled(item.id)
        card.addView(Ui.text(this, "${item.name}  ${item.version}", 15f, 0xFFF1F5F9.toInt(), bold = true))
        card.addView(Ui.text(this, item.description, 12f, 0xFF9FB2C2.toInt()))

        val targetVersion = serverConfig?.version ?: "1.21"
        val compat = item.isCompatibleWith(targetVersion)
        card.addView(
            Ui.text(
                this,
                if (compat) "✓ سازگار با نسخه سرور: ${targetVersion}" else "✖ ممکن است با نسخه سرور ناسازگار باشد",
                12f,
                if (compat) 0xFF35D07F.toInt() else 0xFFC97C22.toInt(),
                bold = true
            )
        )

        val linkLine = if (item.hasDirectDownload()) {
            "📥 لینک مستقیم: ${item.downloadUrl.take(80)}"
        } else {
            "📄 فقط مرجع (لینک مستقیم ثبت نشده): ${item.url}"
        }
        card.addView(Ui.text(this, linkLine, 11f, if (item.hasDirectDownload()) 0xFF35D07F.toInt() else 0xFFC97C22.toInt()))

        val row = Ui.horizontal(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val install = Ui.button(this, if (installed) "حذف" else "نصب", if (installed) 0xFFFF5A5A.toInt() else 0xFF35D07F.toInt(), 42f).apply {
            layoutParams = LinearLayout.LayoutParams(0, Ui.dp(this@PackageManagerActivity, 42), 1f)
        }
        install.setOnClickListener {
            if (installed) removeOne(item) else installOne(item)
        }
        val check = Ui.button(this, "🔍 چک لینک", 0xFF2E9BFF.toInt(), 42f).apply {
            layoutParams = LinearLayout.LayoutParams(0, Ui.dp(this@PackageManagerActivity, 42), 1f)
            setPadding(Ui.dp(this@PackageManagerActivity, 6f), 0, Ui.dp(this@PackageManagerActivity, 6f), 0)
        }
        check.setOnClickListener { checkLink(item) }
        row.addView(install)
        row.addView(check)
        card.addView(row)
        body.addView(card)
    }

    private fun checkLink(item: CatalogItem) {
        appendProgress("چک لینک ${item.name}...")
        lifecycleScope.launch(Dispatchers.IO) {
            val health = PackageLinkValidator.check(
                if (item.hasDirectDownload()) item.downloadUrl else item.url
            )
            withContext(Dispatchers.Main) {
                appendProgress(
                    "${item.name}: HTTP ${health.code} | ${health.contentType.ifBlank { "unknown" }} | " +
                        if (health.isDirectDownload) "قابل دانلود ✓" else health.detail
                )
                tts.speak("نتیجه چک لینک برای ${item.name} آماده شد.")
            }
        }
    }

    private fun installOne(item: CatalogItem) {
        if (!item.hasDirectDownload()) {
            tts.speak("${item.name} لینک مستقیم ثبت‌شده ندارد؛ فقط مرجع است.")
            appendProgress("${item.name}: لینک مستقیم ندارد.")
            return
        }
        val cfg = serverConfig ?: ServerConfig(target = ServerTarget.LOCAL)
        val pw = remotePassword?.text?.toString().orEmpty()
        val keyPass = remoteKeyPass?.text?.toString().orEmpty()

        appendProgress("شروع نصب ${item.name}...")
        lifecycleScope.launch(Dispatchers.IO) {
            val result = deployer.deploy(
                context = this@PackageManagerActivity,
                config = cfg,
                item = item,
                sshPassword = pw,
                keyPassphrase = keyPass,
                onProgress = { s -> withContext(Dispatchers.Main) { appendProgress(s) } }
            )
            withContext(Dispatchers.Main) {
                if (result.success) {
                    AppPrefs.setPackageInstalled(item.id, true)
                    appendProgress("✅ ${item.name} نصب شد.")
                    tts.speak("${item.name} با موفقیت نصب شد.")
                } else {
                    appendProgress("❌ ${result.output}")
                    tts.speak("نصب ${item.name} انجام نشد. خروجی را چک کن.")
                }
                refreshList()
            }
        }
    }

    private fun removeOne(item: CatalogItem) {
        val cfg = serverConfig ?: ServerConfig(target = ServerTarget.LOCAL)
        val pw = remotePassword?.text?.toString().orEmpty()
        val keyPass = remoteKeyPass?.text?.toString().orEmpty()

        appendProgress("شروع حذف ${item.name}...")
        lifecycleScope.launch(Dispatchers.IO) {
            val result = deployer.remove(
                config = cfg,
                item = item,
                sshPassword = pw,
                keyPassphrase = keyPass,
                onProgress = { s -> withContext(Dispatchers.Main) { appendProgress(s) } }
            )
            withContext(Dispatchers.Main) {
                if (result.success) {
                    AppPrefs.removePackage(item.id)
                    appendProgress("${item.name} حذف شد.")
                    tts.speak("${item.name} حذف شد.")
                } else {
                    appendProgress("❌ ${result.output}")
                    tts.speak("حذف ${item.name} انجام نشد.")
                }
                refreshList()
            }
        }
    }

    private fun appendProgress(text: String) {
        progress.addView(Ui.text(this, text, 12f, 0xFFD8E3EC.toInt()))
        progress.post { (progress.parent as? ScrollView)?.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) tts.shutdown()
    }
}
