#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;      // fragment position relative to the shape centre (logical px)
layout(location = 3) in ivec2 UV1;     // half extents * 8
layout(location = 4) in ivec2 UV2;     // corner radii * 2, packed: x = tl | tr << 8, y = br | bl << 8
layout(location = 5) in float LineWidth; // 0 = fill, > 0 = outline width, < 0 = inset fill (fill inside an outline), shadow: blur

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 localPos;
layout(location = 2) flat out vec2 halfSize;
layout(location = 3) flat out vec4 radii;
layout(location = 4) flat out float edgeWidth;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    localPos = UV0;
    halfSize = vec2(UV1) / 8.0;
    radii = vec4(float(UV2.x & 0xFF), float((UV2.x >> 8) & 0xFF), float(UV2.y & 0xFF), float((UV2.y >> 8) & 0xFF)) / 2.0;
    edgeWidth = LineWidth;
}
