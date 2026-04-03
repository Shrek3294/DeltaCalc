package com.cobblemonextendedbattleui.pokemon.render

import com.cobblemon.mod.common.api.pokemon.status.Status
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemonextendedbattleui.CobblemonExtendedBattleUI
import com.cobblemonextendedbattleui.UIUtils
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import org.joml.Quaternionf
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles 3D Pokemon model rendering with status tinting and pokeball fallback.
 */
object PokemonModelRenderer {

    // FloatingState cache for Pokemon model rendering (one per UUID)
    private val floatingStates = ConcurrentHashMap<UUID, FloatingState>()

    // Fallback pokeball settings
    private const val BALL_SIZE = 10

    // Pokeball colors
    private val COLOR_NORMAL_TOP = color(255, 80, 80)
    private val COLOR_NORMAL_BOTTOM = color(240, 240, 240)
    private val COLOR_NORMAL_BAND = color(40, 40, 40)
    private val COLOR_NORMAL_CENTER = color(255, 255, 255)

    private val COLOR_POISON = color(160, 90, 200)
    private val COLOR_BURN = color(255, 140, 50)
    private val COLOR_PARALYSIS = color(255, 220, 50)
    private val COLOR_FREEZE = color(100, 200, 255)
    private val COLOR_SLEEP = color(150, 150, 170)

    private val COLOR_KO_TOP = color(80, 80, 80)
    private val COLOR_KO_BOTTOM = color(60, 60, 60)
    private val COLOR_KO_BAND = color(40, 40, 40)
    private val COLOR_KO_CENTER = color(100, 100, 100)

    /**
     * RGBA color tint for model rendering.
     */
    private data class ColorTint(val r: Float, val g: Float, val b: Float, val a: Float)

    private data class Quad<T>(val first: T, val second: T, val third: T, val fourth: T)

    fun getOrCreateFloatingState(uuid: UUID): FloatingState {
        return floatingStates.computeIfAbsent(uuid) { FloatingState() }
    }

    fun clearFloatingStates() {
        floatingStates.clear()
    }

    /**
     * Get RGBA tint values for Pokemon model based on status/KO state.
     */
    private fun getModelTint(isKO: Boolean, status: Status?): ColorTint {
        if (isKO) return ColorTint(0.4f, 0.4f, 0.4f, 0.7f)
        return when (status) {
            Statuses.POISON, Statuses.POISON_BADLY -> ColorTint(0.7f, 0.4f, 0.9f, 1f)
            Statuses.BURN -> ColorTint(1f, 0.5f, 0.2f, 1f)
            Statuses.PARALYSIS -> ColorTint(1f, 0.9f, 0.3f, 1f)
            Statuses.FROZEN -> ColorTint(0.4f, 0.8f, 1f, 1f)
            Statuses.SLEEP -> ColorTint(0.6f, 0.6f, 0.7f, 1f)
            else -> ColorTint(1f, 1f, 1f, 1f)
        }
    }

    fun getStatusColor(status: Status): Int {
        return when (status) {
            Statuses.POISON, Statuses.POISON_BADLY -> COLOR_POISON
            Statuses.BURN -> COLOR_BURN
            Statuses.PARALYSIS -> COLOR_PARALYSIS
            Statuses.FROZEN -> COLOR_FREEZE
            Statuses.SLEEP -> COLOR_SLEEP
            else -> COLOR_NORMAL_TOP
        }
    }

    /**
     * Draw a Pokemon model at the specified position.
     * Falls back to pokeball rendering if model fails to load.
     */
    fun drawPokemonModel(
        context: DrawContext,
        x: Int,
        y: Int,
        modelSize: Int,
        renderablePokemon: RenderablePokemon?,
        speciesIdentifier: Identifier?,
        aspects: Set<String>,
        uuid: UUID,
        isKO: Boolean,
        status: Status?,
        isLeftSide: Boolean,
        applyOpacity: (Int) -> Int,
        teamIndicatorScale: Float
    ) {
        val matrixStack = context.matrices
        val state = getOrCreateFloatingState(uuid)

        if (renderablePokemon != null) {
            state.currentAspects = renderablePokemon.aspects
        } else if (aspects.isNotEmpty()) {
            state.currentAspects = aspects
        }

        val tint = getModelTint(isKO, status)

        val centerX = x + modelSize / 2.0
        val yRotation = if (isLeftSide) -35f else 35f
        val rotation = Quaternionf().rotationXYZ(
            Math.toRadians(13.0).toFloat(),
            Math.toRadians(yRotation.toDouble()).toFloat(),
            0f
        )

        val scale = modelSize / 3.0f

        matrixStack.push()
        try {
            val renderY = y + modelSize * 0.1
            matrixStack.translate(centerX, renderY, 0.0)

            if (renderablePokemon != null) {
                drawProfilePokemon(
                    renderablePokemon = renderablePokemon,
                    matrixStack = matrixStack,
                    rotation = rotation,
                    poseType = PoseType.PORTRAIT,
                    state = state,
                    partialTicks = 0f,
                    scale = scale,
                    r = tint.r, g = tint.g, b = tint.b, a = tint.a
                )
            } else if (speciesIdentifier != null) {
                drawProfilePokemon(
                    species = speciesIdentifier,
                    matrixStack = matrixStack,
                    rotation = rotation,
                    poseType = PoseType.PORTRAIT,
                    state = state,
                    partialTicks = 0f,
                    scale = scale,
                    r = tint.r, g = tint.g, b = tint.b, a = tint.a
                )
            } else {
                matrixStack.pop()
                drawPokeballFallback(context, x, y, modelSize, isKO, status, applyOpacity, teamIndicatorScale)
                return
            }
        } catch (e: Exception) {
            CobblemonExtendedBattleUI.LOGGER.debug("Failed to render Pokemon model: ${e.message}")
            matrixStack.pop()
            drawPokeballFallback(context, x, y, modelSize, isKO, status, applyOpacity, teamIndicatorScale)
            return
        }
        matrixStack.pop()
    }

    /**
     * Draw a pokeball as fallback when model rendering fails.
     */
    private fun drawPokeballFallback(
        context: DrawContext,
        x: Int,
        y: Int,
        modelSize: Int,
        isKO: Boolean,
        status: Status?,
        applyOpacity: (Int) -> Int,
        teamIndicatorScale: Float
    ) {
        val scaledBallSize = (BALL_SIZE * teamIndicatorScale).toInt()
        val offsetX = x + (modelSize - scaledBallSize) / 2
        val offsetY = y + (modelSize - scaledBallSize) / 2

        val colors = when {
            isKO -> Quad(COLOR_KO_TOP, COLOR_KO_BOTTOM, COLOR_KO_BAND, COLOR_KO_CENTER)
            status != null -> {
                val statusColor = getStatusColor(status)
                Quad(statusColor, COLOR_NORMAL_BOTTOM, COLOR_NORMAL_BAND, COLOR_NORMAL_CENTER)
            }
            else -> Quad(COLOR_NORMAL_TOP, COLOR_NORMAL_BOTTOM, COLOR_NORMAL_BAND, COLOR_NORMAL_CENTER)
        }

        drawPokeball(context, offsetX, offsetY, scaledBallSize,
            applyOpacity(colors.first), applyOpacity(colors.second),
            applyOpacity(colors.third), applyOpacity(colors.fourth))
    }

    private fun drawPokeball(
        context: DrawContext,
        x: Int,
        y: Int,
        size: Int,
        topColor: Int,
        bottomColor: Int,
        bandColor: Int,
        centerColor: Int
    ) {
        val halfSize = size / 2
        val centerSize = (size * 0.4).toInt()
        val centerOffset = (size - centerSize) / 2

        context.fill(x + 1, y, x + size - 1, y + halfSize, topColor)
        context.fill(x, y + 1, x + size, y + halfSize, topColor)

        context.fill(x + 1, y + halfSize, x + size - 1, y + size, bottomColor)
        context.fill(x, y + halfSize, x + size, y + size - 1, bottomColor)

        context.fill(x, y + halfSize - 1, x + size, y + halfSize + 1, bandColor)

        context.fill(x + centerOffset, y + centerOffset, x + centerOffset + centerSize, y + centerOffset + centerSize, centerColor)
        context.fill(x + centerOffset, y + centerOffset, x + centerOffset + centerSize, y + centerOffset + 1, bandColor)
        context.fill(x + centerOffset, y + centerOffset + centerSize - 1, x + centerOffset + centerSize, y + centerOffset + centerSize, bandColor)
        context.fill(x + centerOffset, y + centerOffset, x + centerOffset + 1, y + centerOffset + centerSize, bandColor)
        context.fill(x + centerOffset + centerSize - 1, y + centerOffset, x + centerOffset + centerSize, y + centerOffset + centerSize, bandColor)
    }

    private fun color(r: Int, g: Int, b: Int, a: Int = 255): Int = UIUtils.color(r, g, b, a)
}
