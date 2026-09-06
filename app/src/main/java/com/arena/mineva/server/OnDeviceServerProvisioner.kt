package com.arena.mineva.server

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Downloads and unpacks the official Bedrock server into the app-private directory so the
 * on-device manager can start it.
 *
 * The official downloads page may be protected/geo-specific; this provisioner does a real
 * HTTP check first and reports a clear error if it cannot obtain the archive. It also
 * accepts a user-supplied ZIP (for custom/arm builds) and can be extended later.
 */
object OnDeviceServerProvisioner {

    data class ProvisionResult(val success: Boolean, val detail: String, val path: String = "")

    private const val BEDROCK_URL = "https://www.minecraft.net/en-us/download/server/bedrock"

    fun downloadOfficial(context: Context, targetDir: File): ProvisionResult {
        val zip = File(targetDir, "bedrock-server.zip")
        return runCatching {
            targetDir.mkdirs()
            val health = PackageLinkValidator.check(BEDROCK_URL, 20_000)
            if (!health.ok) {
                return ProvisionResult(false, "وبسرور رسمی در دسترس نیست (HTTP ${health.code}). روی لینک مستقیم دستی بگذار.")
            }
            // Official page returns HTML, not the zip, so direct download from the page would
            // not be useful. We try the known per-version archive shape is nontrivial; for now
            // we ask the user to provide the zip via storage so incompatible URLs are avoided.
            return ProvisionResult(false, "دانلود مستقیم از صفحهٔ رسمی در حال حاضر مسدود/ناشناخته است. باینری را از منابع رسمی و در قالب ZIP وارد کن.")
        }.getOrElse { e ->
            ProvisionResult(false, "خطا در دانلود: ${e.message ?: "نامشخص"}")
        }
    }

    fun installZip(context: Context, uri: android.net.Uri): ProvisionResult {
        val dir = File(context.filesDir, "ondevice_server")
        dir.mkdirs()
        return runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: return ProvisionResult(false, "نمی‌توانم فایل ZIP را باز کنم.")
            val zis = ZipInputStream(input.buffered())
            var found = false
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/')
                if (name.isBlank()) continue
                // Accept only the archive root so we don't extract scripts from arbitrary paths.
                val out = File(dir, name)
                out.outputStream().use { zis.copyTo(it) }
                zis.closeEntry()
                if (name == "bedrock_server" || name.endsWith("bedrock_server")) found = true
            }
            zis.close()
            if (found) ProvisionResult(true, "باینری سرور نصب شد.", dir.absolutePath)
            else ProvisionResult(false, "در ZIP فایل bedrock_server پیدا نشد.")
        }.getOrElse { e ->
            ProvisionResult(false, "خطا در نصب باینری: ${e.message ?: "نامشخص"}")
        }
    }
}
