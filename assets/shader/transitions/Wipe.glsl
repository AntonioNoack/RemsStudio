void main(){
    vec4 color0 = texture(tex0, uv);
    vec4 color1 = texture(tex1, uv);
    color = dot(direction, (uv-0.5)*2.0)*0.5+0.5 < progress ? color1 : color0;
}