#version 300 es
precision highp float;

uniform float uTime;
uniform vec2 uResolution;

in vec2 vTexCoord;
out vec4 fragColor;

void main() {
    // Step 01 placeholder: time-varying gradient to verify the pipeline
    float t = uTime * 0.001;
    vec2 uv = vTexCoord;

    float r = 0.5 + 0.5 * sin(t + uv.x * 3.0);
    float g = 0.3 + 0.2 * sin(t * 0.7 + uv.y * 2.0);
    float b = 0.7 + 0.3 * cos(t * 1.3 + uv.x * uv.y * 4.0);

    fragColor = vec4(r, g, b, 0.3);
}
