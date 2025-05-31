void main(){
    vec4 color0 = texture(tex0, uv);
    vec4 color1 = texture(tex1, uv);
    float dir = dot(direction, (uv-0.5)*2.0)*0.5+0.5;
    float maxLength = 1.0 / max(abs(direction.x), abs(direction.y));
    float progress2 = 1.0 - (1.0 - progress) * maxLength;
    color = abs(dir - 0.5) > 0.5 - progress2 * 0.5 ? color1 : color0;
}