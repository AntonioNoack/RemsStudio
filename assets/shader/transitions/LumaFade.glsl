void main(){
    vec4 color0 = texture(tex0, uv);
    vec4 color1 = texture(tex1, uv);
    vec4 colorX = fadeFirstTex ? color0 : color1;
    float bright = brightness(colorX);
    if(fadeBlackToWhite) bright = 1.0 - bright;
    color = progress > bright ? color1 : color0;
}