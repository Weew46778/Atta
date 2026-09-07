package com.arena.mineva

import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.server.PackageCatalog
import com.arena.mineva.server.ServerConfig
import com.arena.mineva.server.ServerProfileStore
import com.arena.mineva.server.ServerEdition
import com.arena.mineva.server.ServerRecipeGenerator
import com.arena.mineva.server.ServerTarget
import com.arena.mineva.server.VpsProvisioner
import com.arena.mineva.guide.GuideController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerWizardActivity : AppCompatActivity() {

    private val tts by lazy { TextToSpeechManager(this) }

    private lateinit var targetLocal: android.widget.TextView
    private lateinit var targetVps: android.widget.TextView
    private lateinit var profileNameField: EditText
    private lateinit var vpsFields: LinearLayout
    private lateinit var hostField: EditText
    private lateinit var userField: EditText
    private lateinit var keyField: EditText
    private lateinit var keyPassphraseField: EditText
    private lateinit var sshPortField: EditText
    private lateinit var sshPasswordField: EditText
    private lateinit var versionField: EditText
    private lateinit var memoryField: EditText
    private lateinit var playersField: EditText
    private lateinit var editionJava: android.widget.TextView
    private lateinit var editionBedrock: android.widget.TextView
    private lateinit var editionHybrid: android.widget.TextView
    private lateinit var result: LinearLayout
    private lateinit var deployButton: TextView
    private var keyPickerPending = false

    private var target = ServerTarget.LOCAL
    private var edition = ServerEdition.BEDROCK

    private val javaCheck = CheckBox(this)
    private val bedrockCheck = CheckBox(this).apply { isChecked = true }
    private val geyserCheck = CheckBox(this)
    private val bungeeCheck = CheckBox(this)

    private val catalog = PackageCatalog(this)
    private lateinit var pluginChecks: MutableList<CheckBox>
    private lateinit var modChecks: MutableList<CheckBox>
    private lateinit var packChecks: MutableList<CheckBox>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
                tts.init {
                    tts.speak("ساخت سرور خودکار. بگو سرور را کجا بسازم یا از گزینهها انتخاب کن.")
                }
        }
        val root = Ui.fill(this)
        root.addView(Ui.text(this, "🛠 ساخت سرور خودکار", 22f, 0xFF2E70B8.toInt(), bold = true))
        root.addView(Ui.text(this, "چند سوال کوتاه بپرس، بقیه را آوا خودش انجام میدهد. پاسخ نهایی: فایل دستور آماده.", 13f, 0xFF9FB2C2.toInt()))

        root.addView(section("نام پروفایل در پیشخوان"))
        profileNameField = field("مثلاً سرور آوا VPS")
        profileNameField.hint = "نام سرور در پیشخوان (اختیاری)"
        root.addView(profileNameField)

        root.addView(section("سرور کجا ساخته شود؟"))
        val targetRow = Ui.horizontal(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        targetLocal = choice("📱 روی گوشی", true)
        targetVps = choice("🖥 روی VPS", false)
        targetLocal.setOnClickListener { setTarget(ServerTarget.LOCAL) }
        targetVps.setOnClickListener { setTarget(ServerTarget.VPS) }
        targetRow.addView(targetLocal, marginParams(0, 0, 8, 0))
        targetRow.addView(targetVps, marginParams(8, 0, 0, 0))
        root.addView(targetRow)

        vpsFields = Ui.vertical(this)
        hostField = field("آدرس VPS (ip یا domain)")
        userField = field("نام کاربری SSH")
        keyField = field("مسیر کلید SSH (اختیاری)")
        keyPassphraseField = passwordField("رمز کلید SSH (اختیاری)")
        sshPortField = field("پورت SSH")
        sshPortField.setText("22")
        sshPasswordField = passwordField("رمز عبور SSH (اختیاری)")
        val pickKey = Ui.button(this, "📂 انتخاب فایل کلید SSH", 0xFF2E70B8.toInt(), 46f) {
            pickKeyFile()
        }
        vpsFields.addView(hostField)
        vpsFields.addView(userField)
        vpsFields.addView(pickKey)
        vpsFields.addView(keyField)
        vpsFields.addView(keyPassphraseField)
        vpsFields.addView(sshPortField)
        vpsFields.addView(sshPasswordField)
        vpsFields.visibility = View.GONE
        root.addView(vpsFields)

        root.addView(section("نوع و نسخه سرور"))
        val editionRow = Ui.horizontal(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        editionJava = choice("Java", false)
        editionBedrock = choice("Bedrock", true)
        editionHybrid = choice("Hybrid", false)
        editionJava.setOnClickListener { setEdition(ServerEdition.JAVA) }
        editionBedrock.setOnClickListener { setEdition(ServerEdition.BEDROCK) }
        editionHybrid.setOnClickListener { setEdition(ServerEdition.HYBRID) }
        editionRow.addView(editionJava, marginParams(0, 0, 8, 0))
        editionRow.addView(editionBedrock, marginParams(8, 0, 8, 0))
        editionRow.addView(editionHybrid, marginParams(8, 0, 0, 0))
        root.addView(editionRow)

        versionField = field("نسخه (مثلاً 1.21)")
        versionField.setText("1.21")
        memoryField = field("رم سرور بر حسب MB")
        memoryField.setText("2048")
        playersField = field("حداکثر بازیکن")
        playersField.setText("10")
        root.addView(versionField)
        root.addView(memoryField)
        root.addView(playersField)

        root.addView(section("اتصال کاربران"))
        setupCheck(javaCheck, "ورود Minecraft Java")
        setupCheck(bedrockCheck, "ورود Minecraft Bedrock")
        setupCheck(geyserCheck, "Geyser (بد رک ↔ جاوا)")
        setupCheck(bungeeCheck, "BungeeCord (چند سرور)")
        root.addView(javaCheck)
        root.addView(bedrockCheck)
        root.addView(geyserCheck)
        root.addView(bungeeCheck)

        pluginChecks = mutableListOf()
        modChecks = mutableListOf()
        packChecks = mutableListOf()
        root.addView(section("پکیجهای پیشفرض"))
        root.addView(Ui.text(this, "پلاگینها", 14f, 0xFF2E9BFF.toInt(), bold = true))
        catalog.byCategory("plugin").take(5).forEach { item ->
            val cb = CheckBox(this).apply {
                text = "${item.name} (${item.version})"
                setTextColor(Color.WHITE)
            }
            pluginChecks.add(cb)
            root.addView(cb)
        }
        root.addView(Ui.text(this, "مادها", 14f, 0xFF2E9BFF.toInt(), bold = true))
        catalog.byCategory("mod").take(5).forEach { item ->
            val cb = CheckBox(this).apply { text = "${item.name} (${item.version})"; setTextColor(Color.WHITE) }
            modChecks.add(cb)
            root.addView(cb)
        }
        root.addView(Ui.text(this, "تکستچر/شیدر/منبع", 14f, 0xFF2E9BFF.toInt(), bold = true))
        (catalog.byCategory("resourcepack") + catalog.byCategory("shader")).take(6).forEach { item ->
            val cb = CheckBox(this).apply { text = "${item.name} (${item.version})"; setTextColor(Color.WHITE) }
            packChecks.add(cb)
            root.addView(cb)
        }

        root.addView(
            Ui.button(this, "🎓 راهنمای گام‌به‌گام VPS", 0xFFC97C22.toInt(), 48f) {
                startActivity(android.content.Intent(this, VpsGuideActivity::class.java))
            }
        )
        root.addView(
            Ui.button(this, "⚙️ ساخت خودکار سرور", 0xFF35D07F.toInt(), 56f) {
                buildServer()
            }
        )

        deployButton = Ui.button(this, "🖥 ساخت و اجرای خودکار روی VPS (SSH)", 0xFF2E70B8.toInt(), 56f) {
            deployVps()
        }
        deployButton.visibility = View.GONE
        root.addView(deployButton)

        result = Ui.vertical(this)
        root.addView(result)

        setContentView(root)
    }

    // ---------------------------------------------- helpers

    private fun setTarget(t: ServerTarget) {
        target = t
        updateChoices(targetLocal, t == ServerTarget.LOCAL)
        updateChoices(targetVps, t == ServerTarget.VPS)
        vpsFields.visibility = if (t == ServerTarget.VPS) LinearLayout.VISIBLE else LinearLayout.GONE
        deployButton.visibility = if (t == ServerTarget.VPS) View.VISIBLE else View.GONE
        tts.speak(if (t == ServerTarget.LOCAL) "روی گوشی. خیلی هم خوب." else "روی سرور مجازی. مشخصات SSH را بده.")
    }

    private fun setEdition(e: ServerEdition) {
        edition = e
        updateChoices(editionJava, e == ServerEdition.JAVA)
        updateChoices(editionBedrock, e == ServerEdition.BEDROCK)
        updateChoices(editionHybrid, e == ServerEdition.HYBRID)
        updateEditionHint()
        tts.speak("نسخه $e انتخاب شد.")
    }

    private fun updateEditionHint() {
        geyserCheck.isEnabled = edition == ServerEdition.JAVA || edition == ServerEdition.HYBRID
    }

    private fun choice(text: String, active: Boolean): android.widget.TextView {
        return Ui.text(this, text, 15f, if (active) Color.WHITE else 0xFF9FB2C2.toInt(), bold = active).apply {
            gravity = android.view.Gravity.CENTER
            setPadding(Ui.dp(this@ServerWizardActivity, 12f), Ui.dp(this@ServerWizardActivity, 12f), Ui.dp(this@ServerWizardActivity, 12f), Ui.dp(this@ServerWizardActivity, 12f))
            setBackgroundColor(if (active) 0xFF2E70B8.toInt() else 0xFF1C2836.toInt())
        }
    }

    private fun updateChoices(view: android.widget.TextView, active: Boolean) {
        view.textSize = 15f
        view.setTextColor(if (active) Color.WHITE else 0xFF9FB2C2.toInt())
        view.setTypeface(view.typeface, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        view.setBackgroundColor(if (active) 0xFF2E70B8.toInt() else 0xFF1C2836.toInt())
    }

    private fun field(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF9FB2C2.toInt())
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT
        background = Ui.card(this@ServerWizardActivity).background
        setPadding(Ui.dp(this@ServerWizardActivity, 12f), Ui.dp(this@ServerWizardActivity, 10f), Ui.dp(this@ServerWizardActivity, 12f), Ui.dp(this@ServerWizardActivity, 10f))
    }

    private fun passwordField(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF9FB2C2.toInt())
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        background = Ui.card(this@ServerWizardActivity).background
        setPadding(Ui.dp(this@ServerWizardActivity, 12f), Ui.dp(this@ServerWizardActivity, 10f), Ui.dp(this@ServerWizardActivity, 12f), Ui.dp(this@ServerWizardActivity, 10f))
    }

    private fun section(text: String): android.widget.TextView =
        Ui.text(this, text, 16f, 0xFFF1F5F9.toInt(), bold = true)

    private fun marginParams(l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(Ui.dp(this@ServerWizardActivity, l.toFloat()), Ui.dp(this@ServerWizardActivity, t.toFloat()), Ui.dp(this@ServerWizardActivity, r.toFloat()), Ui.dp(this@ServerWizardActivity, b.toFloat()))
        }

    private fun setupCheck(cb: CheckBox, label: String) {
        cb.text = label
        cb.setTextColor(Color.WHITE)
    }

    // ---------------------------------------------- build

    private fun createConfig(): ServerConfig = ServerConfig(
        target = target,
        edition = edition,
        version = versionField.text.toString().ifBlank { "1.21" },
        host = hostField.text.toString(),
        user = userField.text.toString(),
        sshKeyPath = keyField.text.toString(),
        sshKeyPassphrase = keyPassphraseField.text.toString(),
        sshPort = sshPortField.text.toString().toIntOrNull() ?: 22,
        sshPassword = sshPasswordField.text.toString(),
        port = if (edition == ServerEdition.JAVA) 25565 else 19132,
        maxPlayers = playersField.text.toString().toIntOrNull() ?: 10,
        memoryMb = memoryField.text.toString().toIntOrNull() ?: 2048,
        useGeyser = geyserCheck.isChecked,
        useBungee = bungeeCheck.isChecked,
        pluginNames = checkedNames(pluginChecks),
        modNames = checkedNames(modChecks),
        resourcePackNames = checkedNames(packChecks),
        allowJavaClients = javaCheck.isChecked,
        allowBedrockClients = bedrockCheck.isChecked
    )

    private fun buildServer() {
        result.removeAllViews()
        try {
            val config = createConfig()
            val recipe = ServerRecipeGenerator.generate(this, config)
            val profileId = ServerProfileStore.add(config, profileNameField.text.toString().trim().ifBlank { null })
            ServerProfileStore.setActive(profileId)
            AppPrefs.lastServerConfigJson = config.toJson()
            AppPrefs.homeServerTarget = config.target.name

            result.addView(Ui.text(this, "✅ سرور آماده شد", 20f, 0xFF35D07F.toInt(), bold = true))
            result.addView(Ui.text(this, "فایل: ${recipe.fileName}", 14f))
            result.addView(Ui.text(this, "مسیر: ${recipe.path}", 12f, 0xFF9FB2C2.toInt()))
            result.addView(Ui.text(this, recipe.notes, 13f, 0xFFD8E3EC.toInt()))
            result.addView(
                Ui.button(this, "📋 کپی اسکریپت", 0xFF2E70B8.toInt(), 46f) {
                    val cm = getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("mineava_server", recipe.script))
                    tts.speak("دستورات سرور در کلیپ‌بورد کپی شد.")
                }
            )
            GuideController.markStepDone("server", this, tts)
            tts.speak("آماده شد. فایل دستورات ساخته شد و در کلیپ‌بورد می‌توانی کپی کنی. برای ساخت واقعی روی VPS از دکمه «ساخت و اجرای خودکار روی VPS» استفاده کن.")
        } catch (e: Exception) {
            result.addView(Ui.text(this, "❌ در ساخت سرور خطا پیش آمد", 18f, 0xFFFF5A5A.toInt(), bold = true))
            result.addView(Ui.text(this, (e.message ?: e.javaClass.simpleName).take(400), 12f, 0xFFD8E3EC.toInt()))
            runCatching { tts.speak("در ساخت سرور خطایی رخ داد. متن خطا را از صفحه ببین.") }
        }
    }

    private fun pickKeyFile() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        keyPickerPending = true
        startActivityForResult(intent, 7001)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 7001) return
        keyPickerPending = false
        val uri = data?.data ?: return
        runCatching {
            val target = java.io.File(filesDir, "mineava_ssh_key")
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                target.setReadable(true)
            }
            keyField.setText(target.absolutePath)
            tts.speak("کلید SSH انتخاب و کپی شد.")
        }.onFailure {
            tts.speak("نتوانستم کلید را کپی کنم.")
        }
    }

    private fun deployVps() {
        result.removeAllViews()
        try {
            buildVps()
        } catch (e: Exception) {
            deployButton.isEnabled = true
            deployButton.text = "🖥 ساخت و اجرای خودکار روی VPS (SSH)"
            result.addView(Ui.text(this, "❌ خطا در شروع استقرار", 18f, 0xFFFF5A5A.toInt(), bold = true))
            result.addView(Ui.text(this, (e.message ?: e.javaClass.simpleName).take(400), 12f, 0xFFD8E3EC.toInt()))
            runCatching { tts.speak("در شروع استقرار خطایی رخ داد.") }
        }
    }

    private fun buildVps() {
        val config = createConfig()
        if (config.host.isBlank() || config.user.isBlank()) {
            tts.speak("اول آدرس VPS و نام کاربری SSH را وارد کن.")
            result.addView(Ui.text(this, "آدرس VPS و نام کاربری SSH را وارد کن.", 14f, 0xFFFF5A5A.toInt()))
            return
        }
        deployButton.isEnabled = false
        deployButton.text = "در حال اتصال..."
        val profileId = ServerProfileStore.add(config, profileNameField.text.toString().trim().ifBlank { null })
        ServerProfileStore.setActive(profileId)
        AppPrefs.lastServerConfigJson = config.toJson()
        AppPrefs.homeServerTarget = config.target.name
        result.removeAllViews()
        result.addView(Ui.text(this, "در حال اتصال به ${config.host}...", 14f, 0xFF2E9BFF.toInt()))
        tts.speak("در حال اتصال به سرور مجازی هستم. طول میکشد.")

        lifecycleScope.launch(Dispatchers.IO) {
            val outcome = VpsProvisioner().provision(this@ServerWizardActivity, config) { stage ->
                runOnUiThread { appendProgress("• $stage") }
            }
            withContext(Dispatchers.Main) {
                showDeployResult(outcome)
            }
        }
    }

    private fun showDeployResult(outcome: com.arena.mineva.server.VpsProvisioner.ProvisionResult) {
        deployButton.isEnabled = true
        deployButton.text = "🖥 ساخت و اجرای خودکار روی VPS (SSH)"
        appendProgress(if (outcome.success) "✅ ساخت سرور روی VPS تمام شد." else "⚠️ نیاز به بررسی.")
        if (outcome.success) GuideController.markStepDone("server", this, tts)
        result.addView(
            Ui.text(
                this,
                outcome.output.take(1200),
                12f,
                if (outcome.success) 0xFF35D07F.toInt() else 0xFFFF5A5A.toInt()
            )
        )
        result.addView(
            Ui.button(this, "📋 کپی خروجی", 0xFF2E70B8.toInt(), 44f) {
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("mineava_vps_output", outcome.output))
                tts.speak("خروجی سرور در کلیپبورد کپی شد.")
            }
        )
        tts.speak(
            if (outcome.success)
                "سرور روی VPS ساخته و اجرا شد. حالا از سرور مدیریت میتوانی وضعیت را ببینی."
            else
                "در ساخت سرور مشکلی پیش آمد. خروجی را از کلیپبورد بردار و چک کن."
        )
    }

    private fun appendProgress(text: String) {
        result.addView(Ui.text(this, text, 13f, 0xFF9FB2C2.toInt()))
    }

    private fun checkedNames(list: List<CheckBox>): List<String> =
        list.filter { it.isChecked }.map { it.text.toString() }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) tts.shutdown()
    }
}
