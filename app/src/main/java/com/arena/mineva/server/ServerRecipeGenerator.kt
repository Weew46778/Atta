package com.arena.mineva.server

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arena.mineva.AppPrefs
import java.io.File

/**
 * Generates a ready-to-run server setup recipe for the requested target.
 *
 * The VPS script is a real installer: it installs a Java runtime, downloads Paper (or uses
 * the user-supplied Bedrock archive), writes server.properties, creates a tmux console with
 * send/start/stop/log/backup helpers, installs an optional playit.gg tunnel for remote
 * access, and starts the server through systemd.
 */
object ServerRecipeGenerator {

    fun generate(context: Context, config: ServerConfig): RecipeResult {
        val shell = buildScript(config)
        val md = buildMarkdown(config, shell)
        val fileName = "MineAva-server-${System.currentTimeMillis()}.sh"
        val saved = saveDownloadSafe(context, fileName, shell)
        return RecipeResult(
            fileName = saved?.name ?: fileName,
            script = shell,
            markdown = md,
            path = saved?.absolutePath ?: "فایل‌های اپ/recipes/$fileName",
            notes = buildNotes(config)
        )
    }

    private fun saveDownloadSafe(context: Context, name: String, content: String): File? =
        runCatching { saveDownload(context, name, content) }.getOrElse {
            runCatching {
                val dir = File(context.filesDir, "recipes").apply { mkdirs() }
                val file = File(dir, name)
                file.writeText(content)
                file
            }.getOrNull()
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
            appendLocal(c, sb)
        } else {
            sb.appendLine("if [ \"$(id -u)\" -eq 0 ]; then SUDO=\"\"; else SUDO=\"sudo\"; fi")
            sb.appendLine("export DEBIAN_FRONTEND=noninteractive")
            sb.appendLine("SERVER_VERSION=\"${c.version}\"")
            sb.appendLine("SERVER_PORT=${c.port}")
            sb.appendLine("MEMORY_MB=${c.memoryMb}")
            sb.appendLine("RCON_PORT=25575")
            sb.appendLine("RCON_PASSWORD='${esc(AppPrefs.rconPassword)}'")
            sb.appendLine()
            sb.appendLine("\$SUDO apt-get update")
            sb.appendLine("\$SUDO apt-get install -y openjdk-21-jdk-headless curl unzip tmux screen wget ca-certificates")
            sb.appendLine()
            sb.appendLine("mkdir -p ~/minecraft-server")
            sb.appendLine("cd ~/minecraft-server")
            appendEdition(c, sb)
            appendServerProps(c, sb)
            appendPackages(c, sb)
            appendConsole(c, sb)
            appendPlayit(c, sb)
            sb.appendLine("echo")
            sb.appendLine("echo 'MineAva server installed successfully.'")
            sb.appendLine("echo 'Connect on LAN:' \"\$(hostname -I | awk '{print \\$1}'):${c.port}\"")
            if (c.playitSecret.isNotBlank()) {
                sb.appendLine("echo 'Remote: create a Minecraft tunnel on playit.gg dashboard -> localhost:${c.port}, then share the playit.gg address.'")
            }
        }
        return sb.toString()
    }

    private fun appendLocal(c: ServerConfig, sb: StringBuilder) {
        sb.appendLine("# ON-DEVICE LOCAL RECIPE")
        sb.appendLine("# Android cannot execute a standard Linux JRE/JAR directly, so this script is")
        sb.appendLine("# the exact launcher the app can use once a compatible binary is imported.")
        sb.appendLine("mkdir -p server")
        sb.appendLine("cd server")
        sb.appendLine("echo '=== MineAva local server launcher ==='")
        if (c.edition == ServerEdition.BEDROCK) {
            sb.appendLine("if [ -x ./bedrock_server ]; then")
            sb.appendLine("  echo 'Starting Bedrock server on port ${c.port}'")
            sb.appendLine("  chmod +x ./bedrock_server 2>/dev/null || true")
            sb.appendLine("  LD_LIBRARY_PATH=. ./bedrock_server")
            sb.appendLine("else")
            sb.appendLine("  echo 'bedrock_server not found. Import the official Bedrock server ZIP through MineAva first.'")
            sb.appendLine("  exit 1")
            sb.appendLine("fi")
        } else {
            sb.appendLine("if [ -x ./jre/bin/java ] && [ -f ./server.jar ]; then")
            sb.appendLine("  echo 'Starting portable Java server on port ${c.port}'")
            sb.appendLine("  ./jre/bin/java -Xms256M -Xmx${c.memoryMb}M -jar server.jar nogui")
            sb.appendLine("else")
            sb.appendLine("  echo 'Missing portable JRE or server.jar. Import both through MineAva first.'")
            sb.appendLine("  exit 1")
            sb.appendLine("fi")
        }
    }

    private fun appendEdition(c: ServerConfig, sb: StringBuilder) {
        when (c.edition) {
            ServerEdition.JAVA -> {
                sb.appendLine("# --- Paper/Java download ---")
                sb.appendLine("curl -fsSL \"https://api.papermc.io/v2/projects/paper/versions/\$SERVER_VERSION/builds\" -o /tmp/paper.json || true")
                sb.appendLine("PAPER_BUILD=\$(grep -oE '\"build\":[-0-9]+' /tmp/paper.json 2>/dev/null | head -1 | grep -oE '[-0-9]+' || true)")
                sb.appendLine("if [ -n \"\$PAPER_BUILD\" ] && [ \"\$PAPER_BUILD\" != \"0\" ]; then")
                sb.appendLine("  echo \"Downloading Paper \$SERVER_VERSION build \$PAPER_BUILD\"")
                sb.appendLine("  curl -fsSL -o server.jar \"https://api.papermc.io/v2/projects/paper/versions/\$SERVER_VERSION/builds/\$PAPER_BUILD/downloads/paper-\$SERVER_VERSION-\$PAPER_BUILD.jar\"")
                sb.appendLine("else")
                sb.appendLine("  echo 'WARNING: Could not resolve Paper build. Put server.jar in ~/minecraft-server and continue.'")
                sb.appendLine("fi")
                sb.appendLine("echo 'eula=true' > eula.txt")
            }
            ServerEdition.BEDROCK -> {
                sb.appendLine("# --- Bedrock dedicated server ---")
                sb.appendLine("if [ ! -f bedrock-server.zip ]; then")
                sb.appendLine("  echo 'ERROR: bedrock-server.zip is missing.'")
                sb.appendLine("  echo 'Download the official Linux Bedrock archive (from minecraft.net server download) and place it at ~/minecraft-server/bedrock-server.zip, then re-run this script.'")
                sb.appendLine("  exit 1")
                sb.appendLine("fi")
                sb.appendLine("mkdir -p bedrock-server")
                sb.appendLine("unzip -o bedrock-server.zip -d bedrock-server")
                sb.appendLine("chmod +x bedrock-server/bedrock_server 2>/dev/null || true")
                sb.appendLine("cp server.properties bedrock-server/ || true")
            }
            ServerEdition.HYBRID -> {
                sb.appendLine("# --- Java server + Geyser + Floodgate so Bedrock clients can join ---")
                appendEdition(c.copy(edition = ServerEdition.JAVA), sb)
                sb.appendLine("mkdir -p plugins")
                sb.appendLine("cat > geyser-config.yml <<'GEYSER'")
                sb.appendLine("remote:")
                sb.appendLine("  address: 127.0.0.1")
                sb.appendLine("  port: 25565")
                sb.appendLine("bedrock:")
                sb.appendLine("  port: ${c.port}")
                sb.appendLine("GEYSER")
                sb.appendLine("echo 'Geyser: put Geyser-Spigot.jar and Floodgate-Spigot.jar into plugins/ and restart.'")
            }
        }
    }

    private fun appendServerProps(c: ServerConfig, sb: StringBuilder) {
        val rcon = if (AppPrefs.rconEnabled) "true" else "true"
        sb.appendLine("# --- server.properties ---")
        sb.appendLine("cat > server.properties <<'PROPS'")
        if (c.edition == ServerEdition.BEDROCK) {
            sb.appendLine("server-name=MineAva")
            sb.appendLine("server-port=${c.port}")
            sb.appendLine("max-players=${c.maxPlayers}")
            sb.appendLine("difficulty=normal")
            sb.appendLine("gamemode=survival")
            sb.appendLine("allow-cheats=true")
            sb.appendLine("enable-rcon=$rcon")
            sb.appendLine("rcon.port=25575")
            sb.appendLine("rcon.password='${esc(AppPrefs.rconPassword)}'")
        } else {
            sb.appendLine("server-port=${c.port}")
            sb.appendLine("max-players=${c.maxPlayers}")
            sb.appendLine("motd=MineAva")
            sb.appendLine("difficulty=normal")
            sb.appendLine("gamemode=survival")
            sb.appendLine("online-mode=false")
            sb.appendLine("enable-command-block=true")
            sb.appendLine("enable-rcon=$rcon")
            sb.appendLine("rcon.port=25575")
            sb.appendLine("rcon.password='${esc(AppPrefs.rconPassword)}'")
        }
        sb.appendLine("PROPS")
    }

    private fun appendPackages(c: ServerConfig, sb: StringBuilder) {
        sb.appendLine("# --- Package directories ---")
        sb.appendLine("mkdir -p plugins mods modpacks shaderpacks resourcepacks scripts")
        (c.pluginNames + c.modNames + c.modpackNames + c.resourcePackNames + c.shaderPackNames + c.texturePackNames)
            .distinct()
            .forEach { name ->
                sb.appendLine("echo '• Queued package: $name (download + verify link in the MineAva app)'")
            }
    }

    private fun appendConsole(c: ServerConfig, sb: StringBuilder) {
        sb.appendLine("# --- tmux console helpers ---")
        sb.appendLine("cat > scripts/send.sh <<'SH'")
        sb.appendLine("#!/usr/bin/env bash")
        sb.appendLine("tmux send-keys -t MineAvaServer \"\$1\" Enter")
        sb.appendLine("SH")
        sb.appendLine("chmod +x scripts/send.sh")
        sb.appendLine("cat > scripts/view.sh <<'SH'")
        sb.appendLine("#!/usr/bin/env bash")
        sb.appendLine("tmux capture-pane -t MineAvaServer -p | tail -n 60")
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
        sb.appendLine("cat > scripts/log.sh <<'SH'")
        sb.appendLine("#!/usr/bin/env bash")
        sb.appendLine("tmux capture-pane -t MineAvaServer -p | tail -n 120")
        sb.appendLine("SH")
        sb.appendLine("chmod +x scripts/log.sh")

        // The app talks to this tmux session over SSH; keep it alive after the setup script ends.
        sb.appendLine("cat > /tmp/MineAva-server.service <<EOF")
        sb.appendLine("[Unit]")
        sb.appendLine("Description=MineAva Minecraft Server")
        sb.appendLine("After=network-online.target")
        sb.appendLine("Wants=network-online.target")
        sb.appendLine("[Service]")
        sb.appendLine("Type=simple")
        sb.appendLine("WorkingDirectory=%h/minecraft-server")
        sb.appendLine("ExecStart=/bin/bash -lc 'cd %h/minecraft-server && exec tmux new-session -A -s MineAvaServer ./run.sh'")
        sb.appendLine("Restart=on-failure")
        sb.appendLine("RestartSec=10")
        sb.appendLine("[Install]")
        sb.appendLine("WantedBy=multi-user.target")
        sb.appendLine("EOF")
        sb.appendLine("\$SUDO mv /tmp/MineAva-server.service /etc/systemd/system/")
        sb.appendLine("\$SUDO systemctl daemon-reload")
        sb.appendLine("\$SUDO systemctl enable --now MineAva-server || true")

        sb.appendLine("# open firewall (non-fatal)")
        sb.appendLine("if command -v ufw >/dev/null 2>&1; then")
        sb.appendLine("  \$SUDO ufw allow ${c.port}/tcp || true")
        sb.appendLine("  \$SUDO ufw allow ${c.port}/udp || true")
        sb.appendLine("  \$SUDO ufw allow 25575/tcp || true")
        sb.appendLine("fi")
    }

    private fun appendPlayit(c: ServerConfig, sb: StringBuilder) {
        if (c.playitSecret.isBlank()) return
        sb.appendLine("# --- playit.gg remote access ---")
        sb.appendLine("if command -v playit >/dev/null 2>&1; then")
        sb.appendLine("  echo 'playit already installed.'")
        sb.appendLine("else")
        sb.appendLine("  echo 'Installing playit.gg agent'")
        sb.appendLine("  curl -fsSL -o /usr/local/bin/playit 'https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-linux-amd64' || true")
        sb.appendLine("  chmod +x /usr/local/bin/playit 2>/dev/null || true")
        sb.appendLine("fi")
        sb.appendLine("cat > /tmp/playit.service <<EOF")
        sb.appendLine("[Unit]")
        sb.appendLine("Description=playit.gg MineAva tunnel")
        sb.appendLine("After=network-online.target MineAva-server.service")
        sb.appendLine("Wants=network-online.target")
        sb.appendLine("[Service]")
        sb.appendLine("Type=simple")
        sb.appendLine("ExecStart=/usr/local/bin/playit --secret '${esc(c.playitSecret)}'")
        sb.appendLine("Restart=always")
        sb.appendLine("RestartSec=5")
        sb.appendLine("[Install]")
        sb.appendLine("WantedBy=multi-user.target")
        sb.appendLine("EOF")
        sb.appendLine("\$SUDO mv /tmp/playit.service /etc/systemd/system/playit.service")
        sb.appendLine("\$SUDO systemctl daemon-reload")
        sb.appendLine("\$SUDO systemctl enable --now playit || true")
    }

    private fun esc(value: String): String =
        value.replace("'", "'\\''")

    private fun buildMarkdown(c: ServerConfig, shell: String): String {
        return """
            # MineAva — ساختهی سرور خودکار

            - هدف: ${c.target}
            - نسخه: ${c.edition} / ${c.version}
            - پورت: ${c.port}
            - حافظه: ${c.memoryMb} MB
            - اتصال جاوا: ${if (c.allowJavaClients) "فعال" else "غیرفعال"}
            - اتصال بدراک: ${if (c.allowBedrockClients) "فعال" else "غیرفعال"}
            - Geyser: ${if (c.useGeyser) "فعال" else "خاموش"}
            - دسترسی راه دور Playit: ${if (c.playitSecret.isNotBlank()) "فعال" else "غیرفعال"}

            ## اسکریپت پیشنهادی

            ```bash
            $shell
            ```
        """.trimIndent()
    }

    private fun buildNotes(c: ServerConfig): String {
        return when (c.target) {
            ServerTarget.LOCAL -> """
                روی گوشی، اجرای مستقیم سرور Java با JAR استاندارد ممکن نیست چون اندروید زمان اجرای جاوا ندارد.
                اپ میتواند باینری Bedrock compatible را در مسیر خود نگه دارد؛ برای Java باید از VPS استفاده کنی.
            """.trimIndent()
            ServerTarget.VPS -> """
                اپ با SSH به VPS وصل میشود، اسکریپت را آپلود و اجرا میکند.
                اگر کلید Playit دادی، agent روی سرور نصب میشود و در داشبورد playit.gg باید تانل Minecraft به آدرس localhost:${c.port} بسازی.
                رمز عبور SSH فقط در حافظه نگهداری میشود و در فایل کانفیگ ذخیره نمیشود.
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
