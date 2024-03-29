void main(){
    effect = mix(mask.a, dot(vec3(0.3), mask.rgb), useMaskColor);
    if(invertMask1) effect = 1.0 - effect;
    color = mix(
    texture(tex, uv2),
    texture(tex, round((uv2 - 0.5) / pixelating) * pixelating + 0.5),
    effect
    );
}