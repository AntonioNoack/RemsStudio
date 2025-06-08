void main(){
    vec2 uv2 = mix(uv, center, clamp(1.0-2.0*abs(progress-0.5), 0.0, 1.0));
    color = progress > 0.5 ? texture(tex1, uv2) : texture(tex0, uv2);
}