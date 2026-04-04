#version 150

uniform sampler2D Sampler0;

uniform vec2 TargetSize;
uniform vec4 FogColor;
uniform mat4 InverseProjMat;
uniform vec3 CameraPos;
uniform vec3 CameraLook;
uniform vec3 CameraUp;
uniform vec3 CameraLeft;
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

in vec2 TexCoord;
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
const float OVERLAP_BLEND_DISTANCE = 6.0;
const float EDGE_FEATHER_EXTRA = 7.5;
const float WALL_BOUNDARY_GAP_CAP = 0.35;
const float WALL_BAND_WIDTH_CAP = 13.0;
const float WALL_OUTER_OVERHANG = 4.0;
const float WALL_INNER_FADE = 3.5;
const float WALL_OUTER_FADE = 1.6;
const float RADIAL_WARP_STRENGTH = 2.8;
const float SYSTEM_BRIDGE_FADE = 9.0;
const float SYSTEM_ROUNDING_RADIUS = 8.0;
const float OUTSIDE_VIEWER_DENSITY_BOOST = 2.45;
const float OUTSIDE_VIEWER_OCCLUSION_RELAX = 0.85;
const int BAND_SAMPLE_COUNT = 3;
const int MAX_ZONES = 8;

float fogBoundaryGap();
float fogBandWidth();
float boundaryRadius(vec4 zone);
float outerBoundaryRadius(vec4 zone);

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
    vec2 texel = 1.0 / TargetSize;
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

vec3 worldRayDirection(vec2 uv) {
    vec2 ndc = (uv * 2.0) - 1.0;
    vec4 clipPoint = vec4(ndc, 1.0, 1.0);
    vec4 viewPoint = InverseProjMat * clipPoint;
    vec3 viewDir = normalize(viewPoint.xyz / max(viewPoint.w, EPSILON));
    vec3 worldDir = (CameraLeft * -viewDir.x) + (CameraUp * viewDir.y) + (CameraLook * -viewDir.z);
    return normalize(worldDir);
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

float systemBridgeFactor(int currentZoneIndex, vec4 currentZone, vec3 worldSample) {
    float currentDistance = length((worldSample - currentZone.xyz).xz);
    float currentBoundary = boundaryRadius(currentZone);
    float currentBoundaryDelta = abs(currentDistance - currentBoundary);
    float currentNearBoundary = 1.0 - smoothstep(0.0, SYSTEM_BRIDGE_FADE, currentBoundaryDelta);
    if (currentNearBoundary <= 0.0001) {
        return 0.0;
    }

    float bridgeFactor = 0.0;
    for (int zoneIndex = 0; zoneIndex < MAX_ZONES; zoneIndex++) {
        if (float(zoneIndex) >= ActiveZoneCount) {
            break;
        }
        if (zoneIndex == currentZoneIndex) {
            continue;
        }

        vec4 otherZone = getZone(zoneIndex);
        if (otherZone.w <= 0.0) {
            continue;
        }

        float centerDistance = length((currentZone.xyz - otherZone.xyz).xz);
        float overlapDepth = (currentZone.w + otherZone.w) - centerDistance;
        if (overlapDepth <= 0.0) {
            continue;
        }

        float otherDistance = length((worldSample - otherZone.xyz).xz);
        float otherBoundary = boundaryRadius(otherZone);
        float otherBoundaryDelta = abs(otherDistance - otherBoundary);
        float otherNearBoundary = 1.0 - smoothstep(0.0, SYSTEM_BRIDGE_FADE, otherBoundaryDelta);
        float linkedFactor = smoothstep(0.0, SYSTEM_BRIDGE_FADE * 1.5, overlapDepth);
        float roundedSeam = 1.0 - smoothstep(0.0, SYSTEM_ROUNDING_RADIUS,
            length(vec2(currentBoundaryDelta, otherBoundaryDelta)));
        float edgePairPresence = currentNearBoundary * otherNearBoundary;
        float bridgePresence = max((roundedSeam * 1.18), edgePairPresence * 0.78);
        bridgeFactor = max(bridgeFactor, bridgePresence * linkedFactor);
    }

    return bridgeFactor;
}

float overlapCutFactor(int currentZoneIndex, vec4 currentZone, vec3 worldSample) {
    float cutFactor = 1.0;
    float bridgeFactor = systemBridgeFactor(currentZoneIndex, currentZone, worldSample);
    for (int zoneIndex = 0; zoneIndex < MAX_ZONES; zoneIndex++) {
        if (float(zoneIndex) >= ActiveZoneCount) {
            break;
        }
        if (zoneIndex == currentZoneIndex) {
            continue;
        }

        vec4 otherZone = getZone(zoneIndex);
        if (otherZone.w <= 0.0) {
            continue;
        }

        float otherDistance = length((worldSample - otherZone.xyz).xz);
        float otherBoundary = boundaryRadius(otherZone);
        float insideOtherZone = 1.0 - smoothstep(otherBoundary - OVERLAP_BLEND_DISTANCE,
            otherBoundary + (OVERLAP_BLEND_DISTANCE * 0.65), otherDistance);
        float cutStrength = mix(1.0, 0.12, bridgeFactor);
        cutFactor *= (1.0 - (insideOtherZone * cutStrength));
        if (cutFactor <= 0.0001) {
            return 0.0;
        }
    }
    return cutFactor;
}

bool cameraInsideAnyZone() {
    for (int zoneIndex = 0; zoneIndex < MAX_ZONES; zoneIndex++) {
        if (float(zoneIndex) >= ActiveZoneCount) {
            break;
        }

        vec4 zone = getZone(zoneIndex);
        if (zone.w <= 0.0) {
            continue;
        }

        float cameraDistance = length((CameraPos - zone.xyz).xz);
        if (cameraDistance <= zone.w) {
            return true;
        }
    }
    return false;
}

vec3 wallTintColor() {
    float fogLuminance = dot(FogColor.rgb, vec3(0.2126, 0.7152, 0.0722));
    float nightFactor = 1.0 - smoothstep(0.40, 0.60, fogLuminance);
    float daylightFactor = smoothstep(0.60, 0.86, fogLuminance);
    vec3 nightTint = vec3(0.46, 0.44, 0.56);
    vec3 daylightTint = vec3(0.81, 0.82, 0.84);
    vec3 tintedColor = mix(FogColor.rgb, nightTint, nightFactor * 0.70);
    tintedColor = mix(tintedColor, daylightTint, daylightFactor * 0.28);
    float tintedLuminance = dot(tintedColor, vec3(0.2126, 0.7152, 0.0722));
    float daylightClamp = min(1.0, 0.76 / max(tintedLuminance, EPSILON));
    return mix(tintedColor, tintedColor * daylightClamp, daylightFactor);
}

float fogBoundaryGap() {
    return min(WallHalfThickness * 0.28, WALL_BOUNDARY_GAP_CAP);
}

float fogBandWidth() {
    return min(WallHalfThickness * 0.72, WALL_BAND_WIDTH_CAP);
}

float boundaryRadius(vec4 zone) {
    return max(zone.w - fogBoundaryGap(), 0.0);
}

float outerBoundaryRadius(vec4 zone) {
    return boundaryRadius(zone) + WALL_OUTER_OVERHANG;
}

float radialFogProfile(float radialDistance, float zoneRadius, float radialWarp, float viewerOutsideFactor) {
    float boundaryGap = fogBoundaryGap();
    float bandWidth = fogBandWidth();
    float fogInnerRadius = max(zoneRadius - boundaryGap - bandWidth, 0.0);
    float fogOuterRadius = max(zoneRadius - boundaryGap + WALL_OUTER_OVERHANG, 0.0);
    float warpedDistance = radialDistance + radialWarp;
    float innerFade = smoothstep(fogInnerRadius - (WALL_INNER_FADE * 0.25), fogInnerRadius + WALL_INNER_FADE, warpedDistance);
    float outerFade = 1.0 - smoothstep(fogOuterRadius - WALL_OUTER_FADE, fogOuterRadius, warpedDistance);
    float centerRadius = mix(fogInnerRadius, fogOuterRadius, 0.78);
    float centerSpread = max(bandWidth * 0.48, 1.0);
    float centerBody = exp(-pow((warpedDistance - centerRadius) / centerSpread, 2.0));
    float edgeBias = smoothstep(centerRadius - centerSpread * 0.15, fogOuterRadius - 0.35, warpedDistance);
    float profile = innerFade * outerFade;
    profile *= mix(0.34, 1.0, centerBody);
    profile *= mix(1.0, 1.34, edgeBias * viewerOutsideFactor);
    return profile;
}

float bandSegmentContribution(int currentZoneIndex, vec4 zone, vec3 rayDir, vec2 bandInterval, float stormFactor,
                              float viewerOutsideFactor) {
    float bandLength = intervalLength(bandInterval);
    if (bandLength <= 0.0) {
        return 0.0;
    }

    float densityPerBlock = mix(0.34, 0.52, stormFactor);
    float visibilityFactor = smoothstep(MIN_VISIBLE_BAND * 0.35, FULL_VISIBLE_BAND, bandLength);
    float stepLength = bandLength / float(BAND_SAMPLE_COUNT);
    float density = 0.0;

    for (int sampleIndex = 0; sampleIndex < BAND_SAMPLE_COUNT; sampleIndex++) {
        float sampleLerp = (float(sampleIndex) + 0.5) / float(BAND_SAMPLE_COUNT);
        float sampleT = mix(bandInterval.x, bandInterval.y, sampleLerp);
        vec3 worldSample = CameraPos + (rayDir * sampleT);
        vec3 localSample = worldSample - zone.xyz;
        float overlapCut = overlapCutFactor(currentZoneIndex, zone, worldSample);
        if (overlapCut <= 0.0001) {
            continue;
        }

        float verticalFactor = smoothstep(WallBottomOffset - (VERTICAL_FADE * 1.5), WallBottomOffset + VERTICAL_FADE, localSample.y)
            * (1.0 - smoothstep(WallTopOffset - VERTICAL_FADE, WallTopOffset + (VERTICAL_FADE * 1.5), localSample.y));
        if (verticalFactor <= 0.0001) {
            continue;
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

        float radialDistance = length(localSample.xz);
        float radialWarp = ((bodyNoise + detailNoise) - 1.0) * RADIAL_WARP_STRENGTH;
        float radialFactor = radialFogProfile(radialDistance, zone.w, radialWarp, viewerOutsideFactor);
        if (radialFactor <= 0.0001) {
            continue;
        }

        float sampleDensity = stepLength * densityPerBlock;
        sampleDensity *= mix(0.78, 1.18, bodyNoise);
        sampleDensity *= mix(0.90, 1.08, detailNoise);
        sampleDensity *= mix(0.90, 1.12, erosionNoise);
        sampleDensity *= radialFactor;
        sampleDensity *= verticalFactor;
        sampleDensity *= overlapCut;
        density += sampleDensity;
    }

    density *= visibilityFactor;
    return density;
}

float zoneContribution(int currentZoneIndex, vec4 zone, vec3 rayDir, float tMax, float stormFactor, float viewerOutsideFactor) {
    vec3 localOrigin = CameraPos - zone.xyz;
    float innerRadius = max(boundaryRadius(zone) - fogBandWidth(), 0.0);
    float outerRadius = outerBoundaryRadius(zone);

    vec2 outerInterval = cylinderInterval(localOrigin, rayDir, outerRadius, tMax);
    float outerLength = intervalLength(outerInterval);
    if (outerLength <= 0.0) {
        return 0.0;
    }

    if (innerRadius <= EPSILON) {
        return bandSegmentContribution(currentZoneIndex, zone, rayDir, outerInterval, stormFactor, viewerOutsideFactor);
    }

    vec2 innerInterval = cylinderInterval(localOrigin, rayDir, innerRadius, tMax);
    float innerLength = intervalLength(innerInterval);
    if (innerLength <= 0.0) {
        return bandSegmentContribution(currentZoneIndex, zone, rayDir, outerInterval, stormFactor, viewerOutsideFactor);
    }

    vec2 nearBand = clampInterval(vec2(outerInterval.x, innerInterval.x), tMax);
    vec2 farBand = clampInterval(vec2(innerInterval.y, outerInterval.y), tMax);
    float density = bandSegmentContribution(currentZoneIndex, zone, rayDir, nearBand, stormFactor, viewerOutsideFactor);
    density += bandSegmentContribution(currentZoneIndex, zone, rayDir, farBand, stormFactor, viewerOutsideFactor);
    return density;
}

void main() {
    vec2 uv = clamp(TexCoord, vec2(0.0), vec2(1.0));
    vec3 rayDir = worldRayDirection(uv);
    bool viewerInsideAnyZone = cameraInsideAnyZone();

    float occlusionFade;
    float tMax = stableDepthDistance(uv, occlusionFade);

    if (tMax <= EPSILON) {
        fragColor = vec4(0.0);
        return;
    }

    float stormFactor = clamp(WeatherIntensity, 0.0, 1.0);
    float viewerOutsideFactor = viewerInsideAnyZone ? 0.0 : 1.0;
    float totalDensity = 0.0;
    for (int zoneIndex = 0; zoneIndex < MAX_ZONES; zoneIndex++) {
        if (float(zoneIndex) >= ActiveZoneCount) {
            break;
        }

        vec4 zone = getZone(zoneIndex);
        if (zone.w <= 0.0) {
            continue;
        }

        totalDensity += zoneContribution(zoneIndex, zone, rayDir, tMax, stormFactor, viewerOutsideFactor);
    }

    float adjustedOcclusionFade = mix(occlusionFade, 1.0, viewerOutsideFactor * OUTSIDE_VIEWER_OCCLUSION_RELAX);
    totalDensity *= adjustedOcclusionFade * mix(1.15, OUTSIDE_VIEWER_DENSITY_BOOST, viewerOutsideFactor);
    float alpha = 1.0 - exp(-totalDensity);
    alpha = clamp(alpha, 0.0, 0.995);
    if (alpha <= 0.002) {
        fragColor = vec4(0.0);
        return;
    }

    fragColor = vec4(wallTintColor(), alpha);
}
