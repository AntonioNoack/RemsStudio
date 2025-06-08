void main(){
    vec4 color0 = texture(tex0, uv);
    vec4 color1 = texture(tex1, uv);
    vec2 uv2 = fract(tiling.xy * uv + tiling.zw);
    float distance = length((uv2-center)*aspect);
    float maxDistance = 2.0 * length(max(center, 1.0-center)*aspect);
    color = mixColor2(color0,color1,distance - clamp(1.0 - progress, 0.0, 1.0) * maxDistance);
}