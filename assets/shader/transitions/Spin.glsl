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
    float progressI = clamp(progress, 0.0, 1.0);
    float angle0 = sign(direction.x) * progressI * PI * 0.5;
    float angle1 = sign(direction.x) * (progressI - 1.0) * PI * 0.5;
    vec2 uv0 = rotate(uv-spinCorner, angle0)+spinCorner;
    vec2 uv1 = rotate(uv-spinCorner, angle1)+spinCorner;

    float f0 = isInsideF(uv0);
    float f1 = isInsideF(uv1);
    vec4 color0 = texture(tex0,uv0);
    vec4 color1 = texture(tex1,uv1);

    if (f0>0.0 && f1>0.0) {
        // on the edge between the two images
        color = mixColor(color0,color1,f1/(f0+f1));
    } else {
        // anywhere else
        color = dist(uv0) < dist(uv1)
            ? mixColor(fadeColor,color0,f0)
            : mixColor(fadeColor,color1,f1);
    }
}