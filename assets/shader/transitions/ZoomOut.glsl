void main(){
    vec2 uv2 = mix(uv, center, -0.5/abs(progress-0.5)+1);
    color = progress > 0.5 ? getInRect(tex1, uv2) : getInRect(tex0, uv2);
}