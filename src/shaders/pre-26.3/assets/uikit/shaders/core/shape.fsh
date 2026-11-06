#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <uikit:shape.glsl>

out vec4 fragColor;

void main() {
    float c = shapeCoverage();
    if (c <= 0.0) discard;
    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * c);
}
