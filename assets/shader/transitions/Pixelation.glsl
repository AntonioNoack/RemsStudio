void main(){
    vec2 size = textureSize(tex0,0);
    float res = size.y;
    float numPixels = clamp(1.0 - abs((progress-0.5)*2.0), 0.0, 1.0) * res;
    numPixels = max(round(numPixels) / 9.0, 1.0);
    vec2 pixelSize = size / numPixels;
    vec2 uv2 = round(uv * pixelSize) / pixelSize;
    color = progress > 0.5 ? texture(tex1, uv2) : texture(tex0, uv2);
}