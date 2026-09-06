package com.arena.mineva.server

/**
 * Builds Minecraft commands for the target edition.
 *
 * Bedrock and Java have different command syntax. This builder keeps that difference in a
 * single place so the overlay panel can prepare commands that are correct for the server.
 *
 * Note: we deliberately do NOT claim to inject commands into a running Bedrock *client*.
 * That is not possible through any public/safe Android API. These commands are delivered to
 * the server console (via tmux) or copied to the clipboard for the user.
 */
object BedrockCommandBuilder {

    enum class Edition { JAVA, BEDROCK }

    fun giveNetheriteSword(
        edition: Edition,
        count: Int = 1,
        enchants: List<String> = listOf("sharpness", "unbreaking")
    ): String = when (edition) {
        Edition.JAVA -> {
            val tags = enchants.map { "{\"id\":\"$it\",\"lvl\":10}" }.joinToString(", ")
            "give @p netherite_sword $count 0 {\"Enchantments\":[$tags]}"
        }
        Edition.BEDROCK -> {
            val ench = enchants.map { "\"$it\":10" }.joinToString(",")
            "give @p netherite_sword $count 0 {\"ench\":[$ench]}"
        }
    }

    fun giveProtectedChestplate(edition: Edition): String = when (edition) {
        Edition.JAVA -> "give @p netherite_chestplate 1 0 {\"Enchantments\":[{\"id\":\"protection\",\"lvl\":10},{\"id\":\"unbreaking\",\"lvl\":10}]}"
        Edition.BEDROCK -> "give @p netherite_chestplate 1 0 {\"ench\":[{\"id\":\"protection\",\"lvl\":10},{\"id\":\"unbreaking\",\"lvl\":10}]}"
    }

    fun giveSpawnEgg(mobId: String, edition: Edition): String = when (edition) {
        Edition.JAVA -> "give @p minecraft:$mobId.spawn_egg 1"
        Edition.BEDROCK -> "give @p spawn_egg 1 0 {spawn_egg_type:\"$mobId\"}"
    }

    fun executeAsPlayer(command: String): String =
        "execute as @p run $command"

    fun bedrockSafe(command: String): String {
        // Bedrock command length limits (~256 chars) and no complex NBT on older versions.
        return command.take(220)
    }

    fun normalizeUserCommand(raw: String, edition: Edition): Pair<String, String?> {
        var cmd = raw.trim()
        var hint: String? = null
        if (!cmd.startsWith("/")) cmd = "/$cmd"
        if (edition == Edition.BEDROCK && cmd.length > 256) {
            hint = "دستور بدراک خیلی طولانی شد؛ به ${cmd.take(220).length} کاراکتر کوتاه شد."
            cmd = cmd.take(220)
        }
        return cmd to hint
    }
}
