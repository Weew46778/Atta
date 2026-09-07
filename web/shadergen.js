/*
 * PixelCraft Studio — RenderDragon-compatible shader pack builder.
 *
 * Bedrock (RenderDragon) shader packs are distributed as resource packs that add a
 * `textures/renderer/` folder with JSON material/deferred definitions plus compiled
 * `.bin`/`.shader` fragments. This generator produces a well-formed pack scaffold:
 * manifest + renderer JSON + Human-readable HLSL PBR sources whose parameters are
 * derived from the user's realistic settings.
 *
 * NOTE: true RenderDragon shaders require the HLSL to be compiled with the official
 * Bedrock shader toolchain for the target engine version. The generator emits the
 * source + config and, where possible, the JSON that drives the pipeline.
 */

function buildShaderManifest(name, uuid, minEngine) {
  return {
    format_version: 2,
    header: {
      name: name,
      description: "PixelCraft Studio — realistic RenderDragon shader pack",
      uuid: uuid,
      version: [1, 0, 0],
      min_engine_version: minEngine,
    },
    modules: [
      {
        type: "resources",
        uuid: uuid.replace(/(.{8})/, (m) => genUuid()),
        version: [1, 0, 0],
      },
    ],
  };
}

function genUuid() {
  // RFC4122 v4
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : ((r & 0x3) | 0x8);
    return v.toString(16);
  });
}

function buildShaderSettings(realism) {
  // realism in [0,1]; derive believable post-processing parameters.
  const t = Math.max(0, Math.min(1, realism));
  return {
    exposure: 0.9 + t * 0.55,
    sunIntensity: 1.1 + t * 1.0,
    fogDensity: 0.018 - t * 0.012,
    waterReflect: 0.3 + t * 0.5,
    ambientOcclusion: t,
    saturation: 1.02 + t * 0.18,
    contrast: 1.05 + t * 0.15,
    roughnessScale: 1 - t * 0.45,
    sunColor: [1.0, 0.96, 0.88],
    skyZenith: [0.12 + t * 0.06, 0.22 + t * 0.1, 0.42 + t * 0.16],
    skyHorizon: [0.6, 0.75 - t * 0.07, 0.92 - t * 0.05],
    n: Math.round(196605),
  };
}

function buildShaderFiles(name, realism, packIconCanvas) {
  const s = buildShaderSettings(realism);
  const fmt = (a) => a.map((v) => +v.toFixed(4));
  const j = (o) => JSON.stringify(o, null, 2);

  const files = [];
  const uuid = genUuid();

  files.push({ path: 'manifest.json', data: j(buildShaderManifest(name, uuid, [1, 26, 0])) });

  // per_frame.json — global constants baked from settings
  files.push({ path: 'textures/renderer/per_frame.json', data: j({
    headers: [
      { name: "FOG_COLOR", value: fmt(s.skyHorizon) },
      { name: "SKY_ZENITH", value: fmt(s.skyZenith) },
      { name: "SUN_DIRECTION", value: fmt([0.25, -0.72, 0.5]) },
      { name: "SUN_COLOR", value: fmt(s.sunColor) },
      { name: "SUN_INTENSITY", value: +s.sunIntensity.toFixed(3) },
      { name: "EXPOSURE", value: +s.exposure.toFixed(3) },
      { name: "FOG_DENSITY", value: +s.fogDensity.toFixed(5) },
      { name: "WATER_REFLECT", value: +s.waterReflect.toFixed(3) },
    ],
  }) });

  // per_object.json — per-block uniform defines
  files.push({ path: 'textures/renderer/per_object.json', data: j({
    headers: [
      { name: "AO_STRENGTH", value: +s.ambientOcclusion.toFixed(3) },
      { name: "ROUGHNESS_SCALE", value: +s.roughnessScale.toFixed(3) },
      { name: "SATURATION", value: +s.saturation.toFixed(3) },
      { name: "CONTRAST", value: +s.contrast.toFixed(3) },
    ],
  }) });

  // materials.json — pipeline material that consumes the PBR shader
  files.push({ path: 'textures/renderer/materials.json', data: j([
    {
      name: "pixelcraft_pbr",
      shader: "shaders/pixelcraft_pbr",
      passes: [
        { pass: "geometry", vertex: "VertexShader", pixel: "PixelShader" },
      ],
      uniforms: [
        { name: "SUN_DIRECTION", header: "SUN_DIRECTION" },
        { name: "AO_STRENGTH", header: "AO_STRENGTH" },
        { name: "ROUGHNESS_SCALE", header: "ROUGHNESS_SCALE" },
      ],
    },
  ]) });

  // shaders.json — declares the shader sources
  files.push({ path: 'textures/renderer/shaders.json', data: j([
    { name: "shaders/pixelcraft_pbr", vertex: "PixelCraftVertex", pixel: "PixelCraftPixel" },
  ]) });

  // deferred.json — toggle the deferred lighting pipeline used by RenderDragon
  files.push({ path: 'textures/renderer/deferred.json', data: j({
    version: 1,
    deferred_lighting: true,
    sky: { zenith: fmt(s.skyZenith), horizon: fmt(s.skyHorizon) },
    fog: { density: +s.fogDensity.toFixed(5), color: fmt(s.skyHorizon) },
    water: { reflection_strength: +s.waterReflect.toFixed(3) },
  }) });

  // Human-readable HLSL PBR source (compiled by the Bedrock toolchain at build time)
  files.push({ path: 'shaders/pixelcraft_pbr.hlsl', data: PBR_HLSL });

  return { files, settings: s };
}

const PBR_HLSL = `// PixelCraft Studio — Realistic PBR shader (RenderDragon source)
// Compiled by the official Bedrock RenderDragon shader toolchain for the target engine.
#include <common/common.h>

struct FrameUniforms {
  float3 SunDirection;
  float3 SunColor;
  float  SunIntensity;
  float  Exposure;
  float  FogDensity;
  float3 FogColor;
  float3 SkyZenith;
  float3 SkyHorizon;
  float  WaterReflection;
};

struct ObjectUniforms {
  float  AOStrength;
  float  RoughnessScale;
  float  Saturation;
  float  Contrast;
};

Texture2D DiffuseMap   : register(t0);
SamplerState LinearSampler : register(s0);

struct VSInput {
  float3 Position : POSITION;
  float3 Normal   : NORMAL;
  float2 UV       : TEXCOORD0;
};

struct PSInput {
  float4 Position : SV_POSITION;
  float3 WorldNormal : NORMAL;
  float2 UV       : TEXCOORD0;
  float3 WorldPos : TEXCOORD1;
};

PSInput PixelCraftVertex(VSInput input) {
  PSInput out;
  out.Position = mul(g_WorldViewProj, float4(input.Position, 1.0));
  out.WorldNormal = normalize(mul((float3x3)g_World, input.Normal));
  out.UV = input.UV;
  out.WorldPos = mul((float3x3)g_World, input.Position) + g_WorldTranslation;
  return out;
}

float3 PixelCraftPixel(PSInput input) : SV_TARGET {
  float4 albedo = DiffuseMap.Sample(LinearSampler, input.UV);
  float3 N = normalize(input.WorldNormal);
  float3 L = normalize(-g_SUN_DIRECTION.xyz);
  float NdotL = saturate(dot(N, L));

  // Saturation / contrast grading
  float luma = dot(albedo.rgb, float3(0.2126, 0.7152, 0.0722));
  float3 graded = lerp(float3(luma, luma, luma), albedo.rgb, g_SATURATION);
  graded = (graded - 0.5) * g_CONTRAST + 0.5;

  // Sun + ambient (AO)
  float3 direct = g_SUN_COLOR.rgb * g_SUN_INTENSITY * NdotL;
  float3 ambient = lerp(0.05, 0.35, saturate(N.y + 0.5)) * g_AO_STRENGTH;
  float3 color = graded * (ambient + direct);

  // Simple specular from roughness
  float3 H = normalize(L + float3(0.0, 0.0, 1.0));
  float spec = pow(saturate(dot(N, H)), 32.0) * g_ROUGHNESS_SCALE;
  color += spec * g_SUN_COLOR.rgb * 0.4;

  // Exposure + gamma
  color = 1.0 - exp(-color * g_EXPOSURE);
  color = pow(saturate(color), rcp(2.2));

  // Fog
  float dist = length(input.WorldPos - g_WorldTranslation);
  float fog = 1.0 - exp(-dist * g_FOG_DENSITY);
  return float4(lerp(color, g_FOG_COLOR.rgb, fog), albedo.a);
}
`;

// Expose to browsers
window.ShaderGen = { buildShaderFiles, buildShaderSettings, genUuid };
