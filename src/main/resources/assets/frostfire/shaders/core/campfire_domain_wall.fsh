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
    float driftSpeed = mix(0.18, 0.11, stormFactor);
    float swirlSpeed = mix(0.34, 0.20, stormFactor);
    float largeScale = mix(0.16, 0.11, stormFactor);
    float detailScale = mix(0.42, 0.31, stormFactor);

    vec3 bodySample = vec3(worldPos.x * largeScale, worldPos.y * 0.055, worldPos.z * largeScale)
        + vec3(Time * driftSpeed, -Time * driftSpeed * 0.28, -Time * driftSpeed * 0.46);
    vec3 crossSample = vec3(worldPos.z * (largeScale * 0.82), worldPos.y * 0.072, worldPos.x * (largeScale * 0.82))
        + vec3(-Time * driftSpeed * 0.43, Time * driftSpeed * 0.19, Time * driftSpeed * 0.36);
    vec3 detailSample = vec3(worldPos.x * detailScale, worldPos.y * 0.145, worldPos.z * detailScale)
        + vec3(-Time * swirlSpeed, Time * swirlSpeed * 0.41, Time * swirlSpeed * 0.67);
    vec3 erosionSample = vec3(worldPos.z * 0.24, worldPos.y * 0.11, worldPos.x * 0.24)
        + vec3(Time * swirlSpeed * 0.29, -Time * swirlSpeed * 0.22, -Time * swirlSpeed * 0.38);

    float bodyNoise = fbm(bodySample);
    float crossNoise = fbm(crossSample);
    float detailNoise = fbm(detailSample);
    float erosionNoise = fbm(erosionSample);

    float body = smoothstep(0.20, 0.92, mix(bodyNoise, crossNoise, 0.45));
    float wisps = smoothstep(0.18, 0.84, detailNoise);
    float pockets = 1.0 - smoothstep(mix(0.56, 0.64, stormFactor), mix(0.86, 0.92, stormFactor),
        erosionNoise + (0.16 * (1.0 - body)));
    float verticalPulse = 0.78 + (0.22 * smoothstep(0.12, 0.88,
        fbm(vec3(worldPos.x * 0.09, worldPos.y * 0.03, worldPos.z * 0.09) + vec3(Time * 0.06, -Time * 0.03, 0.0))));

    float density = mix(0.62, 0.88, stormFactor);
    float alpha = vertexColor.a * density;
    alpha *= mix(0.78, 1.28, body);
    alpha *= mix(0.86, 1.14, wisps);
    alpha *= mix(0.48, 1.0, pockets);
    alpha *= mix(0.96, 1.10, crossNoise);
    alpha *= verticalPulse;

    if (alpha <= 0.008) {
        discard;
    }

    vec3 color = vertexColor.rgb;
    float tintNoise = mix(bodyNoise, detailNoise, 0.35);
    color *= mix(0.92, 1.03, tintNoise);
    color *= mix(vec3(0.96, 0.98, 1.0), vec3(1.0), stormFactor * 0.35);

    fragColor = vec4(color, clamp(alpha, 0.0, 1.0)) * ColorModulator;
}
