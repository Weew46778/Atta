package com.arena.mineva.server

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Imports the two parts a real on-device Java server needs:
 *
 *  1. A portable JRE for Android/ARM (ZIP) — e.g. a Termux/Android-JVM runtime build.
 *  2. The actual server JAR (`.jar` directly, or a ZIP containing one).
 *
 * Nothing is auto-downloaded here because there is no single universal, license-safe
 * "run-on-Android" JRE that we can guarantee; the app accepts a user/script-supplied ZIP.
 */
object OnDeviceJavaServerProvisioner {

    data class ImportResult(val success: Boolean, val detail: String, val path: String = "")

    /** Extract a portable JRE ZIP into filesDir/ondevice_java_server/jre. */
    fun installJreZip(context: Context, uri: android.net.Uri): ImportResult {
        val target = File(context.filesDir, "ondevice_java_server/jre")
        target.deleteRecursively()
        target.mkdirs()
        return runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: return ImportResult(false, "نمی‌توانم فایل JRE ZIP را باز کنم.")
            val zis = ZipInputStream(input.buffered())
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name
                if (name.isBlank() || name.contains("..")) {
                    zis.closeEntry()
                    continue
                }
                // Strip a single top-level directory, e.g. "openjdk-17/bin/java" -> "bin/java".
                val parts = name.split('/').filter { it.isNotBlank() }
                if (parts.isEmpty()) {
                    zis.closeEntry()
                    continue
                }
                val rel = if (parts.size > 1) parts.drop(1).joinToString("/") else parts.last()
                if (rel.isBlank()) {
                    zis.closeEntry()
                    continue
                }
                val dest = File(target, rel)
                if (entry.isDirectory) {
                    dest.mkdirs()
                } else {
                    dest.parentFile?.mkdirs()
                    FileOutputStream(dest).use { zis.copyTo(it) }
                }
                zis.closeEntry()
            }
            zis.close()

            val java = File(target, "bin/java")
            if (!java.exists()) {
                target.deleteRecursively()
                return ImportResult(
                    false,
                    "در ZIP جای JRE فایل bin/java پیدا نشد. مطمئن شو بستهٔ JRE مخصوص ARM/Android است."
                )
            }
            java.setExecutable(true, true)
            ImportResult(true, "JRE موبایل نصب شد (${target.absolutePath}).", target.absolutePath)
        }.getOrElse { e ->
            ImportResult(false, "خطا در نصب JRE: ${e.message ?: "نامشخص"}")
        }
    }

    /** Import a server JAR (or a ZIP containing one) into the on-device Java server directory. */
    fun installServerJar(context: Context, uri: android.net.Uri): ImportResult {
        val target = File(context.filesDir, "ondevice_java_server")
        target.mkdirs()
        return runCatching {
            val displayName = queryDisplayName(context, uri)
            val lower = (displayName ?: uri.lastPathSegment ?: "").lowercase()
            val out = File(target, if (lower.endsWith(".zip")) "server.jar" else (displayName?.substringAfterLast('/') ?: "server.jar"))
            val input = context.contentResolver.openInputStream(uri)
                ?: return ImportResult(false, "نمی‌توانم فایل JAR را باز کنم.")

            if (lower.endsWith(".zip")) {
                // ZIP: look for any *.jar and extract the biggest/first one as server.jar.
                val zis = ZipInputStream(input.buffered())
                var foundJar: String? = null
                val tmp = File(target, "server.jar.tmp")
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    if (entry.isDirectory || !name.endsWith(".jar")) {
                        zis.closeEntry()
                        continue
                    }
                    if (foundJar == null || name.contains("server", true) || name.contains("paper", true)) {
                        FileOutputStream(tmp).use { zis.copyTo(it) }
                        foundJar = name
                    }
                    zis.closeEntry()
                }
                zis.close()
                if (foundJar == null) {
                    tmp.delete()
                    return ImportResult(false, "در ZIP هیچ فایل JAR پیدا نشد.")
                }
                tmp.renameTo(File(target, "server.jar"))
            } else {
                FileOutputStream(out).use { input.copyTo(it) }
                if (!out.name.endsWith(".jar")) {
                    val renamed = File(target, "server.jar")
                    out.renameTo(renamed)
                }
            }
            ImportResult(true, "فایل JAR سرور نصب شد.", File(target, "server.jar").absolutePath)
        }.getOrElse { e ->
            ImportResult(false, "خطا در نصب JAR: ${e.message ?: "نامشخص"}")
        }
    }

    private fun queryDisplayName(context: Context, uri: android.net.Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }
}
