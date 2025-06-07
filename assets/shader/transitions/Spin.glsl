vec2 rotate(vec2 uv, float angle) {
    float c = cos(angle), s = sin(angle);
    uv *= aspect;
    uv = mat2(c,s,-s,c) * uv;
    uv /= aspect;
    return uv;
}
float dist(vec2 uv){
    uv = abs(uv-0.5);
    return max(uv.x,uv.y);
}
void main(){
    float angle0 = sign(direction.x) * progress * PI * 0.5;
    float angle1 = sign(direction.x) * (progress - 1.0) * PI * 0.5;
    vec2 uv0 = rotate(uv-center, angle0)+center;
    vec2 uv1 = rotate(uv-center, angle1)+center;
    vec4 color0 = getInRect(tex0, uv0);
    vec4 color1 = getInRect(tex1, uv1);
    color = dist(uv0) < dist(uv1) ? color0 : color1;
}