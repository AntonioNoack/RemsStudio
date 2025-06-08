void main(){
    vec4 color0 = texture(tex0, uv);
    vec4 color1 = texture(tex1, uv);
    color0.rgb *= color0.rgb;
    color1.rgb *= color1.rgb;
    color0 = mix(color0,fadeColor,clamp(progress*2.0,0.0,1.0));
    color1 = mix(fadeColor,color1,clamp(progress*2.0-1.0,0.0,1.0));
    color = mix(color0,color1,clamp(progress,0.0,1.0));
    color.rgb = sqrt(max(color.rgb,vec3(0.0)));
    color.a = 1.0; // ok??
}