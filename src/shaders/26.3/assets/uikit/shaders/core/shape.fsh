#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <uikit:shape.glsl>

layout(location = 0) out vec4 fragColor;

void main() {
    float c = shapeCoverage();
    if (c <= 0.0) discard;
    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * c);
}
