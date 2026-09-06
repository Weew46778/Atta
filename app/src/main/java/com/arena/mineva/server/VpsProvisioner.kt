package com.arena.mineva.server

import android.content.Context

/**
 * Real, best-effort VPS provisioner.
 *
 * Flow:
 *  1. SSH connect (password or private key).
 *  2. Detect UID (root or sudo).
 *  3. Upload a generated setup script through SFTP.
 *  4. Run it as root / via `sudo -S` (password supplied non-interactively).
 *  5. Return the live command output so the UI/voice can report the result.
 */
class VpsProvisioner {

    data class ProvisionResult(
        val success: Boolean,
        val output: String,
        val stage: String,
        val error: String? = null
    )

    fun provision(
        context: Context,
        config: ServerConfig,
        onProgress: (String) -> Unit = {}
    ): ProvisionResult {
        val ssh = SshClient()
        var session: com.jcraft.jsch.Session? = null
        return try {
            onProgress("اتصال به سرور...")
            session = ssh.connect(
                host = config.host,
                user = config.user,
                password = config.sshPassword.ifBlank { null },
                keyPath = config.sshKeyPath.ifBlank { null },
                keyPassphrase = config.sshKeyPassphrase.ifBlank { null },
                port = config.sshPort
            )
            onProgress("اتصال برقرار شد. آمادهسازی اسکریپت...")

            val uidResult = ssh.exec(session!!, "id -u")
            val isRoot = uidResult.output.trim() == "0"
            onProgress(if (isRoot) "کاربر root. اجرای مستقیم." else "اجرا با sudo (نیاز به رمز عبور).")

            val script = ServerRecipeGenerator.buildScript(config)
            val remoteScript = "/tmp/mineava-setup.sh"
            ssh.put(session, script, remoteScript)
            ssh.exec(session, "chmod +x $remoteScript")
            onProgress("اسکریپت آپلود شد. در حال ساخت و نصب...")

            val runCommand = if (isRoot) {
                "bash $remoteScript"
            } else if (config.sshPassword.isNotBlank()) {
                val esc = config.sshPassword.replace("'", "'\\''")
                "printf '%s\\n' '$esc' | sudo -S -p '' bash $remoteScript 2>&1"
            } else {
                "sudo -n -p '' bash $remoteScript 2>&1"
            }
            val result = ssh.exec(session, runCommand)

            val success = result.output.contains("server", ignoreCase = true) ||
                result.output.contains("MineAva", ignoreCase = true) ||
                result.output.contains("started", ignoreCase = true) ||
                result.output.contains("success", ignoreCase = true)
            onProgress(if (success) "ساخت سرور تمام شد." else "برخی مراحل نیاز به بررسی دارند.")

            ProvisionResult(
                success = success,
                output = result.output,
                stage = "deployed"
            )
        } catch (e: Exception) {
            onProgress("خطای SSH: ${e.message}")
            ProvisionResult(
                success = false,
                output = e.message ?: "خطا",
                stage = "failed",
                error = e.message
            )
        } finally {
            runCatching { session?.disconnect() }
        }
    }
}
