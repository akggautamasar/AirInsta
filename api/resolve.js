"use strict";
const { normalizeInstagramLink, json } = require("./_shared");

// Keep the extractor on the server, so the browser never depends on its CORS policy.
module.exports = async function handler(req, res) {
  if (req.method !== "GET") return json(res, 405, { status: "error", message: "Method not allowed." });
  const link = normalizeInstagramLink(req.query.url);
  if (!link) return json(res, 400, { status: "error", message: "Provide a valid Instagram reel, post, carousel or story URL." });

  let upstreamBase;
  try {
    upstreamBase = new URL(process.env.AIRINSTA_WORKER_URL || "https://airig.worksbeyondworks.workers.dev/");
    if (upstreamBase.protocol !== "https:") throw Error("Secure worker URL required");
  } catch (_) { return json(res, 500, { status: "error", message: "Extractor configuration is invalid." }); }

  upstreamBase.searchParams.set("url", link);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 24000);
  try {
    const response = await fetch(upstreamBase, {
      method: "GET", signal: controller.signal, redirect: "error",
      headers: { Accept: "application/json", "User-Agent": "AirInsta/1.0" },
      cache: "no-store"
    });
    if (!response.ok) {
      return json(res, 502, { status: "error", message: "The Instagram extractor is temporarily unavailable. Please retry." });
    }
    const declared = Number(response.headers.get("content-length") || 0);
    if (declared > 4 * 1024 * 1024) return json(res, 502, { status: "error", message: "The extractor returned too much data." });
    const raw = await response.text();
    if (raw.length > 4 * 1024 * 1024) return json(res, 502, { status: "error", message: "The extractor returned too much data." });
    let data;
    try { data = JSON.parse(raw); }
    catch (_) { return json(res, 502, { status: "error", message: "The extractor returned an invalid response." }); }
    if (data && (data.status === "error" || data.status === "failed" || data.success === false)) {
      return json(res, 422, { status: "error", message: String(data.message || data.error || "The post could not be resolved.").slice(0, 300) });
    }
    return json(res, 200, data);
  } catch (e) {
    const message = e.name === "AbortError"
      ? "The extractor timed out. Please try again."
      : "Unable to reach the extractor. Please try again.";
    return json(res, 502, { status: "error", message });
  } finally { clearTimeout(timeout); }
};
module.exports.config = { maxDuration: 30 };
