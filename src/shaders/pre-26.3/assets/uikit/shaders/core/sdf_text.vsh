#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;   // x = weight * 1000 (0 regular, + bolder), y = softness * 1000 (shadows)

out vec4 vertexColor;
out vec2 texCoord0;
flat out vec2 params;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
    params = vec2(UV1) / 1000.0;
}
