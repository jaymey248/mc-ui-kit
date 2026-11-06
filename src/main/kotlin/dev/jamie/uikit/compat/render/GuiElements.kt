package dev.jamie.uikit.compat.render

//? if <26.3 {
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.textures.FilterMode
//?} else {
/*import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.FilterMode
*///?}
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Submits kit primitives into Minecraft's deferred GUI render state, so they sort/batch/layer with
 * vanilla items and text exactly like vanilla elements. VERSION-SPECIFIC.
 */
object GuiElements {
    private const val AA_PAD = 1.5f
    private const val ALPHA_MASK = 0xFF shl 24

    private var cachedPose: Matrix3x2f? = null

    /** The deferred element keeps a reference to the pose, so snapshot it (reusing equal matrices). */
    private fun snapshot(current: Matrix3x2fc): Matrix3x2f {
        val c = cachedPose
        if (c != null && c.equals(current, 0f)) return c
        return Matrix3x2f(current).also { cachedPose = it }
    }

    private fun packRadius(r: Float) = (r.coerceIn(0f, 127f) * 2f).roundToInt().coerceIn(0, 255)
    private fun fixed(v: Float) = (v * 8f).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

    val linearClamp get() = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)

    private val clipCorner = org.joml.Vector2f()

    /**
     * Pushes a scissor for the pose-space rectangle ([x0],[y0])–([x1],[y1]): its corners go through the current pose into
     * GUI px (axis-aligned bounds, outward to whole px) and the result is intersected with the enclosing scissor.
     * Pushed straight onto the scissor stack: `enableScissor` would apply the pose a second time. Pair with [popScissor].
     */
    fun pushScissor(g: GuiGraphicsExtractor, x0: Float, y0: Float, x1: Float, y1: Float) {
        val p = g.pose()
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until 4) {
            p.transformPosition(if (i and 1 == 0) x0 else x1, if (i < 2) y0 else y1, clipCorner)
            minX = min(minX, clipCorner.x); maxX = max(maxX, clipCorner.x)
            minY = min(minY, clipCorner.y); maxY = max(maxY, clipCorner.y)
        }
        val l = floor(minX).toInt(); val t = floor(minY).toInt()
        g.scissorStack.push(ScreenRectangle(l, t, max(0, ceil(maxX).toInt() - l), max(0, ceil(maxY).toInt() - t)))
    }

    fun popScissor(g: GuiGraphicsExtractor) {
        g.scissorStack.pop()
    }

    /**
     * Rounded rectangle with per-corner radii (tl, tr, br, bl) and per-corner colours.
     * [edge]: 0 fill, > 0 outline of that width, < 0 fill inset by |edge|. For [shadow], edge = blur radius.
     */
    fun shape(
        g: GuiGraphicsExtractor,
        x0: Float, y0: Float, x1: Float, y1: Float,
        cTL: Int, cTR: Int, cBR: Int, cBL: Int,
        rTL: Float, rTR: Float, rBR: Float, rBL: Float,
        edge: Float,
        shadow: Boolean = false,
        texture: TextureSetup? = null,
    ) {
        if ((cTL or cTR or cBR or cBL) and ALPHA_MASK == 0) return
        val left = min(x0, x1); val right = max(x0, x1)
        val top = min(y0, y1); val bottom = max(y0, y1)
        val hw = (right - left) * 0.5f; val hh = (bottom - top) * 0.5f
        if (hw <= 0f || hh <= 0f) return
        val pad = if (shadow) AA_PAD + edge else AA_PAD
        val qx0 = floor(left - pad); val qy0 = floor(top - pad)
        val qx1 = ceil(right + pad); val qy1 = ceil(bottom + pad)

        val pose = snapshot(g.pose())
        val scissor = g.scissorStack.peek()
        val quad = ScreenRectangle(qx0.toInt(), qy0.toInt(), (qx1 - qx0).toInt(), (qy1 - qy0).toInt()).transformMaxBounds(pose)
        val bounds = if (scissor != null) scissor.intersection(quad) ?: return else quad

        val pipeline = when {
            shadow -> VsbPipelines.SHAPE_SHADOW
            texture != null -> VsbPipelines.SHAPE_TEXTURED
            else -> VsbPipelines.SHAPE
        }
        g.guiRenderState.addGuiElement(
            ShapeState(
                pose, qx0, qy0, qx1, qy1, (left + right) * 0.5f, (top + bottom) * 0.5f,
                cTL, cTR, cBR, cBL, fixed(hw), fixed(hh),
                packRadius(rTL) or (packRadius(rTR) shl 8), packRadius(rBR) or (packRadius(rBL) shl 8),
                edge, pipeline, texture ?: TextureSetup.noTexture(), scissor, bounds,
            )
        )
    }

    private class ShapeState(
        val pose: Matrix3x2fc,
        val x0: Float, val y0: Float, val x1: Float, val y1: Float,
        val cx: Float, val cy: Float,
        val cTL: Int, val cTR: Int, val cBR: Int, val cBL: Int,
        val hw: Int, val hh: Int, val radiiTop: Int, val radiiBottom: Int,
        val edge: Float,
        val pipeline: RenderPipeline, val tex: TextureSetup,
        val scissor: ScreenRectangle?, val bounds: ScreenRectangle,
    ) : GuiElementRenderState {
        override fun pipeline() = pipeline
        override fun textureSetup() = tex
        override fun scissorArea() = scissor
        override fun bounds() = bounds

        override fun buildVertices(c: VertexConsumer) {
            v(c, x0, y0, cTL); v(c, x0, y1, cBL); v(c, x1, y1, cBR); v(c, x1, y0, cTR)
        }

        private fun v(c: VertexConsumer, x: Float, y: Float, color: Int) {
            c.addVertexWith2DPose(pose, x, y).setColor(color).setUv(x - cx, y - cy)
                .setUv1(hw, hh).setUv2(radiiTop, radiiBottom).setLineWidth(edge)
        }
    }

    /** A batch of textured quads drawn with the SDF text pipeline (one element per string). */
    class GlyphBatch(capacity: Int) {
        var data = FloatArray(capacity * 8)   // x0 y0 x1 y1 u0 v0 u1 v1
        var colors = IntArray(capacity)
        var weights = IntArray(capacity)
        var count = 0

        /** [weight] is the SDF threshold shift (0 regular … ~0.11 bold). */
        fun add(x0: Float, y0: Float, x1: Float, y1: Float, u0: Float, v0: Float, u1: Float, v1: Float, color: Int, weight: Float) {
            if (count == colors.size) {
                data = data.copyOf(data.size * 2)
                colors = colors.copyOf(colors.size * 2)
                weights = weights.copyOf(weights.size * 2)
            }
            weights[count] = (weight * 1000).roundToInt()
            val o = count * 8
            data[o] = x0; data[o + 1] = y0; data[o + 2] = x1; data[o + 3] = y1
            data[o + 4] = u0; data[o + 5] = v0; data[o + 6] = u1; data[o + 7] = v1
            colors[count++] = color
        }
    }

    fun glyphs(g: GuiGraphicsExtractor, batch: GlyphBatch, texture: TextureSetup, softness: Float) {
        if (batch.count == 0) return
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until batch.count) {
            val o = i * 8
            minX = min(minX, batch.data[o]); minY = min(minY, batch.data[o + 1])
            maxX = max(maxX, batch.data[o + 2]); maxY = max(maxY, batch.data[o + 3])
        }
        val pose = snapshot(g.pose())
        val scissor = g.scissorStack.peek()
        val quad = ScreenRectangle(floor(minX).toInt(), floor(minY).toInt(), (ceil(maxX) - floor(minX)).toInt() + 1, (ceil(maxY) - floor(minY)).toInt() + 1)
            .transformMaxBounds(pose)
        val bounds = if (scissor != null) scissor.intersection(quad) ?: return else quad
        g.guiRenderState.addGuiElement(
            TextState(pose, batch.data.copyOf(batch.count * 8), batch.colors.copyOf(batch.count), batch.weights.copyOf(batch.count), batch.count,
                (softness * 1000).roundToInt(), texture, scissor, bounds)
        )
    }

    private class TextState(
        val pose: Matrix3x2fc, val data: FloatArray, val colors: IntArray, val weights: IntArray, val count: Int,
        val softness: Int,
        val tex: TextureSetup, val scissor: ScreenRectangle?, val bounds: ScreenRectangle,
    ) : GuiElementRenderState {
        override fun pipeline() = VsbPipelines.SDF_TEXT
        override fun textureSetup() = tex
        override fun scissorArea() = scissor
        override fun bounds() = bounds

        override fun buildVertices(c: VertexConsumer) {
            for (i in 0 until count) {
                val o = i * 8
                val col = colors[i]
                val weight = weights[i]
                val x0 = data[o]; val y0 = data[o + 1]; val x1 = data[o + 2]; val y1 = data[o + 3]
                val u0 = data[o + 4]; val v0 = data[o + 5]; val u1 = data[o + 6]; val v1 = data[o + 7]
                c.addVertexWith2DPose(pose, x0, y0).setColor(col).setUv(u0, v0).setUv1(weight, softness)
                c.addVertexWith2DPose(pose, x0, y1).setColor(col).setUv(u0, v1).setUv1(weight, softness)
                c.addVertexWith2DPose(pose, x1, y1).setColor(col).setUv(u1, v1).setUv1(weight, softness)
                c.addVertexWith2DPose(pose, x1, y0).setColor(col).setUv(u1, v0).setUv1(weight, softness)
            }
        }
    }
}
