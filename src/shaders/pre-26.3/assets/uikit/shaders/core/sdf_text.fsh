#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;
flat in vec2 params;

out vec4 fragColor;

void main() {
    float dist = texture(Sampler0, texCoord0).a;
    float edge = 0.5 - params.x;
    float aa = max(fwidth(dist) * 0.75, 1e-4) + params.y;
    float a = smoothstep(edge - aa, edge + aa, dist);
    if (a <= 0.003) discard;
    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * a);
}
