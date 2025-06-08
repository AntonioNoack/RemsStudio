void main() {
    float angle = clamp(progress, 0.0, 1.0) * PI;
    vec2 uv2 = rotate(uv - 0.5, direction);
    float posX = cos(angle);
    float zFactor = (1.0 - 0.9 * sin(angle) * sign(posX) * uv2.x);

    vec2 uv3 = zFactor * vec2(uv2.x / abs(posX), uv2.y);
    uv3 = rotateInv(uv3, direction) + 0.5;
    color = progress > 0.5 ? getInRect(tex1, uv3) : getInRect(tex0, uv3);
}