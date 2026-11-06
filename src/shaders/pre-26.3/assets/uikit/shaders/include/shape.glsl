in vec4 vertexColor;
in vec2 localPos;
flat in vec2 halfSize;
flat in vec4 radii;
flat in float edgeWidth;

float roundedBoxSDF(vec2 p, vec2 extent, float radius) {
    vec2 q = abs(p) - extent + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float shapeDistance() {
    float r = localPos.x < 0.0 ? (localPos.y < 0.0 ? radii.x : radii.w) : (localPos.y < 0.0 ? radii.y : radii.z);
    r = min(r, min(halfSize.x, halfSize.y));
    return roundedBoxSDF(localPos, halfSize, r);
}

float shapeCoverage() {
    float d = shapeDistance();
    float aa = max(fwidth(d), 1e-4);
    float outer = clamp(0.5 - d / aa, 0.0, 1.0);
    if (edgeWidth == 0.0) return outer;
    float inner = clamp(0.5 - (d + abs(edgeWidth)) / aa, 0.0, 1.0);
    if (edgeWidth > 0.0) return outer - inner;
    return inner;
}
