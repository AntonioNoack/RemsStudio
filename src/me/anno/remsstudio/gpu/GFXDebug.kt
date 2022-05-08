package me.anno.remsstudio.gpu

import me.anno.gpu.shader.GeoShader

object GFXDebug {

    // is used to draw indexed geometry optionally as lines (for debugging purposes)
    val lineGeometry = GeoShader(
        "layout(triangles) in;\n" + "layout(line_strip, max_vertices = 6) out;\n" + "#inOutVarying\n" + "void main(){\n" + "   #copy[0]\n" + "   gl_Position = gl_in[0].gl_Position;\n" + "   EmitVertex();\n" + // a-b
                "   #copy[1]\n" + "   gl_Position = gl_in[1].gl_Position;\n" + "   EmitVertex();\n" + // b-c
                "   #copy[2]\n" + "   gl_Position = gl_in[2].gl_Position;\n" + "   EmitVertex();\n" + // c-a
                "   #copy[0]\n" + "   gl_Position = gl_in[0].gl_Position;\n" + "   EmitVertex();\n" + "   EndPrimitive();\n" + "}"
    )

    // does not work for scaled things, somehow...
    /*val normalGeometry0 = GeoShader(
        "layout(triangles) in;\n" +
                "layout(triangle_strip, max_vertices = 12) out;\n" +
                "\n" +
                "#inOutVarying\n" +
                "\n" +
                // v_normal[i]
                // x*w,y*w,z*w,w
                "uniform mat4 transform;\n" + // the camera + projection transform
                "void main(){\n" +
                // the normal lines for visualization
                "   for(int i=0;i<3;i++){\n" +
                "       #copy[i]\n" +
                "       gl_Position = gl_in[i].gl_Position;\n" +
                "       vec4 normal = transform * vec4(v_normal[i]*gl_Position.w*0.05, 0.0);\n" +
                "       vec4 smallOffset = vec4(normalize(vec2(normal.y,-normal.x))*0.004,0.0,0.0)*gl_in[i].gl_Position.w;\n" +
                "       gl_Position = gl_in[i].gl_Position - smallOffset;\n" +
                "       EmitVertex();\n" +
                "       gl_Position = gl_in[i].gl_Position + smallOffset;\n" +
                "       EmitVertex();\n" +
                "       gl_Position = gl_in[i].gl_Position + normal;\n" +
                "       EmitVertex();\n" +
                "       EndPrimitive();\n" +
                "   }\n" +
                // the original triangle
                "   for(int i=0;i<3;i++){\n" +
                "       #copy[i]\n" +
                "       gl_Position = gl_in[i].gl_Position;\n" +
                "       EmitVertex();\n" +
                "   }\n" +
                "   EndPrimitive();\n" +
                "}\n"
    )*/

    val cullFaceColoringGeometry = GeoShader(
        "layout(triangles) in;\n" + "layout(triangle_strip, max_vertices = 3) out;\n" + "#inOutVarying\n" + "void main(){\n" +
                // check if front facing or back facing, and change the color
                "   vec3 a = gl_in[0].gl_Position.xyz / gl_in[0].gl_Position.w;\n" + "   vec3 b = gl_in[1].gl_Position.xyz / gl_in[1].gl_Position.w;\n" + "   vec3 c = gl_in[2].gl_Position.xyz / gl_in[2].gl_Position.w;\n" + "   vec4 color = vec4(cross(b-a,c-a).z < 0.0 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 0.0, 1.0), 1.0);\n" + "   for(int i=0;i<3;i++){\n" + "       #copy[i]\n" + "       f_vertexColor = color;\n" + "       gl_Position = gl_in[i].gl_Position;\n" + "       EmitVertex();\n" + "   }\n" + "   EndPrimitive();\n" + "}\n"
    )

}