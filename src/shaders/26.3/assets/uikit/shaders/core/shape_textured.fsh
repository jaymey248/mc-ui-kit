#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <uikit:shape.glsl>

uniform sampler2D Sampler0;

layout(location = 0) out vec4 fragColor;

void main() {
    float c = shapeCoverage();
    if (c <= 0.0) discard;
    vec2 uv = clamp(localPos / halfSize * 0.5 + 0.5, 0.0, 1.0);
    vec4 color = texture(Sampler0, uv) * vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * c);
}
