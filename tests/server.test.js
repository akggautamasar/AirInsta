"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { Writable } = require("node:stream");
const { once } = require("node:events");
const { normalizeInstagramLink, isApprovedMediaUrl, safeFilename } = require("../api/_shared");
const resolve = require("../api/resolve");
const media = require("../api/media");

function responseRecorder() {
  const chunks = [], headers = {};
  const res = new Writable({ write(chunk, _encoding, callback) { chunks.push(Buffer.from(chunk)); callback(); } });
  res.statusCode = 200;
  res.setHeader = (name, value) => { headers[name.toLowerCase()] = String(value); };
  res.getHeader = name => headers[name.toLowerCase()];
  res.bodyText = () => Buffer.concat(chunks).toString("utf8");
  return res;
}
async function call(handler, request) {
  const res = responseRecorder();
  const finished = once(res, "finish");
  await handler(request, res);
  await finished;
  return res;
}

test("only real Instagram post links are accepted", () => {
  assert.equal(normalizeInstagramLink("https://www.instagram.com/reel/AbC123/?utm_source=test"), "https://www.instagram.com/reel/AbC123/");
  for (const value of [
    "http://instagram.com/reel/x", "https://instagram.com.evil.example/reel/x",
    "https://evil.example/?url=https://instagram.com/reel/x",
    "https://someone@instagram.com/reel/x", "https://instagram.com:444/reel/x",
    "https://instagram.com/account", "javascript:alert(1)"
  ]) assert.equal(normalizeInstagramLink(value), null, value);
});

test("media host validation blocks lookalikes, credentials and private addresses", () => {
  assert.equal(isApprovedMediaUrl("https://instagram.fna.fbcdn.net/video.mp4?sig=test"), true);
  for (const value of [
    "https://fbcdn.net.evil.example/video.mp4", "https://127.0.0.1/video.mp4",
    "https://internal.local/video.mp4", "https://user@fbcdn.net/video.mp4",
    "http://fbcdn.net/video.mp4", "https://fbcdn.net:8443/video.mp4"
  ]) assert.equal(isApprovedMediaUrl(value), false, value);
  const name = safeFilename("../../x.mp4");
  assert.ok(name.endsWith(".mp4") && !name.includes("/") && !name.includes("..") && !name.startsWith("."));
});

test("resolver validates input before contacting the extractor", async () => {
  const original = global.fetch;
  let called = false;
  global.fetch = async () => { called = true; throw new Error("unexpected"); };
  try {
    const res = await call(resolve, { method: "GET", query: { url: "https://example.com/" } });
    assert.equal(res.statusCode, 400); assert.equal(called, false);
  } finally { global.fetch = original; }
});

test("resolver returns the extractor's public media response", async () => {
  const original = global.fetch;
  global.fetch = async url => {
    assert.equal(new URL(url).searchParams.get("url"), "https://www.instagram.com/reel/test/");
    return new Response(JSON.stringify({ status: "success", result: [{ video_url: "https://fbcdn.net/video.mp4" }] }), { status: 200 });
  };
  try {
    const res = await call(resolve, { method: "GET", query: { url: "https://instagram.com/reel/test/" } });
    assert.equal(res.statusCode, 200);
    assert.equal(JSON.parse(res.bodyText()).result[0].video_url, "https://fbcdn.net/video.mp4");
  } finally { global.fetch = original; }
});

test("media relay forwards video ranges and streams partial content", async () => {
  const original = global.fetch;
  global.fetch = async (_url, init) => {
    assert.equal(init.headers.Range, "bytes=5-9");
    return new Response("hello", { status: 206, headers: {
      "content-type": "video/mp4", "content-range": "bytes 5-9/20", "accept-ranges": "bytes"
    } });
  };
  try {
    const res = await call(media, { method: "GET", query: { url: "https://fbcdn.net/video.mp4" }, headers: { range: "bytes=5-9" } });
    assert.equal(res.statusCode, 206); assert.equal(res.getHeader("content-range"), "bytes 5-9/20");
    assert.equal(res.bodyText(), "hello");
  } finally { global.fetch = original; }
});

test("media relay refuses a redirect to an unapproved host", async () => {
  const original = global.fetch;
  let requests = 0;
  global.fetch = async () => {
    requests++;
    return new Response(null, { status: 302, headers: { location: "https://evil.example/private" } });
  };
  try {
    const res = await call(media, { method: "GET", query: { url: "https://fbcdn.net/video.mp4" }, headers: {} });
    assert.equal(res.statusCode, 502); assert.equal(requests, 1);
  } finally { global.fetch = original; }
});

