"use strict";

const INSTAGRAM_HOSTS = new Set(["instagram.com", "www.instagram.com", "m.instagram.com", "instagr.am", "www.instagr.am"]);
const SUPPORTED_PATHS = new Set(["reel", "reels", "p", "tv", "stories", "share"]);

function parseSecureUrl(raw, maxLength) {
  if (typeof raw !== "string" || !raw || raw.length > (maxLength || 11000)) return null;
  try {
    const url = new URL(raw);
    if (url.protocol !== "https:" || url.port || url.username || url.password) return null;
    return url;
  } catch (_) { return null; }
}
function normalizeInstagramLink(raw) {
  const url = parseSecureUrl(raw, 2500);
  if (!url || !INSTAGRAM_HOSTS.has(url.hostname.toLowerCase())) return null;
  const segments = url.pathname.split("/").filter(Boolean);
  if (segments.length < 2 || !SUPPORTED_PATHS.has((segments[0] || "").toLowerCase())) return null;
  if (segments.some(s => s.length > 180)) return null;
  return "https://www.instagram.com/" + segments.map(encodeURIComponent).join("/") + "/";
}
function isApprovedMediaUrl(raw) {
  const url = parseSecureUrl(raw, 12000);
  if (!url) return false;
  const host = url.hostname.toLowerCase();
  // Never fetch arbitrary user-supplied hosts or private IP ranges.
  // Instagram media is typically delivered by signed fbcdn.net URLs.
  return host === "fbcdn.net" || host.endsWith(".fbcdn.net") ||
    host === "cdninstagram.com" || host.endsWith(".cdninstagram.com") ||
    host === "instagram.com" || host.endsWith(".instagram.com");
}
function safeFilename(raw) {
  return String(raw || "AirInsta_media")
    .replace(/[^a-zA-Z0-9_.-]/g, "_").replace(/\.{2,}/g, ".").replace(/^\.+/, "")
    .slice(0, 110) || "AirInsta_media";
}
function json(res, code, data) {
  res.statusCode = code;
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.end(JSON.stringify(data));
}
module.exports = { normalizeInstagramLink, isApprovedMediaUrl, safeFilename, json };
