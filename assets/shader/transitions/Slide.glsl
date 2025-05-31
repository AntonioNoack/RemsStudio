void main(){
    vec4 color0 = getInRect(tex0, uv - direction * progress);
    vec4 color1 = getInRect(tex1, uv - direction * (progress - 1.0));
    color = dot(direction, (uv-0.5)*2.0)*0.5+0.5 < progress ? color1 : color0;
}