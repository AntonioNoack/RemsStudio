void main(){
    vec4 color0 = texture(tex0, uv);
    vec4 color1 = texture(tex1, uv);
    float distance = length((uv-center)*aspect);
    float maxDistance = 2.0 * length(max(center, 1.0-center)*aspect);
    color = mixColor2(color0,color1,distance - (1.0 - progress) * maxDistance);
}