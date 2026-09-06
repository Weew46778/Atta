package com.arena.mineva.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Self-updater for the application.
 *
 * Checks a remote JSON manifest (app/update_manifest.json) for a newer versionCode,
 * health-checks the APK URL, downloads the APK, verifies the optional SHA-256, and hands
 * it to the Android package installer through FileProvider.
 *
 * The manifest URL is the public GitHub raw path so a released APK on that repo can be
 * picked up without code changes.
 */
object AppUpdater {

    const val UPDATE_MANIFEST_URL =
        "https://raw.githubusercontent.com/Weew46778/Atta/main/app/update_manifest.json"

    data class UpdateInfo(
        val latestVersion: String = "",
        val latestVersionCode: Long = 0,
        val apkUrl: String = "",
        val sha256: String = "",
        val notes: String = "",
        val mandatory: Boolean = false,
        val updateAvailable: Boolean = false,
        val detail: String = ""
    )

    data class InstallResult(val success: Boolean, val detail: String)

    fun currentVersion(context: Context): Pair<Long, String> = runCatching {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        code to (info.versionName ?: "0.1.0")
    }.getOrDefault(0L to "0.1.0")

    fun check(context: Context, manifestUrl: String = UPDATE_MANIFEST_URL): UpdateInfo {
        return runCatching {
            val conn = URL(manifestUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            val code = conn.responseCode
            if (code != 200) {
                return UpdateInfo(detail = "بررسی بروزرسانی در دسترس نیست (HTTP $code).")
            }
            val raw = conn.inputStream.bufferedReader().readText()
            val o = JSONObject(raw)
            val latestCode = o.optLong("versionCode", 0L)
            val latestVersion = o.optString("versionName")
            val apkUrl = o.optString("apkUrl")
            val sha256 = o.optString("sha256")
            val notes = o.optString("notes")
            val mandatory = o.optBoolean("mandatory", false)

            val (current, currentVer) = currentVersion(context)
            val available = latestCode > 0 && latestCode > current
            val detail = when {
                latestCode == 0L -> "مانیفست بروزرسانی ناقص است."
                !available -> "اپ شما به‌روز است ($currentVer)."
                apkUrl.isBlank() -> "نسخه $latestVersion موجود است، اما لینک مستقیم APK هنوز منتشر نشده."
                else -> "نسخه $latestVersion در دسترس است ($currentVer → $latestVersion)."
            }
            UpdateInfo(
                latestVersion = latestVersion,
                latestVersionCode = latestCode,
                apkUrl = apkUrl,
                sha256 = sha256,
                notes = notes,
                mandatory = mandatory,
                updateAvailable = available,
                detail = detail
            )
        }.getOrElse { e ->
            UpdateInfo(detail = "خطا در بررسی بروزرسانی: ${e.message ?: "نامشخص"}")
        }
    }

    fun download(context: Context, info: UpdateInfo): InstallResult {
        if (info.apkUrl.isBlank()) return InstallResult(false, "لینک APK خالی است.")
        return runCatching {
            val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code != 200) return InstallResult(false, "دانلود APK ناموفق (HTTP $code).")

            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates")
            dir.mkdirs()
            val apk = File(dir, "mineava-update.apk")
            conn.inputStream.use { input ->
                apk.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    val max = 200L * 1024 * 1024
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > max) return InstallResult(false, "حجم APK از حد مجاز بیشتر است.")
                        output.write(buffer, 0, n)
                    }
                }
            }
            conn.disconnect()

            if (info.sha256.isNotBlank()) {
                val actual = sha256(apk)
                if (!actual.equals(info.sha256, ignoreCase = true)) {
                    apk.delete()
                    return InstallResult(false, "چک‌سام APK مطابقت ندارد (SHA-256).")
                }
            }

            installApk(context, apk)
        }.getOrElse { e ->
            InstallResult(false, "خطا در دانلود/نصب APK: ${e.message ?: "نامشخص"}")
        }
    }

    private fun installApk(context: Context, apk: File): InstallResult {
        return runCatching {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            InstallResult(true, "دانلود شد؛ صفحه نصب اندروید باز شد.")
        }.getOrElse { e ->
            InstallResult(false, "باز کردن نصب‌کننده ناموفق بود: ${e.message ?: "نامشخص"}")
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
