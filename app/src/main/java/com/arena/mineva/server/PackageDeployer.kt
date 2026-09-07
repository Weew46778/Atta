package com.arena.mineva.server

import android.content.Context
import java.io.File

/**
 * Deploys a single catalog package to the target server.
 *
 * Flow:
 *  1. Real network link check (HTTP status + content type).
 *  2. If the URL is a page rather than a direct file, refuse auto-deploy.
 *  3. Download to the app's cache.
 *  4. For a VPS: SFTP upload to the right folder and restart the systemd service.
 *  5. For local: keep the file in app storage (runtime still needed later).
 */
class PackageDeployer {

    data class DeployResult(
        val success: Boolean,
        val output: String,
        val stage: String,
        val localPath: String = "",
        val error: String? = null
    )

    fun deploy(
        context: Context,
        config: ServerConfig,
        item: CatalogItem,
        sshPassword: String = config.sshPassword,
        keyPassphrase: String = config.sshKeyPassphrase,
        onProgress: (String) -> Unit = {}
    ): DeployResult {
        if (!item.hasDirectDownload()) {
            return DeployResult(false, "این آیتم لینک مستقیم ندارد و فقط مرجع است.", "no_url")
        }

        return runCatching {
            onProgress("چک لینک: ${item.name}")
            val health = PackageLinkValidator.check(item.downloadUrl)
            if (!health.ok || !health.isDirectDownload) {
                return DeployResult(
                    false,
                    "لینک سالم نیست یا صفحه مرجع است: ${health.detail}",
                    "invalid_link"
                )
            }

            onProgress("دانلود: ${item.name}")
            val fileName = PackageLinkValidator.fileNameFromUrl(item.downloadUrl, "${item.id}.jar")
            val packageDir = File(context.cacheDir, "mineava_packages").apply { mkdirs() }
            val localFile = File(packageDir, fileName)
            PackageLinkValidator.download(item.downloadUrl, localFile) { read, total ->
                onProgress("دانلود ${item.name}: $read/$total")
            }

            val folder = remoteFolder(item.category)
            if (config.target == ServerTarget.VPS) {
                onProgress("اتصال به سرور: ${config.host}")
                val ssh = SshClient()
                val session = ssh.connect(
                    host = config.host,
                    user = config.user,
                    password = sshPassword.ifBlank { null },
                    keyPath = config.sshKeyPath.ifBlank { null },
                    keyPassphrase = keyPassphrase.ifBlank { null },
                    port = config.sshPort
                )
                val remoteDir = "~/minecraft-server/$folder"
                val remotePath = "$remoteDir/$fileName"
                ssh.exec(session, "mkdir -p $remoteDir")
                onProgress("آپلود به $remoteDir")
                ssh.putBytes(session, localFile.readBytes(), remotePath)
                onProgress("ریاستارت سرویس سرور")
                val restartCmd = if (sshPassword.isNotBlank()) {
                    val esc = sshPassword.replace("'", "'\\''")
                    "printf '%s\\n' '$esc' | sudo -S -p '' systemctl restart MineAva-server 2>&1 || true"
                } else {
                    "sudo -n -p '' systemctl restart MineAva-server 2>&1 || true"
                }
                val restart = ssh.exec(session, restartCmd)
                session.disconnect()

                DeployResult(
                    success = true,
                    output = "نصب شد: $fileName در $remoteDir\n${restart.output}",
                    stage = "deployed",
                    localPath = localFile.absolutePath
                )
            } else {
                val localTarget = File(context.filesDir, "mineava_packages/$folder/${fileName}")
                localTarget.parentFile?.mkdirs()
                localFile.copyTo(localTarget, overwrite = true)
                DeployResult(
                    success = true,
                    output = "دانلود و ذخیره محلی: ${localTarget.absolutePath}",
                    stage = "local",
                    localPath = localTarget.absolutePath
                )
            }
        }.getOrElse { e ->
            DeployResult(false, "خطا: ${e.message ?: "نامشخص"}", "failed", error = e.message)
        }
    }

    fun remove(
        config: ServerConfig,
        item: CatalogItem,
        sshPassword: String = config.sshPassword,
        keyPassphrase: String = config.sshKeyPassphrase,
        onProgress: (String) -> Unit = {}
    ): DeployResult {
        val folder = remoteFolder(item.category)
        val fileName = if (item.hasDirectDownload()) {
            PackageLinkValidator.fileNameFromUrl(item.downloadUrl, "${item.id}.jar")
        } else {
            "${item.id}.jar"
        }
        return runCatching {
            if (config.target == ServerTarget.VPS) {
                onProgress("اتصال به سرور برای حذف ${item.name}")
                val ssh = SshClient()
                val session = ssh.connect(
                    host = config.host,
                    user = config.user,
                    password = sshPassword.ifBlank { null },
                    keyPath = config.sshKeyPath.ifBlank { null },
                    keyPassphrase = keyPassphrase.ifBlank { null },
                    port = config.sshPort
                )
                val remotePath = "~/minecraft-server/$folder/$fileName"
                ssh.exec(session, "rm -f $remotePath")
                val restartCmd = if (sshPassword.isNotBlank()) {
                    val esc = sshPassword.replace("'", "'\\''")
                    "printf '%s\\n' '$esc' | sudo -S -p '' systemctl restart MineAva-server 2>&1 || true"
                } else {
                    "sudo -n -p '' systemctl restart MineAva-server 2>&1 || true"
                }
                val restart = ssh.exec(session, restartCmd)
                session.disconnect()
                DeployResult(true, "حذف شد: $remotePath\n${restart.output}", "removed")
            } else {
                DeployResult(true, "حذف علامت محلی.", "removed")
            }
        }.getOrElse { e ->
            DeployResult(false, "خطا: ${e.message ?: "نامشخص"}", "failed", error = e.message)
        }
    }

    private fun remoteFolder(category: String): String = when (category.lowercase()) {
        "plugin" -> "plugins"
        "mod" -> "mods"
        "modpack" -> "modpacks"
        "shader" -> "shaderpacks"
        else -> "resourcepacks"
    }
}
