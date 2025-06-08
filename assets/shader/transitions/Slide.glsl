void main(){
    float progressI = clamp(progress, 0.0, 1.0);
    vec4 color0 = getInRect(tex0, uv - direction * progressI);
    vec4 color1 = getInRect(tex1, uv - direction * (progressI - 1.0));
    color = mixColor2(color1,color0,dot(direction, (uv-0.5)*2.0)*0.5+0.5 - progressI);
}