void main(){
    vec4 color0 = texture(tex0, uv);
    vec4 color1 = texture(tex1, uv);
    vec2 uv2 = fract(uv*tiling.xy+tiling.zw);
    float dir = dot(direction, (uv2-0.5)*2.0)*0.5+0.5;
    float maxLength = 1.0 / max(abs(direction.x), abs(direction.y));
    float progress2 = 1.0 - clamp(1.0 - progress, 0.0, 1.0) * maxLength;
    color = mixColor2(color0,color1,abs(dir - 0.5) - (0.5 - progress2 * 0.5));
}