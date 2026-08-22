/**
 * Marketplace compatibility for the current release line.
 *
 * Paper uses two version schemes. The legacy scheme (1.18.2 through the 1.21
 * line) is end-of-life upstream but still runs this plugin, so it stays
 * advertised. The current scheme is calendar-based (26.1, 26.2, ...), and it is
 * the only one PaperMC still marks SUPPORTED.
 *
 * The previous regex accepted `^1\.` only, so a calendar version could never
 * reach marketplace metadata — the plugin was filtered out of every Modrinth
 * and Hangar search for a currently-supported server. The jar itself is
 * version-agnostic: it compiles against spigot-api 1.8 and reaches newer Paper
 * APIs reflectively, and was verified running on Paper 26.2 under Java 25.
 */
const MIN_CALENDAR_MAJOR = 26;
export function isSupportedMinecraftVersion(value) {
  const match = /^(\d+)\.(\d+)(?:\.(\d+))?$/.exec(String(value || "").trim());
  if (!match) return false;
  const major = Number(match[1]);
  const minor = Number(match[2]);
  const patch = match[3] === undefined ? null : Number(match[3]);
  if (major === 1) {
    if (minor < 18 || minor > 21) return false;
    if (minor === 18) return patch !== null && patch >= 2;
    return true;
  }
  return major >= MIN_CALENDAR_MAJOR;
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
