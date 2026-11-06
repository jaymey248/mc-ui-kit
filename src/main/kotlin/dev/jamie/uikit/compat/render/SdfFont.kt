package dev.jamie.uikit.compat.render

import com.mojang.blaze3d.platform.NativeImage
import dev.jamie.uikit.core.Kit
import dev.jamie.uikit.core.log
import dev.jamie.uikit.core.mc
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

/**
 * Signed-distance-field font generated at runtime from a bundled TTF (stb_truetype, shipped with MC).
 * Glyphs are rasterised lazily into a growing atlas at [BASE_PX]; the shader reconstructs crisp edges at
 * any scale, and weight is a threshold shift — one atlas serves every size and weight.
 */
class SdfFont(private val resource: String, private val name: String, private val warmAscii: Boolean = true) {
    companion object {
        const val BASE_PX = 48f
        const val PADDING = 6
        private const val ON_EDGE = 128
        private const val ATLAS = 1024
    }

    class Glyph(
        val advance: Float, val x0: Float, val y0: Float, val w: Float, val h: Float,
        val u0: Float, val v0: Float, val u1: Float, val v1: Float,
    )

    private lateinit var info: STBTTFontinfo
    private lateinit var data: ByteBuffer
    private var scale = 1f
    /** Ascent/descent/line gap in BASE_PX units. */
    var ascent = 0f; private set
    var descent = 0f; private set
    var lineGap = 0f; private set

    private val glyphs = Int2ObjectOpenHashMap<Glyph>()
    private val kerning = HashMap<Long, Float>()
    private var image: NativeImage? = null
    private var texture: DynamicTexture? = null
    private var setup: TextureSetup? = null
    private var penX = 1; private var penY = 1; private var rowH = 0
    private var dirty = false
    var failed = false; private set

    private fun ensureLoaded(): Boolean {
        if (::info.isInitialized) return !failed
        try {
            val bytes = SdfFont::class.java.getResourceAsStream(resource)!!.readAllBytes()
            data = MemoryUtil.memAlloc(bytes.size).put(bytes).flip()
            info = STBTTFontinfo.malloc()
            check(stbtt_InitFont(info, data)) { "stbtt_InitFont failed" }
            scale = stbtt_ScaleForPixelHeight(info, BASE_PX)
            MemoryStack.stackPush().use { s ->
                val a = s.mallocInt(1); val d = s.mallocInt(1); val g = s.mallocInt(1)
                stbtt_GetFontVMetrics(info, a, d, g)
                ascent = a[0] * scale; descent = d[0] * scale; lineGap = g[0] * scale
            }
            image = NativeImage(ATLAS, ATLAS, true)
            val tex = DynamicTexture({ "${Kit.modId}:$name" }, image!!)
            mc.textureManager.register(Kit.id("font/$name"), tex)
            texture = tex
            setup = TextureSetup.singleTexture(tex.textureView, GuiElements.linearClamp)
            // Warm up printable ASCII so the first frame is complete.
            if (warmAscii) for (c in 32..126) glyph(c)
        } catch (t: Throwable) {
            failed = true
            log.error("Failed to load font $resource — falling back to vanilla font", t)
        }
        return !failed
    }

    private val missing = it.unimi.dsi.fastutil.ints.IntOpenHashSet()

    /** The glyph if this font really has it (misses are cached), else null. */
    fun find(cp: Int): Glyph? {
        if (!ensureLoaded()) return null
        glyphs.get(cp)?.let { return it }
        if (missing.contains(cp)) return null
        val g = rasterize(cp)
        if (g == null) missing.add(cp) else glyphs.put(cp, g)
        return g
    }

    /** Like [find] but substitutes '?' for missing glyphs in text fonts. */
    fun glyph(cp: Int): Glyph? = find(cp) ?: if (warmAscii && cp != '?'.code) find('?'.code) else null

    fun kern(a: Int, b: Int): Float {
        val k = (a.toLong() shl 32) or b.toLong()
        return kerning.getOrPut(k) { stbtt_GetCodepointKernAdvance(info, a, b) * scale }
    }

    private fun rasterize(cp: Int): Glyph? {
        if (stbtt_FindGlyphIndex(info, cp) == 0 && cp != ' '.code) return null
        MemoryStack.stackPush().use { s ->
            val adv = s.mallocInt(1); val lsb = s.mallocInt(1)
            stbtt_GetCodepointHMetrics(info, cp, adv, lsb)
            val w = s.mallocInt(1); val h = s.mallocInt(1); val xo = s.mallocInt(1); val yo = s.mallocInt(1)
            val sdf = stbtt_GetCodepointSDF(info, scale, cp, PADDING, ON_EDGE.toByte(), ON_EDGE / PADDING.toFloat(), w, h, xo, yo)
            val advance = adv[0] * scale
            if (sdf == null) return Glyph(advance, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            val gw = w[0]; val gh = h[0]
            if (penX + gw + 1 >= ATLAS) { penX = 1; penY += rowH + 1; rowH = 0 }
            if (penY + gh + 1 >= ATLAS) {
                stbtt_FreeSDF(sdf)
                log.warn("Font atlas full, glyph $cp dropped")
                return null
            }
            val img = image!!
            for (yy in 0 until gh) for (xx in 0 until gw) {
                val a = sdf.get(yy * gw + xx).toInt() and 0xFF
                img.setPixel(penX + xx, penY + yy, (a shl 24) or 0xFFFFFF)
            }
            stbtt_FreeSDF(sdf)
            val g = Glyph(
                advance, xo[0].toFloat(), yo[0].toFloat(), gw.toFloat(), gh.toFloat(),
                penX / ATLAS.toFloat(), penY / ATLAS.toFloat(), (penX + gw) / ATLAS.toFloat(), (penY + gh) / ATLAS.toFloat(),
            )
            penX += gw + 1
            rowH = maxOf(rowH, gh)
            dirty = true
            return g
        }
    }

    /** Uploads new glyphs; call before submitting any element that uses the atlas. */
    fun textureSetup(): TextureSetup? {
        if (!ensureLoaded()) return null
        if (dirty) {
            dirty = false
            texture?.upload()
        }
        return setup
    }
}
