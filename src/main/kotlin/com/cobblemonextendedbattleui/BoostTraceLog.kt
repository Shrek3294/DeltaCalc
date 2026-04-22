package com.cobblemonextendedbattleui

import net.minecraft.client.MinecraftClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object BoostTraceLog {
    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val writeLock = Any()

    private fun logPath(): Path {
        val gameDir = MinecraftClient.getInstance().runDirectory.toPath()
        return gameDir.resolve("logs").resolve("ebu-boost-trace.log")
    }

    fun append(message: String) {
        val line = "[${LocalDateTime.now().format(timestampFormatter)}] $message${System.lineSeparator()}"
        synchronized(writeLock) {
            try {
                val path = logPath()
                Files.createDirectories(path.parent)
                Files.writeString(
                    path,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                )
            } catch (ignored: Exception) {
                // Keep tracing best-effort. Main logging already receives the same event.
            }
        }
    }
}
