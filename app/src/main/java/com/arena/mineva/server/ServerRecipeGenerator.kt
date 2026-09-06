package com.arena.mineva.server

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Generates a ready-to-run server setup recipe for the requested target.
 *
 * For local targets it produces a placeholder/on-device recipe. For VPS targets it
 * produces a self-contained Bash script that the app can upload via SFTP and execute
 * over SSH. It also saves the script to Download/MineAva for manual use.
 */
object ServerRecipeGenerator {

    fun generate(context: Context, config: ServerConfig): RecipeResult {
        val shell = buildScript(config)
        val md = buildMarkdown(config, shell)
        val fileName = "MineAva-server-${System.currentTimeMillis()}.sh"
        val saved = saveDownload(context, fileName, shell)
        return RecipeResult(
            fileName = saved?.name ?: fileName,
            script = shell,
            markdown = md,
            path = saved?.absolutePath ?: "Download/MineAva/$fileName",
            notes = buildNotes(config)
        )
    }

    fun buildScript(c: ServerConfig): String {
        val sb = StringBuilder()
        sb.appendLine("#!/usr/bin/env bash")
        sb.appendLine("set -euo pipefail")
        sb.appendLine()
        sb.appendLine("# MineAva server recipe — generated automatically.")
        sb.appendLine("# Target: ${c.target} | Edition: ${c.edition} | Version: ${c.version}")
        sb.appendLine()
        if (c.target == ServerTarget.LOCAL) {
            sb.appendLine("# ON-DEVICE NOTE")
            sb.appendLine("# Android can run a Bedrock dedicated server more easily than Java.")
            sb.appendLine("# This recipe expects a Bedrock release binary under ./bedrock-server.")
            sb.appendLine("mkdir -p ./bedrock-server")
            sb.appendLine("cd ./bedrock-server")
            sb.appendLine("echo 'Place the Bedrock Server executable from the official archive here.'")
            sb.appendLine("chmod +x ./bedrock_server")
            sb.appendLine("LD_LIBRARY_PATH=. ./bedrock_server &")
            sb.appendLine("echo \"Bedrock server started on UDP ${c.port}\"")
        } else {
            sb.appendLine("if [ \"$(id -u)\" -eq 0 ]; then SUDO=\"\"; else SUDO=\"sudo\"; fi")
            sb.appendLine("export DEBIAN_FRONTEND=noninteractive")
            sb.appendLine("SERVER_VERSION=\"${c.version}\"")
            sb.appendLine("SERVER_PORT=${c.port}")
            sb.appendLine("MEMORY=${c.memoryMb}")
            sb.appendLine()
            sb.appendLine("\$SUDO apt-get update")
            sb.appendLine("\$SUDO apt-get install -y openjdk-21-jdk-headless curl unzip tmux screen wget")
            sb.appendLine()
            sb.appendLine("mkdir -p ~/minecraft-server")
            sb.appendLine("cd ~/minecraft-server")
            appendEdition(c, sb)
            appendPackages(c, sb)
            appendSystemd(c, sb)
        }
        return sb.toString()
    }

    private fun appendSystemd(c: ServerConfig, sb: StringBuilder) {
        sb.appendLine("# Persistent tmux session lets the app stream real server STDIN.")
        sb.appendLine("mkdir -p scripts")
        sb.appendLine("cat > scripts/send.sh <<'SH'")
        sb.appendLine("#!/usr/bin/env bash")
        sb.appendLine("tmux send-keys -t MineAvaServer \"\$1\" Enter")
        sb.appendLine("SH")
        sb.appendLine("chmod +x scripts/send.sh")
        sb.appendLine("cat > scripts/view.sh <<'SH'")
        sb.appendLine("#!/usr/bin/env bash")
        sb.appendLine("tmux capture-pane -t MineAvaServer -p | tail -n 50")
        sb.appendLine("SH")
        sb.appendLine("chmod +x scripts/view.sh")
        sb.appendLine("cat > scripts/start.sh <<'SH'")
        sb.appendLine("#!/usr/bin/env bash")
        sb.appendLine("cd \"\$(dirname \"\$0\")/..\"")
        sb.appendLine("tmux kill-session -t MineAvaServer 2>/dev/null || true")
        sb.appendLine("tmux new-session -d -s MineAvaServer './run.sh'")
        sb.appendLine("SH")
        sb.appendLine("chmod +x scripts/start.sh")
        sb.appendLine("cat > scripts/stop.sh <<'SH'")
        sb.appendLine("#!/usr/bin/env bash")
        sb.appendLine("tmux send-keys -t MineAvaServer 'stop' Enter 2>/dev/null || true")
        sb.appendLine("sleep 5")
        sb.appendLine("tmux kill-session -t MineAvaServer 2>/dev/null || true")
        sb.appendLine("SH")
        sb.appendLine("chmod +x scripts/stop.sh")

        sb.appendLine("# Persist a systemd unit so the server survives reboots.")
        sb.appendLine("# The service keeps the tmux session in the foreground, so the app can")
        sb.appendLine("# send commands with 'tmux send-keys' and read output with 'tmux capture-pane'.")
        sb.appendLine("cat > /tmp/MineAva-server.service <<EOF")
        sb.appendLine("[Unit]")
        sb.appendLine("Description=MineAva Minecraft Server")
        sb.appendLine("After=network-online.target")
        sb.appendLine("Wants=network-online.target")
        sb.appendLine("[Service]")
        sb.appendLine("Type=simple")
        sb.appendLine("WorkingDirectory=\$HOME/minecraft-server")
        sb.appendLine("ExecStart=/bin/bash -lc 'cd \$HOME/minecraft-server && exec tmux new-session -A -s MineAvaServer ./run.sh'")
        sb.appendLine("Restart=on-failure")
        sb.appendLine("RestartSec=10")
        sb.appendLine("[Install]")
        sb.appendLine("WantedBy=multi-user.target")
        sb.appendLine("EOF")
        sb.appendLine("\$SUDO mv /tmp/MineAva-server.service /etc/systemd/system/")
        sb.appendLine("\$SUDO systemctl daemon-reload")
        sb.appendLine("\$SUDO systemctl enable --now MineAva-server")
        sb.appendLine("echo 'MineAva server installed successfully.'")
    }

    private fun appendEdition(c: ServerConfig, sb: StringBuilder) {
        when {
            c.edition == ServerEdition.JAVA -> {
                sb.appendLine("# Fetch the latest Paper build for the requested version.")
                sb.appendLine("curl -sL \"https://api.papermc.io/v2/projects/paper/versions/\$SERVER_VERSION/builds\" \\")
                sb.appendLine("  | grep -oE '\"build\":[0-9]+' | head -1 | grep -oE '[0-9]+' > /tmp/paper_build")
                sb.appendLine("PAPER_BUILD=\$(cat /tmp/paper_build)")
                sb.appendLine("if [ -n \"\$PAPER_BUILD\" ]; then")
                sb.appendLine("  curl -sL -o server.jar \\")
                sb.appendLine("    \"https://api.papermc.io/v2/projects/paper/versions/\$SERVER_VERSION/builds/\$PAPER_BUILD/downloads/paper-\$SERVER_VERSION-\$PAPER_BUILD.jar\"")
                sb.appendLine("else")
                sb.appendLine("  echo 'WARNING: Could not find Paper build; place server.jar manually.'")
                sb.appendLine("fi")
                sb.appendLine("cat > run.sh <<RUNE")
                sb.appendLine("#!/usr/bin/env bash")
                sb.appendLine("cd \"\$(dirname \"\$0\")\"")
                sb.appendLine("java -Xms\${MEMORY}M -Xmx\${MEMORY}M -jar server.jar --nogui")
                sb.appendLine("RUNE")
                sb.appendLine("chmod +x run.sh")
                sb.appendLine("# Accept first EULA automatically.")
                sb.appendLine("echo 'eula=true' > eula.txt")
            }
            c.edition == ServerEdition.BEDROCK -> {
                sb.appendLine("curl -L -o bedrock-server.zip \\")
                sb.appendLine("  \"https://www.minecraft.net/en-us/download/server/bedrock\"")
                sb.appendLine("unzip -o bedrock-server.zip -d bedrock-server")
                sb.appendLine("chmod +x bedrock-server/bedrock_server")
                sb.appendLine("cat > run.sh <<RUNE")
                sb.appendLine("#!/usr/bin/env bash")
                sb.appendLine("cd \"\$(dirname \"\$0\")/bedrock-server\"")
                sb.appendLine("LD_LIBRARY_PATH=. ./bedrock_server")
                sb.appendLine("RUNE")
                sb.appendLine("chmod +x run.sh")
                sb.appendLine("echo 'Bedrock server recipe ready.'")
            }
            c.edition == ServerEdition.HYBRID -> {
                sb.appendLine("# Java server + Geyser + Floodgate let Bedrock clients join Java.")
                appendEdition(c.copy(edition = ServerEdition.JAVA), sb)
                sb.appendLine("# Copy Geyser-Spigot.jar and Floodgate-Spigot.jar into plugins/.")
                sb.appendLine("# Geyser listens for Bedrock on UDP ${c.port}. Keep the Java port at 25565.")
                sb.appendLine("mkdir -p plugins")
            }
        }
    }

    private fun appendPackages(c: ServerConfig, sb: StringBuilder) {
        sb.appendLine("mkdir -p plugins mods modpacks shaderpacks resourcepacks")
        (c.pluginNames + c.modNames + c.modpackNames + c.resourcePackNames + c.shaderPackNames + c.texturePackNames)
            .distinct()
            .forEach { name ->
                sb.appendLine("echo '\\u2022 Queued package: $name (download + verify link in the MineAva app)'")
            }
    }

    private fun buildMarkdown(c: ServerConfig, shell: String): String {
        return """
            # MineAva — ساخته‍ی سرور خودکار

            - هدف: ${c.target}
            - نسخه: ${c.edition} / ${c.version}
            - پورت: ${c.port}
            - حافظه: ${c.memoryMb} MB
            - اتصال جاوا: ${if (c.allowJavaClients) "فعال" else "غیرفعال"}
            - اتصال بدراک: ${if (c.allowBedrockClients) "فعال" else "غیرفعال"}
            - Geyser: ${if (c.useGeyser) "فعال" else "خاموش"}

            ## اسکریپت پیشنهادی

            ```bash
            $shell
            ```
        """.trimIndent()
    }

    private fun buildNotes(c: ServerConfig): String {
        return when (c.target) {
            ServerTarget.LOCAL -> """
                روی گوشی، سرور Bedrock بهصورت واقعی قابل اجرا است (در صورت داشتن باینری رسمی و منابع کافی).
                سرور Java روی اندروید به یک runtime جاوا نیاز دارد که در نسخه اولیه داخل اپ نیست.
                این بخش در حال حاضر «دستور پیشنهادی و چک منابع» تولید میکند و در نسخه بعدی به اجرای واقعی محلی متصل میشود.
            """.trimIndent()
            ServerTarget.VPS -> """
                اپ با SSH به VPS وصل میشود، اسکریپت را آپلود و با sudo اجرا میکند.
                برای ورود بدون رمز، کلید SSH در مسیر قابل خواندن دستگاه قرار بگیرد.
                رمز عبور فقط برای همین عملیات در حافظه نگهداری میشود و در فایل کانفیگ ذخیره نمیشود.
            """.trimIndent()
        }
    }

    private fun saveDownload(context: Context, name: String, content: String): File? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/x-shellscript")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MineAva")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            // We can't produce a File for a MediaStore uri. Return a pseudo file with the name.
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MineAva/$name")
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val file = File(dir, name)
            file.writeText(content)
            file
        }
    }
}

data class RecipeResult(
    val fileName: String,
    val script: String,
    val markdown: String,
    val path: String,
    val notes: String
)
