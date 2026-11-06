#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <uikit:shape.glsl>

out vec4 fragColor;

void main() {
    // edgeWidth carries the blur radius. Smooth gaussian-like falloff outside the shape.
    float d = shapeDistance();
    float blur = max(edgeWidth, 1.0);
    float t = clamp(1.0 - (d + blur * 0.25) / blur, 0.0, 1.0);
    float a = t * t * (3.0 - 2.0 * t);
    if (a <= 0.0) discard;
    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * a);
}
