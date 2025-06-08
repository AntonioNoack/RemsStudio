void main(){
    color = progress > 0.5 ? texture(tex1, uv) : texture(tex0, uv);
    color = max(color,vec4(0.0));
    float progress2 = 2.0*abs(progress-0.5);
    color.rgb = pow(color.rgb,vec3(clamp(progress2,0.001,1.0)));
}