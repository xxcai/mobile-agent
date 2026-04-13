#version 300 es
precision highp float;

uniform float uTime;
uniform vec2 uResolution;
uniform float uCornerRadius;
uniform float uBlurRadius;
uniform float uPadding;

in vec2 vTexCoord;
out vec4 fragColor;

// ---- SDF ----

float sdRoundRect(vec2 p, vec2 b, float r) {
    vec2 d = abs(p) - b + r;
    return length(max(d, 0.0)) - r;
}

// ---- HDR Color Pipeline ----

const mat3 RgbRec709To2020 = mat3(
    0.627403895934699,  0.069097289358232,  0.016391438875150,
    0.329283038377884,  0.919540395075459,  0.088013307877226,
    0.043313065687417,  0.011362315566309,  0.895595253247624
);

const float hlgGamma       = 1.2;
const float hlgInvGamma    = 1.0 / hlgGamma;
const float hlgPeakLevel   = 1.0;
const float hlgBlackLevel  = 0.0001;
const float hlgPowNegInvGamma = pow(hlgPeakLevel, -hlgInvGamma);
const float hlgBeta        = sqrt(3.0 * pow(hlgBlackLevel / hlgPeakLevel, hlgInvGamma));
const float hlgBetaFraction = 1.0 / (1.0 - hlgBeta);

#define HLG_A (0.17883277)
#define HLG_B (1.0 - (4.0 * HLG_A))
#define HLG_C (0.5 - HLG_A * log(4.0 * HLG_A))
#define HLG_EPSILON (0.0001)

vec3 sRGB2Linear(vec3 pixel) {
    for (int i = 0; i < 3; i++) {
        if (pixel[i] <= 0.040448236277108)
            pixel[i] /= 12.92;
        else
            pixel[i] = pow((pixel[i] + 0.055) / 1.055, 2.2);
    }
    return pixel;
}

vec3 hlgOETF(vec3 pixel) {
    pixel = max(pixel, 0.0);
    for (int i = 0; i < 3; i++) {
        if (pixel[i] <= (1.0 / 12.0))
            pixel[i] = sqrt(pixel[i] * 3.0);
        else
            pixel[i] = HLG_A * log(max(HLG_EPSILON, 12.0 * pixel[i] - HLG_B)) + HLG_C;
    }
    return pixel;
}

vec3 linearToHLG(vec3 pixel) {
    float Yd = dot(pixel, vec3(0.2627, 0.6780, 0.0593));
    float Ys = Yd > 0.0 ? pow(Yd, hlgInvGamma - 1.0) : 0.0;
    vec3 Ls = pixel * Ys * hlgPowNegInvGamma;
    return (hlgOETF(Ls) - hlgBeta) * hlgBetaFraction;
}

vec3 convert709To2020(vec3 pixel) {
    vec3 result = sRGB2Linear(pixel);
    result = RgbRec709To2020 * result;
    return linearToHLG(result);
}

// ---- Edge color ----

vec3 computeEdgeColor(vec2 uv) {
    // Simple angle-based gradient: hue varies along the edge
    vec2 centered = uv - 0.5;
    // Aspect ratio correction
    centered.y *= uResolution.x / uResolution.y;
    float angle = atan(centered.y, centered.x);

    // Map angle [-PI, PI] to two color stops
    float t = angle / 3.14159265 + 1.0; // [0, 2]

    // Purple → Cyan → Purple
    vec3 purple = vec3(0.77, 0.53, 1.0);
    vec3 cyan   = vec3(0.01, 0.77, 1.0);
    vec3 blue   = vec3(0.33, 0.51, 1.0);

    vec3 color;
    if (t < 1.0) {
        color = mix(purple, cyan, t);
    } else {
        color = mix(cyan, blue, t - 1.0);
    }

    // Apply HDR
    return convert709To2020(color);
}

// ---- Main ----

void main() {
    vec2 uv = vTexCoord;

    // Pixel coords with origin at screen center
    vec2 p = (uv - 0.5) * uResolution;

    // SDF boundary at screen edge (no inset)
    vec2 halfSize = uResolution * 0.5 - uPadding;

    // Sigmoid of SDF: gives smooth transition at the edge
    //   ~0 deep inside, 0.5 at boundary, ~1 outside
    float d = sdRoundRect(p, halfSize, uCornerRadius);
    float s = 1.0 / (1.0 + exp(-d / uBlurRadius));

    // Edge color with HDR
    vec3 color = computeEdgeColor(uv);

    // Glow: bright line at boundary + soft outer falloff
    //   4*s*(1-s) = bell curve, peaks at s=0.5 (the SDF boundary)
    float brightLine = 4.0 * s * (1.0 - s);
    //   s*(1-s)^0.3 = wider soft glow, peaks around s≈0.77
    float outerGlow = s * pow(1.0 - s, 0.3) * 0.5;

    float intensity = clamp(brightLine + outerGlow, 0.0, 1.0);

    fragColor = vec4(color * intensity * 0.85, intensity * 0.85);
}
