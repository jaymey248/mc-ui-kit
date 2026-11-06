package dev.jamie.uikit.compat.render

import dev.jamie.uikit.core.Kit
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
//? if <26.3 {
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexFormat
//?} else {
/*import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.vertex.VertexFormat
*///?}
//? if <26.2 {
import com.mojang.blaze3d.vertex.VertexFormatElement
//?} else if <26.3 {
/*import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.PrimitiveTopology
import net.minecraft.client.renderer.BindGroupLayouts
*///?} else {
/*import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import net.minecraft.client.renderer.BindGroupLayouts
*///?}

/**
 * All custom GPU pipelines. VERSION-SPECIFIC: this file and [GuiElements] are what a Minecraft
 * render-API change touches, nothing outside `compat/`.
 * Stonecutter `//?` blocks select the code per version.
 */
object VsbPipelines {
    private fun id(path: String) = Kit.id(path)

    /** Position, colour, centre-relative pos (UV0), half size (UV1), radii (UV2), edge width. */
    //? if <26.2 {
    val SHAPE_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("UV0", VertexFormatElement.UV0)
        .add("UV1", VertexFormatElement.UV1)
        .add("UV2", VertexFormatElement.UV2)
        .add("LineWidth", VertexFormatElement.LINE_WIDTH)
        .build()

    val TEXT_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("UV0", VertexFormatElement.UV0)
        .add("UV1", VertexFormatElement.UV1)
        .build()
    //?} else {
    /*val SHAPE_FORMAT: VertexFormat = VertexFormat.builder(0)
        .addAttribute("Position", GpuFormat.RGB32_FLOAT)
        .addAttribute("Color", GpuFormat.RGBA8_UNORM)
        .addAttribute("UV0", GpuFormat.RG32_FLOAT)
        .addAttribute("UV1", GpuFormat.RG16_SINT)
        .addAttribute("UV2", GpuFormat.RG16_SINT)
        .addAttribute("LineWidth", GpuFormat.R32_FLOAT)
        .build()

    val TEXT_FORMAT: VertexFormat = VertexFormat.builder(0)
        .addAttribute("Position", GpuFormat.RGB32_FLOAT)
        .addAttribute("Color", GpuFormat.RGBA8_UNORM)
        .addAttribute("UV0", GpuFormat.RG32_FLOAT)
        .addAttribute("UV1", GpuFormat.RG16_SINT)
        .build()
    *///?}

    private fun gui(name: String, vertex: String, fragment: String, format: VertexFormat, sampler: Boolean = false): RenderPipeline {
        val b = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(id("pipeline/$name"))
            .withVertexShader(id("core/$vertex"))
            .withFragmentShader(id("core/$fragment"))
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        //? if <26.2 {
        b.withVertexFormat(format, VertexFormat.Mode.QUADS)
        if (sampler) b.withSampler("Sampler0")
        //?} else {
        /*b.withVertexBinding(0, format).withPrimitiveTopology(PrimitiveTopology.QUADS)
        if (sampler) b.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        *///?}
        return RenderPipelines.register(b.build())
    }

    val SHAPE = gui("shape", "shape", "shape", SHAPE_FORMAT)
    val SHAPE_SHADOW = gui("shape_shadow", "shape", "shape_shadow", SHAPE_FORMAT)
    val SHAPE_TEXTURED = gui("shape_textured", "shape", "shape_textured", SHAPE_FORMAT, sampler = true)
    val SDF_TEXT = gui("sdf_text", "sdf_text", "sdf_text", TEXT_FORMAT, sampler = true)

    /** Touch at init so pipelines register before the first resource reload compiles shaders. */
    fun init() {}
}
