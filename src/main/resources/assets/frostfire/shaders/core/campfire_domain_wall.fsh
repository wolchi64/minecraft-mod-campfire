#version 150

uniform sampler2D Sampler0;

uniform vec2 ScreenSize;
uniform vec4 FogColor;
uniform mat4 InverseProjMat;
uniform vec3 CameraPos;
uniform vec3 NearTopLeft;
uniform vec3 NearTopRight;
uniform vec3 NearBottomLeft;
uniform vec3 NearBottomRight;
uniform float Time;
uniform float WeatherIntensity;
uniform float WallHalfThickness;
uniform float WallBottomOffset;
uniform float WallTopOffset;
uniform float FarPlaneDistance;
uniform float ActiveZoneCount;
uniform vec4 Zone0;
uniform vec4 Zone1;
uniform vec4 Zone2;
uniform vec4 Zone3;
uniform vec4 Zone4;
uniform vec4 Zone5;
uniform vec4 Zone6;
uniform vec4 Zone7;

out vec4 fragColor;

const float SKY_DEPTH_THRESHOLD = 0.99999;
const float EPSILON = 0.0001;
const float VERTICAL_FADE = 40.0;
const float MIN_VISIBLE_BAND = 1.35;
const float FULL_VISIBLE_BAND = 5.0;
const float CLOSE_OCCLUDER_FADE_START = 6.0;
const float CLOSE_OCCLUDER_FADE_END = 10.0;
const float DEPTH_SOFTEN_SPREAD_NEAR = 1.5;
const float DEPTH_SOFTEN_SPREAD_FAR = 8.0;
const float DEPTH_SOFTEN_BLEND = 0.65;
const int MAX_ZONES = 8;

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

vec4 getZone(int index) {
    if (index == 0) return Zone0;
    if (index == 1) return Zone1;
    if (index == 2) return Zone2;
    if (index == 3) return Zone3;
    if (index == 4) return Zone4;
    if (index == 5) return Zone5;
    if (index == 6) return Zone6;
    return Zone7;
}

vec2 invalidInterval() {
    return vec2(1.0, 0.0);
}

vec2 clampInterval(vec2 interval, float tMax) {
    return vec2(max(interval.x, 0.0), min(interval.y, tMax));
}

vec2 intersectIntervals(vec2 a, vec2 b) {
    return vec2(max(a.x, b.x), min(a.y, b.y));
}

float intervalLength(vec2 interval) {
    return max(0.0, interval.y - interval.x);
}

float viewDistanceFromDepth(vec2 uv, float depthSample) {
    if (depthSample >= SKY_DEPTH_THRESHOLD) {
        return FarPlaneDistance;
    }

    vec2 ndc = (uv * 2.0) - 1.0;
    vec4 clipHit = vec4(ndc, (depthSample * 2.0) - 1.0, 1.0);
    vec4 viewHit = InverseProjMat * clipHit;
    viewHit /= max(viewHit.w, EPSILON);
    return length(viewHit.xyz);
}

float stableDepthDistance(vec2 uv, out float occlusionFade) {
    vec2 texel = 1.0 / ScreenSize;
    vec2 minUv = texel * 0.5;
    vec2 maxUv = vec2(1.0) - minUv;

    vec2 uvLeft = clamp(uv + vec2(-texel.x, 0.0), minUv, maxUv);
    vec2 uvRight = clamp(uv + vec2(texel.x, 0.0), minUv, maxUv);
    vec2 uvUp = clamp(uv + vec2(0.0, texel.y), minUv, maxUv);
    vec2 uvDown = clamp(uv + vec2(0.0, -texel.y), minUv, maxUv);
    vec2 uvUpLeft = clamp(uv + vec2(-texel.x, texel.y), minUv, maxUv);
    vec2 uvUpRight = clamp(uv + vec2(texel.x, texel.y), minUv, maxUv);
    vec2 uvDownLeft = clamp(uv + vec2(-texel.x, -texel.y), minUv, maxUv);
    vec2 uvDownRight = clamp(uv + vec2(texel.x, -texel.y), minUv, maxUv);

    float centerDistance = viewDistanceFromDepth(uv, texture(Sampler0, uv).r);
    float leftDistance = viewDistanceFromDepth(uvLeft, texture(Sampler0, uvLeft).r);
    float rightDistance = viewDistanceFromDepth(uvRight, texture(Sampler0, uvRight).r);
    float upDistance = viewDistanceFromDepth(uvUp, texture(Sampler0, uvUp).r);
    float downDistance = viewDistanceFromDepth(uvDown, texture(Sampler0, uvDown).r);
    float upLeftDistance = viewDistanceFromDepth(uvUpLeft, texture(Sampler0, uvUpLeft).r);
    float upRightDistance = viewDistanceFromDepth(uvUpRight, texture(Sampler0, uvUpRight).r);
    float downLeftDistance = viewDistanceFromDepth(uvDownLeft, texture(Sampler0, uvDownLeft).r);
    float downRightDistance = viewDistanceFromDepth(uvDownRight, texture(Sampler0, uvDownRight).r);

    float minDistance = min(centerDistance,
        min(min(leftDistance, rightDistance),
        min(min(upDistance, downDistance),
        min(min(upLeftDistance, upRightDistance), min(downLeftDistance, downRightDistance)))));
    float maxDistance = max(centerDistance,
        max(max(leftDistance, rightDistance),
        max(max(upDistance, downDistance),
        max(max(upLeftDistance, upRightDistance), max(downLeftDistance, downRightDistance)))));
    float averageDistance = (
        centerDistance + leftDistance + rightDistance + upDistance + downDistance +
        upLeftDistance + upRightDistance + downLeftDistance + downRightDistance) / 9.0;
    float spread = maxDistance - minDistance;
    float softenFactor = smoothstep(DEPTH_SOFTEN_SPREAD_NEAR, DEPTH_SOFTEN_SPREAD_FAR, spread);
    float softenedDistance = mix(averageDistance, maxDistance, softenFactor * DEPTH_SOFTEN_BLEND);

    occlusionFade = smoothstep(CLOSE_OCCLUDER_FADE_START, CLOSE_OCCLUDER_FADE_END, averageDistance);
    return softenedDistance;
}

vec2 cylinderInterval(vec3 origin, vec3 rayDir, float radius, float tMax) {
    float a = dot(rayDir.xz, rayDir.xz);
    float c = dot(origin.xz, origin.xz) - (radius * radius);

    if (a < EPSILON) {
        if (c <= 0.0) {
            return vec2(0.0, tMax);
        }
        return invalidInterval();
    }

    float b = 2.0 * dot(origin.xz, rayDir.xz);
    float discriminant = (b * b) - (4.0 * a * c);
    if (discriminant < 0.0) {
        return invalidInterval();
    }

    float root = sqrt(discriminant);
    float invTwoA = 0.5 / a;
    float t0 = (-b - root) * invTwoA;
    float t1 = (-b + root) * invTwoA;
    if (t0 > t1) {
        float swap = t0;
        t0 = t1;
        t1 = swap;
    }

    if (c <= 0.0) {
        return clampInterval(vec2(0.0, t1), tMax);
    }
    return clampInterval(vec2(t0, t1), tMax);
}

float bandSampleT(vec2 outerInterval, vec2 innerInterval, float innerRadius) {
    if (innerRadius <= EPSILON || intervalLength(innerInterval) <= 0.0) {
        return mix(outerInterval.x, outerInterval.y, 0.5);
    }

    if (outerInterval.x < innerInterval.x) {
        return mix(outerInterval.x, innerInterval.x, 0.5);
    }

    if (innerInterval.y < outerInterval.y) {
        return mix(innerInterval.y, outerInterval.y, 0.5);
    }

    return mix(outerInterval.x, outerInterval.y, 0.5);
}

float zoneContribution(vec4 zone, vec3 rayDir, float tMax, float stormFactor) {
    vec3 localOrigin = CameraPos - zone.xyz;
    float outerRadius = zone.w + WallHalfThickness;
    float innerRadius = max(zone.w - WallHalfThickness, 0.0);

    vec2 outerInterval = cylinderInterval(localOrigin, rayDir, outerRadius, tMax);
    float outerLength = intervalLength(outerInterval);
    if (outerLength <= 0.0) {
        return 0.0;
    }

    vec2 innerInterval = invalidInterval();
    float innerLength = 0.0;
    if (innerRadius > EPSILON) {
        innerInterval = cylinderInterval(localOrigin, rayDir, innerRadius, tMax);
        innerLength = intervalLength(innerInterval);
    }

    float bandLength = max(0.0, outerLength - innerLength);
    if (bandLength <= 0.0) {
        return 0.0;
    }
    float visibilityFactor = smoothstep(MIN_VISIBLE_BAND, FULL_VISIBLE_BAND, bandLength);
    if (visibilityFactor <= 0.0001) {
        return 0.0;
    }

    float sampleT = bandSampleT(outerInterval, innerInterval, innerRadius);
    vec3 worldSample = CameraPos + (rayDir * sampleT);
    vec3 localSample = worldSample - zone.xyz;

    float radialDistance = length(localSample.xz);
    float edgeFactor = 1.0 - smoothstep(0.0, WallHalfThickness, abs(radialDistance - zone.w));
    float verticalFactor = smoothstep(WallBottomOffset - (VERTICAL_FADE * 1.5), WallBottomOffset + VERTICAL_FADE, localSample.y)
        * (1.0 - smoothstep(WallTopOffset - VERTICAL_FADE, WallTopOffset + (VERTICAL_FADE * 1.5), localSample.y));
    if (verticalFactor <= 0.0001) {
        return 0.0;
    }

    vec3 bodySample = vec3(worldSample.x * 0.12, worldSample.y * 0.05, worldSample.z * 0.12)
        + vec3(Time * 0.12, -Time * 0.03, -Time * 0.08);
    vec3 detailSample = vec3(worldSample.z * 0.24, worldSample.y * 0.10, worldSample.x * 0.24)
        + vec3(-Time * 0.18, Time * 0.06, Time * 0.11);
    vec3 erosionSample = vec3(worldSample.x * 0.18, worldSample.y * 0.08, worldSample.z * 0.18)
        + vec3(Time * 0.07, -Time * 0.05, -Time * 0.09);

    float bodyNoise = smoothstep(0.20, 0.84, fbm(bodySample));
    float detailNoise = smoothstep(0.24, 0.76, fbm(detailSample));
    float erosionNoise = 1.0 - smoothstep(mix(0.66, 0.72, stormFactor), mix(0.88, 0.94, stormFactor), fbm(erosionSample));

    float densityPerBlock = mix(0.055, 0.095, stormFactor);
    float density = bandLength * densityPerBlock;
    density *= mix(0.78, 1.18, bodyNoise);
    density *= mix(0.90, 1.08, detailNoise);
    density *= mix(0.60, 1.0, erosionNoise);
    density *= visibilityFactor;
    density *= edgeFactor;
    density *= verticalFactor;
    return density;
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec3 nearPoint = mix(
        mix(NearBottomLeft, NearBottomRight, uv.x),
        mix(NearTopLeft, NearTopRight, uv.x),
        uv.y);
    vec3 rayDir = normalize(nearPoint);

    float occlusionFade;
    float tMax = stableDepthDistance(uv, occlusionFade);

    if (tMax <= EPSILON) {
        fragColor = vec4(0.0);
        return;
    }

    float stormFactor = clamp(WeatherIntensity, 0.0, 1.0);
    float totalDensity = 0.0;
    for (int zoneIndex = 0; zoneIndex < MAX_ZONES; zoneIndex++) {
        if (float(zoneIndex) >= ActiveZoneCount) {
            break;
        }

        vec4 zone = getZone(zoneIndex);
        if (zone.w <= 0.0) {
            continue;
        }

        totalDensity += zoneContribution(zone, rayDir, tMax, stormFactor);
    }

    totalDensity *= occlusionFade;
    float alpha = 1.0 - exp(-totalDensity);
    alpha = clamp(alpha, 0.0, 0.82);
    if (alpha <= 0.002) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 fogTint = mix(FogColor.rgb, vec3(0.92, 0.95, 1.0), 0.18);
    fragColor = vec4(fogTint, alpha);
}
