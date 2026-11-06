package dev.jamie.uikit.ui

import dev.jamie.uikit.core.Clock
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * Frame clock for UI animation. [dt] is the clamped seconds since the previous UI frame; every widget
 * animation steps with it so motion is frame-rate independent.
 */
object Frame {
    var dt = 0f; private set
    private var last = Clock.now
    /** Global multiplier; 0 = reduce motion (everything snaps). */
    var motion = 1f

    /** Called by every renderer that animates (HUD layer, kit screens); only the first call in a frame advances time. */
    fun begin() {
        val now = Clock.now
        val elapsed = (now - last).toFloat()
        if (elapsed < 0.001f) return // same frame (the HUD drew first, now a screen): keep this frame's dt
        dt = min(elapsed, 0.1f)
        last = now
    }
}

/**
 * Critically-damped-ish spring. The workhorse of all kit motion: positions, sizes, opacities.
 * ```
 * val hover = Spring(0f)
 * hover.target = if (hovered) 1f else 0f
 * val t = hover.update()
 * ```
 */
class Spring(initial: Float, var stiffness: Float = 380f, var damping: Float = 0.86f) {
    var value = initial
    var target = initial
    private var velocity = 0f

    fun update(dt: Float = Frame.dt): Float {
        if (Frame.motion == 0f) {
            value = target; velocity = 0f; return value
        }
        // Sub-step for stability at low frame rates.
        var remaining = dt
        val c = 2f * damping * kotlin.math.sqrt(stiffness)
        while (remaining > 0f) {
            val h = min(remaining, 1f / 240f)
            val force = (target - value) * stiffness - velocity * c
            velocity += force * h
            value += velocity * h
            remaining -= h
        }
        if (abs(target - value) < 0.0005f && abs(velocity) < 0.001f) {
            value = target; velocity = 0f
        }
        return value
    }

    fun snap(v: Float = target) {
        value = v; target = v; velocity = 0f
    }

    val settled: Boolean get() = value == target && velocity == 0f
}

/** Simple exponential approach — cheaper than a spring, for colours and fades. */
class Ease(initial: Float, var speed: Float = 14f) {
    var value = initial
    var target = initial

    fun update(dt: Float = Frame.dt): Float {
        if (Frame.motion == 0f) value = target
        else value += (target - value) * (1f - exp(-speed * dt))
        if (abs(target - value) < 0.0005f) value = target
        return value
    }
}

object Easing {
    fun outCubic(t: Float) = 1f - (1f - t).let { it * it * it }
    fun inOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
    fun outBack(t: Float): Float {
        val c1 = 1.70158f; val c3 = c1 + 1f
        return 1f + c3 * (t - 1f).let { it * it * it } + c1 * (t - 1f).let { it * it }
    }
}
