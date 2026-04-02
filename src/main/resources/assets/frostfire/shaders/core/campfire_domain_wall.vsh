#version 150

in vec3 Position;
out vec2 TexCoord;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main() {
    TexCoord = Position.xy * 0.5 + 0.5;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
