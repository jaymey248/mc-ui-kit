#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 texCoord0;
layout(location = 2) flat in vec2 params;

layout(location = 0) out vec4 fragColor;

void main() {
    float dist = texture(Sampler0, texCoord0).a;
    float edge = 0.5 - params.x;
    float aa = max(fwidth(dist) * 0.75, 1e-4) + params.y;
    float a = smoothstep(edge - aa, edge + aa, dist);
    if (a <= 0.003) discard;
    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * a);
}
