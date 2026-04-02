#version 150

uniform sampler2D Sampler0;

uniform vec2 ScreenSize;
uniform vec4 FogColor;
uniform mat4 InverseProjMat;
uniform mat4 InverseViewRotMat;
uniform vec3 CameraPos;
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

vec2 slabInterval(vec3 origin, vec3 rayDir, float minY, float maxY, float tMax) {
    if (abs(rayDir.y) < EPSILON) {
        if (origin.y < minY || origin.y > maxY) {
            return invalidInterval();
        }
        return vec2(0.0, tMax);
    }

    float t0 = (minY - origin.y) / rayDir.y;
    float t1 = (maxY - origin.y) / rayDir.y;
    if (t0 > t1) {
        float swap = t0;
        t0 = t1;
        t1 = swap;
    }
    return clampInterval(vec2(t0, t1), tMax);
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
    vec2 verticalInterval = slabInterval(localOrigin, rayDir, WallBottomOffset, WallTopOffset, tMax);
    if (intervalLength(verticalInterval) <= 0.0) {
        return 0.0;
    }

    float outerRadius = zone.w + WallHalfThickness;
    float innerRadius = max(zone.w - WallHalfThickness, 0.0);

    vec2 outerInterval = intersectIntervals(cylinderInterval(localOrigin, rayDir, outerRadius, tMax), verticalInterval);
    float outerLength = intervalLength(outerInterval);
    if (outerLength <= 0.0) {
        return 0.0;
    }

    vec2 innerInterval = invalidInterval();
    float innerLength = 0.0;
    if (innerRadius > EPSILON) {
        innerInterval = intersectIntervals(cylinderInterval(localOrigin, rayDir, innerRadius, tMax), verticalInterval);
        innerLength = intervalLength(innerInterval);
    }

    float bandLength = max(0.0, outerLength - innerLength);
    if (bandLength <= 0.0) {
        return 0.0;
    }

    float sampleT = bandSampleT(outerInterval, innerInterval, innerRadius);
    vec3 worldSample = CameraPos + (rayDir * sampleT);
    vec3 localSample = worldSample - zone.xyz;

    float radialDistance = length(localSample.xz);
    float edgeFactor = 1.0 - smoothstep(0.0, WallHalfThickness, abs(radialDistance - zone.w));
    float verticalFactor = smoothstep(WallBottomOffset, WallBottomOffset + 18.0, localSample.y)
        * (1.0 - smoothstep(WallTopOffset - 26.0, WallTopOffset, localSample.y));

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
    density *= edgeFactor;
    density *= verticalFactor;
    return density;
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float depth = texture(Sampler0, uv).r;

    vec2 ndc = (uv * 2.0) - 1.0;
    vec4 clipFar = vec4(ndc, 1.0, 1.0);
    vec4 viewFar = InverseProjMat * clipFar;
    viewFar /= max(viewFar.w, EPSILON);
    vec3 rayDir = normalize((InverseViewRotMat * vec4(viewFar.xyz, 0.0)).xyz);

    float tMax;
    if (depth >= SKY_DEPTH_THRESHOLD) {
        tMax = FarPlaneDistance;
    } else {
        vec4 clipHit = vec4(ndc, (depth * 2.0) - 1.0, 1.0);
        vec4 viewHit = InverseProjMat * clipHit;
        viewHit /= max(viewHit.w, EPSILON);
        tMax = length(viewHit.xyz);
    }

    if (tMax <= EPSILON) {
        discard;
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

    float alpha = 1.0 - exp(-totalDensity);
    alpha = clamp(alpha, 0.0, 0.82);
    if (alpha <= 0.002) {
        discard;
    }

    vec3 fogTint = mix(FogColor.rgb, vec3(0.92, 0.95, 1.0), 0.18);
    fragColor = vec4(fogTint, alpha);
}
