"use strict";
const { Readable } = require("node:stream");
const { isApprovedMediaUrl, safeFilename, json } = require("./_shared");

async function fetchApprovedMedia(raw, init) {
  let url = raw;
  for (let i = 0; i < 4; i++) {
    if (!isApprovedMediaUrl(url)) throw new Error("Unapproved media host");
    const response = await fetch(url, { ...init, redirect: "manual" });
    if (response.status >= 300 && response.status < 400) {
      const location = response.headers.get("location");
      if (!location) throw new Error("Missing media redirect target");
      url = new URL(location, url).toString();
      continue;
    }
    return response;
  }
  throw new Error("Too many media redirects");
}

module.exports = async function handler(req, res) {
  if (req.method !== "GET" && req.method !== "HEAD")
    return json(res, 405, { status: "error", message: "Method not allowed." });
  const url = req.query.url;
  if (!isApprovedMediaUrl(url))
    return json(res, 400, { status: "error", message: "Invalid Instagram media URL." });

  const filename = safeFilename(req.query.filename);
  const isDownload = req.query.download === "1";
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 30000);
  res.on("close", () => controller.abort());
  try {
    const upstreamHeaders = {
      Accept: "video/*, image/*, application/octet-stream;q=0.9, */*;q=0.5",
      "User-Agent": "Mozilla/5.0 (compatible; AirInsta/1.0)"
    };
    // Browser seeking requires partial content to be passed through unmodified.
    const range = req.headers.range;
    if (typeof range === "string" && /^bytes=\d*-\d*$/.test(range)) upstreamHeaders.Range = range;

    const response = await fetchApprovedMedia(url, {
      method: req.method, signal: controller.signal, cache: "no-store", headers: upstreamHeaders
    });
    if (!response.ok && response.status !== 206)
      return json(res, 502, { status: "error", message: "Instagram media has expired or is not available. Resolve the post again." });

    const type = (response.headers.get("content-type") || "").split(";")[0].trim().toLowerCase();
    if (type && !type.startsWith("video/") && !type.startsWith("image/") &&
        type !== "application/octet-stream" && type !== "binary/octet-stream")
      return json(res, 502, { status: "error", message: "The media server returned an unexpected file type." });

    res.statusCode = response.status;
    res.setHeader("Content-Type", type || "application/octet-stream");
    res.setHeader("Cache-Control", "private, no-store");
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Content-Disposition", (isDownload ? "attachment" : "inline") + '; filename="' + filename + '"');
    for (const header of ["content-length", "content-range", "accept-ranges", "etag", "last-modified"]) {
      const value = response.headers.get(header);
      if (value) res.setHeader(header, value);
    }
    if (req.method === "HEAD" || !response.body) return res.end();
    const stream = Readable.fromWeb(response.body);
    stream.on("error", () => { if (!res.headersSent) res.statusCode = 502; res.destroy(); });
    stream.pipe(res);
  } catch (e) {
    if (!res.headersSent) {
      return json(res, 502, { status: "error", message:
        e.name === "AbortError" ? "Media download timed out. Try again." :
        "Unable to stream this media. Its signed URL may have expired." });
    }
    res.destroy();
  } finally { clearTimeout(timeout); }
};
module.exports.config = { maxDuration: 60 };
