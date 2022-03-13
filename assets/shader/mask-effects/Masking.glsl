void main(){
    vec4 maskColor = vec4(
    mix(vec3(1.0), mask.rgb, useMaskColor),
    invertMask1 ? mask.a : 1.0-mask.a
    );
    color = texture(tex, uv2) * maskColor;
}