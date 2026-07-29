/**
 * Marketplace compatibility for the current release line.
 *
 * NeoModeration's release matrix starts at Paper 1.18.2 and currently verifies
 * through the 1.21 line. Keep marketplace metadata inside that tested range.
 */
export function isSupportedMinecraftVersion(value) {
  const match = /^1\.(\d+)(?:\.(\d+))?$/.exec(String(value || "").trim());
  if (!match) return false;
  const minor = Number(match[1]);
  const patch = match[2] === undefined ? null : Number(match[2]);
  if (minor < 18 || minor > 21) return false;
  if (minor === 18) return patch !== null && patch >= 2;
  return true;
}

export function supportedMinecraftVersions(values) {
  return [...new Set((values || []).map(String).filter(isSupportedMinecraftVersion))];
}

export function hangarPaperVersions(platforms) {
  const versions = (platforms || []).flatMap((entry) => {
    if (typeof entry === "string") return [entry];
    if (!entry || typeof entry !== "object") return [];
    return Array.isArray(entry.subVersions) ? entry.subVersions : [entry.version];
  });
  return supportedMinecraftVersions(versions);
}
