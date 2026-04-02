#version 150

in vec4 vertexColor;
in vec3 worldPos;

uniform vec4 ColorModulator;
uniform float Time;
uniform float WeatherIntensity;

out vec4 fragColor;

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453123);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);

    float a = hash(i);
    float b = hash(i + vec3(1.0, 0.0, 0.0));
    float c = hash(i + vec3(0.0, 1.0, 0.0));
    float d = hash(i + vec3(1.0, 1.0, 0.0));
    float e = hash(i + vec3(0.0, 0.0, 1.0));
    float f1 = hash(i + vec3(1.0, 0.0, 1.0));
    float g = hash(i + vec3(0.0, 1.0, 1.0));
    float h = hash(i + vec3(1.0, 1.0, 1.0));

    float front = mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
    float back = mix(mix(e, f1, u.x), mix(g, h, u.x), u.y);
    return mix(front, back, u.z);
}

float fbm(vec3 p) {
    float value = 0.0;
    float amplitude = 0.55;
    for (int octave = 0; octave < 4; octave++) {
        value += amplitude * noise(p);
        p = (p * 2.03) + vec3(17.0, -11.0, 7.0);
        amplitude *= 0.5;
    }
    return value;
}

void main() {
    if (vertexColor.a <= 0.001) {
        discard;
    }

    float stormFactor = clamp(WeatherIntensity, 0.0, 1.0);
    float driftSpeed = mix(0.16, 0.09, stormFactor);
    float swirlSpeed = mix(0.26, 0.16, stormFactor);
    float largeScale = mix(0.14, 0.10, stormFactor);
    float detailScale = mix(0.34, 0.26, stormFactor);

    vec3 bodySample = vec3(worldPos.x * largeScale, worldPos.y * 0.050, worldPos.z * largeScale)
        + vec3(Time * driftSpeed, -Time * driftSpeed * 0.20, -Time * driftSpeed * 0.34);
    vec3 crossSample = vec3(worldPos.z * (largeScale * 0.76), worldPos.y * 0.068, worldPos.x * (largeScale * 0.76))
        + vec3(-Time * driftSpeed * 0.36, Time * driftSpeed * 0.16, Time * driftSpeed * 0.28);
    vec3 detailSample = vec3(worldPos.x * detailScale, worldPos.y * 0.120, worldPos.z * detailScale)
        + vec3(-Time * swirlSpeed, Time * swirlSpeed * 0.34, Time * swirlSpeed * 0.52);
    vec3 erosionSample = vec3(worldPos.z * 0.20, worldPos.y * 0.090, worldPos.x * 0.20)
        + vec3(Time * swirlSpeed * 0.22, -Time * swirlSpeed * 0.18, -Time * swirlSpeed * 0.28);

    float bodyNoise = fbm(bodySample);
    float crossNoise = fbm(crossSample);
    float detailNoise = fbm(detailSample);
    float erosionNoise = fbm(erosionSample);

    float body = smoothstep(0.18, 0.88, mix(bodyNoise, crossNoise, 0.42));
    float wisps = smoothstep(0.16, 0.80, detailNoise);
    float pockets = 1.0 - smoothstep(mix(0.62, 0.68, stormFactor), mix(0.88, 0.93, stormFactor),
        erosionNoise + (0.12 * (1.0 - body)));
    float verticalPulse = 0.86 + (0.14 * smoothstep(0.18, 0.84,
        fbm(vec3(worldPos.x * 0.08, worldPos.y * 0.025, worldPos.z * 0.08) + vec3(Time * 0.05, -Time * 0.02, 0.0))));

    float density = mix(0.74, 0.92, stormFactor);
    float alpha = vertexColor.a * density;
    alpha *= mix(0.84, 1.20, body);
    alpha *= mix(0.88, 1.08, wisps);
    alpha *= mix(0.56, 1.0, pockets);
    alpha *= mix(0.96, 1.08, crossNoise);
    alpha *= verticalPulse;

    if (alpha <= 0.006) {
        discard;
    }

    vec3 color = vertexColor.rgb;
    float tintNoise = mix(bodyNoise, detailNoise, 0.35);
    color *= mix(0.95, 1.02, tintNoise);
    color *= mix(vec3(0.96, 0.98, 1.0), vec3(1.0), stormFactor * 0.35);

    fragColor = vec4(color, clamp(alpha, 0.0, 1.0)) * ColorModulator;
}
