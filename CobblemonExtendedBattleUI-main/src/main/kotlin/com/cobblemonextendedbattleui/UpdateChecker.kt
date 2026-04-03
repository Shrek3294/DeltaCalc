package com.cobblemonextendedbattleui

import com.google.gson.Gson
import com.google.gson.JsonArray
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.toast.SystemToast
import net.minecraft.text.Text
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

object UpdateChecker {
    private const val MODRINTH_PROJECT_SLUG = "cobblemon-extended-battle-ui"
    private const val MODRINTH_API = "https://api.modrinth.com/v2"

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val gson = Gson()

    fun checkForUpdates() {
        CompletableFuture.runAsync {
            try {
                val currentVersion = getCurrentVersion()
                val minecraftVersion = getMinecraftVersion()

                CobblemonExtendedBattleUI.LOGGER.info("Checking for updates (current: $currentVersion, MC: $minecraftVersion)")

                val latestVersion = fetchLatestVersion(minecraftVersion)

                if (latestVersion != null && isNewerVersion(latestVersion, currentVersion)) {
                    CobblemonExtendedBattleUI.LOGGER.info("Update available: $latestVersion")
                    showUpdateToast(latestVersion)
                } else {
                    CobblemonExtendedBattleUI.LOGGER.info("No update available")
                }
            } catch (e: Exception) {
                CobblemonExtendedBattleUI.LOGGER.warn("Failed to check for updates: ${e.message}")
            }
        }
    }

    private fun getCurrentVersion(): String {
        return FabricLoader.getInstance()
            .getModContainer("cobblemonextendedbattleui")
            .map { it.metadata.version.friendlyString }
            .orElse("0.0.0")
    }

    private fun getMinecraftVersion(): String {
        return FabricLoader.getInstance()
            .getModContainer("minecraft")
            .map { it.metadata.version.friendlyString }
            .orElse("1.21.1")
    }

    private fun fetchLatestVersion(minecraftVersion: String): String? {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$MODRINTH_API/project/$MODRINTH_PROJECT_SLUG/version"))
            .header("User-Agent", "CobblemonExtendedBattleUI/$currentVersionForUserAgent")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            CobblemonExtendedBattleUI.LOGGER.warn("Modrinth API returned ${response.statusCode()}")
            return null
        }

        val versions = gson.fromJson(response.body(), JsonArray::class.java)

        // Find latest version compatible with current MC version
        for (element in versions) {
            val version = element.asJsonObject
            val gameVersions = version.getAsJsonArray("game_versions")

            val isCompatible = gameVersions.any { it.asString == minecraftVersion }
            if (isCompatible) {
                return version.get("version_number").asString
            }
        }

        return null
    }

    private val currentVersionForUserAgent: String
        get() = getCurrentVersion()

    private fun isNewerVersion(latest: String, current: String): Boolean {
        // Simple semver comparison (handles x.y.z format)
        val latestParts = latest.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun showUpdateToast(newVersion: String) {
        // Schedule on main thread
        MinecraftClient.getInstance().execute {
            val client = MinecraftClient.getInstance()
            client.toastManager.add(
                SystemToast.create(
                    client,
                    SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.literal("Extended Battle UI Update"),
                    Text.literal("Version $newVersion is available!")
                )
            )
        }
    }
}
