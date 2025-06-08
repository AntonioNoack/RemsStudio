void main(){
    vec2 uv2 = mix(uv, center, 1.0-0.5/abs(clamp(progress, 0.0, 1.0)-0.5));
    color = progress > 0.5 ? getInRect(tex1, uv2) : getInRect(tex0, uv2);
}