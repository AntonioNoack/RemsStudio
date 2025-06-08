void main() {
    float power = clamp(1.0-abs(progress*2.0-1.0), 0.0, 1.0);
    float totalLength = abs(dot(textureSize(tex0,0),direction));
    float spread = 0.20 * totalLength * power;
    int maxNumSamples = 64;
    int numSamples = min(int(spread * 2.5), maxNumSamples) >> 1;
    bool firstTex = progress < 0.5;
    if (numSamples > 0) {
        vec4 sum = texture(firstTex ? tex0 : tex1, uv);
        sum.rgb *= sum.rgb;
        vec4 colorI;
        vec2 duv0 = (spread / (totalLength * float(numSamples))) * direction;
        float totalWeight = 1.0;
        for(int i=1;i<=numSamples;i++) {
            float relativeI = float(i)/float(numSamples);
            float weight = 1.0/(3.0*relativeI*relativeI+1.0);
            vec2 duv = float(i) * duv0;
            colorI = texture(firstTex ? tex0 : tex1, uv + duv);
            colorI.rgb *= colorI.rgb;
            sum += weight * colorI;
            colorI = texture(firstTex ? tex0 : tex1, uv - duv);
            colorI.rgb *= colorI.rgb;
            sum += weight * colorI;
            totalWeight += 2.0 * weight;
        }
        sum *= 1.0/totalWeight;
        sum.rgb = max(sum.rgb,vec3(0.0));
        color = vec4(sqrt(sum.rgb),sum.a);
    } else color = texture(firstTex ? tex0 : tex1, uv);
}