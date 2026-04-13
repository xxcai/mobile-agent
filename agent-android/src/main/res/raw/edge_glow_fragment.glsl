#version 300 es
precision highp float;

// ---- Uniforms ----
uniform float uTime;           // 经过时间（毫秒）
uniform vec2  uResolution;     // 画布分辨率（像素）
uniform float uCornerRadius;   // 屏幕圆角半径（像素）
uniform float uBlurRadius;     // 光晕扩散宽度（像素）
uniform float uPadding;        // 内缩距离（像素）
uniform float uAlpha;          // 全局透明度，状态动画驱动

// ---- 粒子 ----
const int MAX_PARTICLES = 80;
uniform int uParticleCount;
uniform vec4 uParticles[MAX_PARTICLES]; // xy=位置(UV), z=大小(px), w=alpha

in vec2 vTexCoord;
out vec4 fragColor;

// ---- SDF 圆角矩形 ----

float sdRoundRect(vec2 p, vec2 b, float r) {
    vec2 d = abs(p) - b + r;
    return length(max(d, 0.0)) - r;
}

// ---- HDR 色域转换----

// Rec.709 → Rec.2020 色彩空间矩阵
const mat3 RgbRec709To2020 = mat3(
    0.627403895934699,  0.069097289358232,  0.016391438875150,
    0.329283038377884,  0.919540395075459,  0.088013307877226,
    0.043313065687417,  0.011362315566309,  0.895595253247624
);

// HLG 编码常量
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

// sRGB → 线性
vec3 sRGB2Linear(vec3 pixel) {
    for (int i = 0; i < 3; i++) {
        if (pixel[i] <= 0.040448236277108)
            pixel[i] /= 12.92;
        else
            pixel[i] = pow((pixel[i] + 0.055) / 1.055, 2.2);
    }
    return pixel;
}

// HLG OETF（光电转移函数）
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

// 线性 → HLG
vec3 linearToHLG(vec3 pixel) {
    float Yd = dot(pixel, vec3(0.2627, 0.6780, 0.0593));
    float Ys = Yd > 0.0 ? pow(Yd, hlgInvGamma - 1.0) : 0.0;
    vec3 Ls = pixel * Ys * hlgPowNegInvGamma;
    return (hlgOETF(Ls) - hlgBeta) * hlgBetaFraction;
}

// sRGB → Rec.2020 (HLG 编码)
vec3 convert709To2020(vec3 pixel) {
    vec3 result = sRGB2Linear(pixel);
    result = RgbRec709To2020 * result;
    return linearToHLG(result);
}

// ---- 边缘颜色：角度渐变 ----

vec3 computeEdgeColor(vec2 uv) {
    vec2 centered = uv - 0.5;
    // 纵横比修正，使角度分布均匀
    centered.y *= uResolution.x / uResolution.y;
    float angle = atan(centered.y, centered.x) + uTime * 0.001 * 0.2;

    // 角度映射到 [0, 3) 闭环，mod 确保时间偏移后不越界
    float t = mod((angle / 3.14159265 + 1.0) * 1.5, 3.0);

    // 紫 → 青 → 蓝 → 紫（闭环）
    vec3 purple = vec3(0.77, 0.53, 1.0);
    vec3 cyan   = vec3(0.01, 0.77, 1.0);
    vec3 blue   = vec3(0.33, 0.51, 1.0);

    vec3 color;
    if (t < 1.0) {
        color = mix(purple, cyan, t);
    } else if (t < 2.0) {
        color = mix(cyan, blue, t - 1.0);
    } else {
        color = mix(blue, purple, t - 2.0);
    }

    // HDR 色域增强
    return convert709To2020(color);
}

// ---- 主函数 ----

void main() {
    vec2 uv = vTexCoord;

    // 像素坐标，原点在屏幕中心
    vec2 p = (uv - 0.5) * uResolution;

    // SDF 圆角矩形边界尺寸
    vec2 halfSize = uResolution * 0.5 - uPadding;

    // 呼吸波：沿边缘 3 周期正弦调制 blurRadius
    // 纵横比修正后计算角度
    vec2 ac = (uv - 0.5) * vec2(1.0, uResolution.y / uResolution.x);
    float angle = atan(ac.y, ac.x);
    // sin(angle*3 + time*0.5PI)：3 个鼓包，约 4 秒转一圈
    float wave = sin(angle * 3.0 + uTime * 0.001 * 0.5 * 3.14159265) * 0.45 + 0.55;
    float dynamicBlur = uBlurRadius * wave;

    // SDF 距离 → sigmoid：内部≈0，边界≈0.5，外部≈1
    float d = sdRoundRect(p, halfSize, uCornerRadius);
    float s = 1.0 / (1.0 + exp(-d / dynamicBlur));

    // 边缘颜色
    vec3 color = computeEdgeColor(uv);

    // 亮线：钟形曲线 4*s*(1-s)，在边界处（s=0.5）达到峰值
    float brightLine = 4.0 * s * (1.0 - s);
    // 柔和外扩：s*(1-s)^0.3，在 s≈0.77 处峰值，更宽的柔和光晕
    float outerGlow = s * pow(1.0 - s, 0.3) * 0.5;

    float intensity = clamp(brightLine + outerGlow, 0.0, 1.0);

    // ---- 流光：沿边缘流动的窄亮带 ----
    float flowPos = mod(uTime * 0.001 * 0.4, 2.0 * 3.14159265);
    float flowDist = abs(mod(angle - flowPos + 3.14159265, 2.0 * 3.14159265) - 3.14159265);
    float flowFade = smoothstep(0.3, 0.0, flowDist);
    // 只在边缘区域可见（借用 brightLine 的空间分布）
    float flowLight = flowFade * brightLine * 0.8;
    intensity = clamp(intensity + flowLight, 0.0, 1.0);

    // ---- 粒子贡献 ----
    vec3 particleAccum = vec3(0.0);
    for (int i = 0; i < uParticleCount; i++) {
        vec2 pPos = uParticles[i].xy;
        float pSize = uParticles[i].z;
        float pAlpha = uParticles[i].w;

        // 像素空间距离
        vec2 diff = (uv - pPos) * uResolution;
        float dist = length(diff);

        // 圆形实心软边，pow 让中心更饱满
        float fade = pow(max(1.0 - dist / pSize, 0.0), 0.6);
        // 粒子用自身位置对应的渐变色
        vec3 pColor = computeEdgeColor(pPos);
        particleAccum += pColor * fade * pAlpha * 1.5;
    }

    // 光晕颜色 + 粒子贡献，预乘 alpha 输出
    vec3 finalColor = color * intensity + particleAccum;
    float finalAlpha = clamp(intensity + dot(particleAccum, vec3(0.33)), 0.0, 1.0);
    fragColor = vec4(finalColor * uAlpha * 0.85, finalAlpha * uAlpha * 0.85);
}
