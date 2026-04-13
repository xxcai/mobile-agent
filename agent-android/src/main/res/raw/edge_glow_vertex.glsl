#version 300 es

// 全屏 quad 顶点 shader
// 输入：裁剪空间坐标 [-1, 1]，输出 UV [0, 1]

in vec2 aPosition;
out vec2 vTexCoord;

void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = aPosition * 0.5 + 0.5;
}
