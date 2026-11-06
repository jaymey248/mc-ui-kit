#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;      // fragment position relative to the shape centre (logical px)
in ivec2 UV1;     // half extents * 8
in ivec2 UV2;     // corner radii * 2, packed: x = tl | tr << 8, y = br | bl << 8
in float LineWidth; // 0 = fill, > 0 = outline width, < 0 = inset fill (fill inside an outline), shadow: blur

out vec4 vertexColor;
out vec2 localPos;
flat out vec2 halfSize;
flat out vec4 radii;
flat out float edgeWidth;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    localPos = UV0;
    halfSize = vec2(UV1) / 8.0;
    radii = vec4(float(UV2.x & 0xFF), float((UV2.x >> 8) & 0xFF), float(UV2.y & 0xFF), float((UV2.y >> 8) & 0xFF)) / 2.0;
    edgeWidth = LineWidth;
}
