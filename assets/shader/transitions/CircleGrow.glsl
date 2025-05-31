void main(){
    vec4 color0 = texture(tex0, uv);
    vec4 color1 = texture(tex1, uv);
    float distance = length((uv-center)*aspect);
    float maxDistance = 2.0 * length(max(center, 1.0-center)*aspect);
    color = distance < progress * maxDistance ? color1 : color0;
}