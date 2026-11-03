(function () {
  "use strict";
  var $ = function (id) { return document.getElementById(id); };
  var state = { items: [], meta: {}, url: "", active: 0, busy: false, zipBusy: false, captionOpen: false,
    history: readStore("airinsta-history", []), saved: readStore("airinsta-saved", []) };
  var toastTimer = null;
  var installPrompt = null;
  $("currentYear").textContent = String(new Date().getFullYear());

  function readStore(key, fallback) {
    try { var parsed = JSON.parse(localStorage.getItem(key)); return Array.isArray(parsed) ? parsed : fallback; }
    catch (_) { return fallback; }
  }
  function writeStore(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); } catch (_) {} }
  function icon(name, cls) {
    var svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("class", "ico" + (cls ? " " + cls : ""));
    var use = document.createElementNS("http://www.w3.org/2000/svg", "use");
    use.setAttribute("href", "#i-" + name);
    svg.appendChild(use);
    return svg;
  }
  function textNode(tag, className, txt) {
    var el = document.createElement(tag); if (className) el.className = className;
    el.textContent = txt; return el;
  }
  function toast(message) {
    var el = $("toast"); el.textContent = message; el.classList.add("visible");
    clearTimeout(toastTimer); toastTimer = setTimeout(function () { el.classList.remove("visible"); }, 3300);
  }
  function error(message) { $("errorText").textContent = message; $("errorBox").hidden = false; }
  function hideError() { $("errorBox").hidden = true; }
  function validateLink(raw) {
    var s = String(raw || "").trim();
    if (!s) throw new Error("Paste an Instagram reel or post link to continue.");
    var match = s.match(/https?:\/\/[^\s]+/i);
    if (match) s = match[0]; else if (/^(?:www\.)?(?:instagram\.com|instagr\.am)\//i.test(s)) s = "https://" + s;
    var u;
    try { u = new URL(s); } catch (_) { throw new Error("That doesn't look like a valid Instagram URL."); }
    var hostname = u.hostname.toLowerCase();
    if (u.protocol !== "https:" || u.port || u.username || u.password ||
      !["instagram.com", "www.instagram.com", "m.instagram.com", "instagr.am", "www.instagr.am"].includes(hostname))
      throw new Error("Only public Instagram links are supported.");
    var segments = u.pathname.split("/").filter(Boolean);
    if (!["reel", "reels", "p", "tv", "stories", "share"].includes((segments[0] || "").toLowerCase()) || segments.length < 2)
      throw new Error("Use the link to an Instagram reel, post, carousel or public story.");
    return "https://www.instagram.com/" + segments.map(encodeURIComponent).join("/") + "/";
  }
  function safeMediaUrl(url) {
    if (typeof url !== "string" || url.length > 10000) return "";
    try {
      var u = new URL(url);
      var host = u.hostname.toLowerCase();
      var allowed = ["fbcdn.net", "cdninstagram.com", "instagram.com"].some(function (domain) {
        return host === domain || host.endsWith("." + domain);
      });
      return u.protocol === "https:" && !u.username && !u.password && !u.port && allowed ? u.toString() : "";
    } catch (_) { return ""; }
  }
  function firstString() {
    for (var i = 0; i < arguments.length; i++) {
      var x = arguments[i];
      if (typeof x === "string" && /^https:\/\//i.test(x)) return x;
    }
    return "";
  }
  function normalizePayload(payload) {
    var raw = payload && (payload.result || payload.data || payload.media || payload.items || payload);
    var meta = { username: "", caption: "", id: "", isAlbum: false };
    var items = [], seen = new Set(), explored = new WeakSet();
    function put(item) {
      var url = safeMediaUrl(item.url);
      if (!url || seen.has(url)) return;
      seen.add(url);
      items.push({ url: url, poster: safeMediaUrl(item.poster) || "", type: item.type === "video" ? "video" : "image" });
    }
    function crawl(node, depth) {
      if (!node || depth > 9 || items.length >= 100) return;
      if (Array.isArray(node)) { node.forEach(function (x) { crawl(x, depth + 1); }); return; }
      if (typeof node !== "object" || explored.has(node)) return;
      explored.add(node);
      if (!meta.username) meta.username = String(node.username || (node.owner && node.owner.username) || (node.user && node.user.username) || "");
      if (!meta.caption) {
        var cap = node.caption || node.title || node.text;
        if (typeof cap === "string") meta.caption = cap;
        else if (cap && typeof cap.text === "string") meta.caption = cap.text;
      }
      if (!meta.id && (node.id || node.shortcode)) meta.id = String(node.id || node.shortcode);
      if (node.is_album || node.is_carousel) meta.isAlbum = true;
      var versions = Array.isArray(node.video_versions) ? node.video_versions : [];
      var candidate = node.image_versions2 && Array.isArray(node.image_versions2.candidates)
        ? node.image_versions2.candidates[0] : null;
      var video = firstString(node.video_url, node.play_url, node.videoUrl,
        node.video && typeof node.video === "object" ? node.video.url : node.video,
        versions[0] && versions[0].url);
      var image = firstString(node.image_url, node.display_url, node.imageUrl, node.display_src,
        node.image && typeof node.image === "object" ? node.image.url : node.image,
        typeof node.image === "string" ? node.image : "", candidate && candidate.url);
      var poster = firstString(node.video_img, node.thumbnail_url, node.thumbnail, node.cover_url, image);
      var direct = firstString(node.download_url, node.media_url, node.url);
      if (video) put({ url: video, poster: poster, type: "video" });
      else if (image) put({ url: image, poster: poster, type: "image" });
      else if (direct && !node.username && !node.caption) {
        put({ url: direct, poster: poster,
          type: (node.is_video || /\.mp4(?:$|\?)/i.test(direct)) ? "video" : "image" });
      }
      ["result", "data", "items", "media", "medias", "carousel_media", "resources",
        "slides", "children", "nodes", "edges", "node", "edge_sidecar_to_children",
        "edge_sidecar", "carousel", "attachments"].forEach(function (key) {
        if (node[key]) crawl(node[key], depth + 1);
      });
    }
    crawl(raw, 0);
    if (!meta.username && payload && typeof payload.username === "string") meta.username = payload.username;
    if (!meta.caption && payload && typeof payload.caption === "string") meta.caption = payload.caption;
    return { items: items, meta: meta };
  }
  function setBusy(busy) {
    state.busy = busy; $("fetchBtn").disabled = busy; $("linkInput").disabled = busy;
    $("fetchBtnText").textContent = busy ? "Finding your media..." : "Get media";
    $("fetchBtnIcon").replaceChildren(document.createElementNS("http://www.w3.org/2000/svg", "use"));
    $("fetchBtnIcon").firstChild.setAttribute("href", busy ? "#i-loader" : "#i-arrow");
    $("fetchBtnIcon").classList.toggle("spinning", busy);
  }
  async function resolve(raw) {
    hideError();
    var link;
    try { link = validateLink(raw); } catch (e) { error(e.message); return; }
    if (state.busy) return;
    state.url = link; $("linkInput").value = link; $("clearBtn").hidden = false;
    setView("studio");
    setBusy(true);
    $("resultSection").hidden = true;
    var controller = new AbortController(), timeout = setTimeout(function () { controller.abort(); }, 28000);
    try {
      var response = await fetch("/api/resolve?url=" + encodeURIComponent(link), { signal: controller.signal, headers: { Accept: "application/json" }, cache: "no-store" });
      var payload = await response.json().catch(function () { return null; });
      if (!response.ok || !payload || payload.status === "error" || payload.status === "failed") {
        var message = payload && (payload.message || payload.error);
        throw new Error(typeof message === "string" ? message : "The media couldn't be fetched. Try another public post.");
      }
      var parsed = normalizePayload(payload);
      if (!parsed.items.length) throw new Error("No downloadable media was returned. This post may be private, expired or unsupported by the extractor.");
      state.items = parsed.items; state.meta = parsed.meta; state.active = 0; state.captionOpen = false;
      saveHistory({ url: link, username: parsed.meta.username, type: contentKind(parsed.items), date: Date.now() });
      renderResult();
      toast("Your media is ready ✨");
      $("resultSection").scrollIntoView({ behavior: "smooth", block: "start" });
    } catch (e) {
      if (e.name === "AbortError") error("This request timed out. Please try again.");
      else error(e.message || "Something went wrong. Please try again.");
    } finally { clearTimeout(timeout); setBusy(false); }
  }
  function contentKind(items) {
    if (items.length > 1) return "Carousel";
    return items[0] && items[0].type === "video" ? "Reel / video" : "Photo / post";
  }
  function renderResult() {
    var username = state.meta.username.replace(/^@/, "") || "Instagram creator";
    $("creatorName").textContent = "@" + username.replace(/^@/, "");
    $("creatorAvatar").textContent = username.slice(0, 2).toUpperCase();
    $("postType").textContent = contentKind(state.items) + " · Public media";
    $("mediaCount").textContent = state.items.length + (state.items.length === 1 ? " item" : " items");
    $("postCaption").textContent = state.meta.caption || "No caption provided for this post.";
    $("postCaption").classList.remove("expanded");
    $("captionExpand").hidden = !state.meta.caption || state.meta.caption.length <= 180;
    $("captionExpand").textContent = "Show more";
    $("openOriginal").href = state.url;
    $("downloadAll").hidden = state.items.length < 2;
    $("allItemCount").textContent = state.items.length + " files";
    $("downloadProgress").hidden = true;
    $("resultSection").hidden = false;
    updateSaveButton(); showMedia(0);
  }
  function proxyUrl(item, download, filename) {
    return "/api/media?url=" + encodeURIComponent(item.url) +
      (download ? "&download=1&filename=" + encodeURIComponent(filename || "airinsta-media") : "");
  }
  function imageWithFallback(src, item, alt) {
    var el = document.createElement("img");
    el.alt = alt || "Instagram media";
    el.referrerPolicy = "no-referrer"; el.loading = "lazy"; el.src = src;
    el.addEventListener("error", function () {
      if (el.dataset.proxied) { el.style.display = "none"; return; }
      // A video thumbnail is an image URL, not the video's MP4 URL.
      el.dataset.proxied = "1"; el.src = proxyUrl({ url: src }, false);
    });
    return el;
  }
  function showMedia(index) {
    if (!state.items.length) return;
    state.active = (index + state.items.length) % state.items.length;
    var item = state.items[state.active], stage = $("mediaStage");
    stage.replaceChildren();
    stage.style.removeProperty("--media-ratio");
    function sizeStage(width, height, element) {
      if (stage.contains(element) && width > 0 && height > 0) {
        stage.style.setProperty("--media-ratio", String(width / height));
      }
    }
    if (item.type === "video") {
      var video = document.createElement("video");
      video.controls = true; video.playsInline = true; video.preload = "metadata";
      video.referrerPolicy = "no-referrer"; video.src = item.url;
      video.setAttribute("aria-label", "Instagram video " + (state.active + 1));
      // Some extractors return a square, pre-cropped cover for a portrait reel.
      // Show the video's own first frame instead of that misleading cover.
      video.addEventListener("loadedmetadata", function () {
        sizeStage(video.videoWidth, video.videoHeight, video);
      });
      video.addEventListener("error", function () {
        if (video.dataset.proxied) { toast("This video can't be played right now. Try downloading it."); return; }
        video.dataset.proxied = "1"; video.src = proxyUrl(item, false); video.load();
      });
      stage.appendChild(video);
    } else {
      var photo = imageWithFallback(item.url, item, "Instagram photo " + (state.active + 1));
      photo.addEventListener("load", function () {
        sizeStage(photo.naturalWidth, photo.naturalHeight, photo);
      });
      stage.appendChild(photo);
      if (photo.complete) sizeStage(photo.naturalWidth, photo.naturalHeight, photo);
    }
    if (state.items.length > 1) stage.appendChild(textNode("span", "media-counter", (state.active + 1) + " / " + state.items.length));
    $("mediaPosition").textContent = (state.active + 1) + " of " + state.items.length;
    $("activeMediaType").textContent = item.type === "video" ? "Video" : "Photo";
    $("currentKind").textContent = item.type === "video" ? "video" : "photo";
    $("prevMedia").disabled = state.items.length < 2;
    $("nextMedia").disabled = state.items.length < 2;
    renderThumbnails();
  }
  function renderThumbnails() {
    var strip = $("thumbnailStrip"); strip.replaceChildren(); strip.hidden = state.items.length < 2;
    state.items.forEach(function (item, index) {
      var thumb = document.createElement("button"); thumb.type = "button";
      thumb.className = "thumb-item" + (index === state.active ? " selected" : "");
      thumb.setAttribute("aria-label", "View media " + (index + 1));
      thumb.setAttribute("aria-pressed", String(index === state.active));
      if (item.poster || item.type === "image") {
        thumb.appendChild(imageWithFallback(item.poster || item.url, item, "Slide " + (index + 1)));
      } else {
        var sym = textNode("span", "stage-placeholder", ""); sym.appendChild(icon("play")); thumb.appendChild(sym);
      }
      if (item.type === "video") {
        var badge = textNode("span", "thumb-video", ""); badge.appendChild(icon("play")); thumb.appendChild(badge);
      }
      thumb.addEventListener("click", function () { showMedia(index); });
      strip.appendChild(thumb);
    });
  }
  function filename(item, index) {
    var shortId = state.url.match(/\/(?:reel|p|tv|stories)\/([^/]+)/);
    var id = (shortId ? shortId[1] : "post").replace(/[^a-z0-9_-]/gi, "").slice(0, 40);
    var name = (state.meta.username || "instagram").replace(/[^a-z0-9_-]/gi, "").slice(0, 24) || "instagram";
    var ext = item.type === "video" ? "mp4" : "jpg";
    try {
      var found = new URL(item.url).pathname.match(/\.(mp4|mov|jpe?g|png|webp)$/i);
      if (found) ext = found[1].toLowerCase();
    } catch (_) {}
    return "AirInsta_" + name + "_" + id + "_" + String(index + 1).padStart(2, "0") + "." + ext;
  }
  function triggerDownload(url, name, isBlob) {
    var link = document.createElement("a"); link.href = url; link.download = name;
    document.body.appendChild(link); link.click(); link.remove();
    if (isBlob) setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
  }
  function downloadSelected() {
    if (!state.items.length) return;
    var item = state.items[state.active], name = filename(item, state.active);
    triggerDownload(proxyUrl(item, true, name), name, false);
    toast("Starting download...");
  }
  function crcTable() {
    var table = [];
    for (var n = 0; n < 256; n++) {
      var c = n;
      for (var j = 0; j < 8; j++) c = (c & 1) ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      table[n] = c >>> 0;
    }
    return table;
  }
  var CRC_TABLE = crcTable();
  function crc32(bytes) {
    var c = 0xffffffff;
    for (var i = 0; i < bytes.length; i++) c = CRC_TABLE[(c ^ bytes[i]) & 255] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
  }
  function put16(buf, off, value) { buf.setUint16(off, value, true); }
  function put32(buf, off, value) { buf.setUint32(off, value >>> 0, true); }
  function createZip(files) {
    var parts = [], central = [], offset = 0, centralSize = 0;
    files.forEach(function (f) {
      var filenameBytes = new TextEncoder().encode(f.name), size = f.bytes.byteLength, crc = crc32(f.bytes);
      var local = new Uint8Array(30 + filenameBytes.length), h = new DataView(local.buffer);
      put32(h, 0, 0x04034b50); put16(h, 4, 20); put16(h, 6, 0x800); put16(h, 8, 0);
      put32(h, 14, crc); put32(h, 18, size); put32(h, 22, size); put16(h, 26, filenameBytes.length);
      local.set(filenameBytes, 30); parts.push(local, f.bytes);
      var record = new Uint8Array(46 + filenameBytes.length), cd = new DataView(record.buffer);
      put32(cd, 0, 0x02014b50); put16(cd, 4, 20); put16(cd, 6, 20); put16(cd, 8, 0x800);
      put32(cd, 16, crc); put32(cd, 20, size); put32(cd, 24, size); put16(cd, 28, filenameBytes.length);
      put32(cd, 42, offset); record.set(filenameBytes, 46); central.push(record);
      offset += local.length + size; centralSize += record.length;
    });
    var end = new Uint8Array(22), e = new DataView(end.buffer);
    put32(e, 0, 0x06054b50); put16(e, 8, files.length); put16(e, 10, files.length);
    put32(e, 12, centralSize); put32(e, 16, offset);
    return new Blob(parts.concat(central, [end]), { type: "application/zip" });
  }
  async function downloadAll() {
    if (state.zipBusy) return;
    if (state.items.length < 2) { downloadSelected(); return; }
    state.zipBusy = true; $("downloadAll").disabled = true; $("downloadProgress").hidden = false;
    var files = [], totalSize = 0, maxSize = 220 * 1024 * 1024;
    try {
      for (var i = 0; i < state.items.length; i++) {
        $("downloadProgress").textContent = "Preparing file " + (i + 1) + " of " + state.items.length + "...";
        var item = state.items[i], res = await fetch(proxyUrl(item, false), { cache: "no-store" });
        if (!res.ok) throw new Error("A file could not be retrieved (" + res.status + "). Try individual downloads.");
        var declaredSize = Number(res.headers.get("content-length") || "0");
        if (totalSize + declaredSize > maxSize) throw new Error("This carousel is too large for a browser ZIP. Download items individually.");
        var bytes = new Uint8Array(await res.arrayBuffer());
        totalSize += bytes.byteLength;
        if (totalSize > maxSize) throw new Error("This carousel is too large for a browser ZIP. Download items individually.");
        files.push({ name: filename(item, i), bytes: bytes });
      }
      $("downloadProgress").textContent = "Creating your ZIP archive...";
      var blob = createZip(files), id = state.url.match(/\/(?:reel|p|tv)\/([^/]+)/);
      var name = "AirInsta_" + (id ? id[1].replace(/[^a-z0-9_-]/gi, "") : "collection") + ".zip";
      triggerDownload(URL.createObjectURL(blob), name, true);
      toast("Your ZIP is ready!");
    } catch (e) { error(e.message || "Unable to package this collection."); }
    finally { state.zipBusy = false; $("downloadAll").disabled = false; $("downloadProgress").hidden = true; }
  }
  function saveHistory(entry) {
    state.history = [entry].concat(state.history.filter(function (x) { return x.url !== entry.url; })).slice(0, 20);
    writeStore("airinsta-history", state.history); updateCounts();
  }
  function updateCounts() {
    $("recentCount").textContent = state.history.length; $("savedCount").textContent = state.saved.length;
  }
  function updateSaveButton() {
    var saved = state.saved.some(function (s) { return s.url === state.url; });
    $("saveBtn").classList.toggle("is-saved", saved);
    $("saveBtn").setAttribute("aria-label", saved ? "Remove saved link" : "Save link");
    $("saveBtn").title = saved ? "Remove saved link" : "Save link";
  }
  function toggleSaved() {
    if (!state.url) return;
    var index = state.saved.findIndex(function (s) { return s.url === state.url; });
    if (index === -1) {
      state.saved.unshift({ url: state.url, username: state.meta.username, type: contentKind(state.items), date: Date.now() });
      state.saved = state.saved.slice(0, 80); toast("Added to saved links");
    } else { state.saved.splice(index, 1); toast("Removed from saved links"); }
    writeStore("airinsta-saved", state.saved); updateCounts(); updateSaveButton(); renderLibrary("saved");
  }
  function renderLibrary(which) {
    var el = $(which === "history" ? "historyItems" : "savedItems");
    var data = which === "history" ? state.history : state.saved;
    el.replaceChildren();
    if (!data.length) {
      var empty = textNode("div", "library-empty", "");
      empty.appendChild(icon(which === "history" ? "clock" : "bookmark"));
      empty.appendChild(textNode("h3", "", which === "history" ? "No recent links yet" : "Nothing saved just yet"));
      empty.appendChild(textNode("p", "", "Paste an Instagram link in Studio to get started."));
      var start = textNode("button", "", "Explore Studio"); start.type = "button";
      start.addEventListener("click", function () { setView("studio"); }); empty.appendChild(start); el.appendChild(empty);
      return;
    }
    data.forEach(function (entry) {
      var card = textNode("article", "library-item", "");
      var label = textNode("span", "library-item-icon", ""); label.appendChild(icon(entry.type === "Carousel" ? "grid" : "link")); card.appendChild(label);
      var text = textNode("div", "library-item-text", "");
      text.appendChild(textNode("strong", "", entry.username ? "@" + entry.username.replace(/^@/, "") : entry.type || "Instagram post"));
      text.appendChild(textNode("p", "", entry.url)); card.appendChild(text);
      var actions = textNode("div", "library-item-actions", "");
      var open = textNode("button", "", ""); open.type = "button"; open.appendChild(icon("arrow")); open.appendChild(textNode("span", "", "Open"));
      open.setAttribute("aria-label", "Open Instagram link");
      open.addEventListener("click", function () { $("linkInput").value = entry.url; resolve(entry.url); });
      actions.appendChild(open);
      var remove = textNode("button", "", ""); remove.type = "button"; remove.appendChild(icon("trash"));
      remove.setAttribute("aria-label", "Remove link");
      remove.addEventListener("click", function () {
        if (which === "history") { state.history = state.history.filter(function (x) { return x.url !== entry.url; }); writeStore("airinsta-history", state.history); }
        else { state.saved = state.saved.filter(function (x) { return x.url !== entry.url; }); writeStore("airinsta-saved", state.saved); updateSaveButton(); }
        updateCounts(); renderLibrary(which);
      });
      actions.appendChild(remove); card.appendChild(actions); el.appendChild(card);
    });
  }
  function setView(view) {
    if (!["studio", "history", "saved"].includes(view)) view = "studio";
    $("viewStudio").hidden = view !== "studio"; $("viewHistory").hidden = view !== "history"; $("viewSaved").hidden = view !== "saved";
    document.querySelectorAll("[data-view]").forEach(function (button) {
      var isActive = button.dataset.view === view;
      button.classList.toggle("active", isActive);
      if (isActive) button.setAttribute("aria-current", "page"); else button.removeAttribute("aria-current");
    });
    $("breadcrumbCurrent").textContent = view === "studio" ? "Studio" : view === "history" ? "Recent links" : "Saved links";
    if (view !== "studio") renderLibrary(view);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  $("linkForm").addEventListener("submit", function (event) { event.preventDefault(); resolve($("linkInput").value); });
  $("pasteBtn").addEventListener("click", async function () {
    try {
      if (!navigator.clipboard || !navigator.clipboard.readText) throw new Error("not available");
      var clip = await navigator.clipboard.readText();
      $("linkInput").value = clip.trim(); $("clearBtn").hidden = !clip.trim();
      $("linkInput").focus(); hideError(); toast("Link pasted!");
    } catch (_) { $("linkInput").focus(); toast("Use your keyboard's Paste option here."); }
  });
  $("linkInput").addEventListener("input", function () { $("clearBtn").hidden = !$("linkInput").value; hideError(); });
  $("clearBtn").addEventListener("click", function () { $("linkInput").value = ""; $("clearBtn").hidden = true; $("linkInput").focus(); hideError(); });
  $("dismissError").addEventListener("click", hideError);
  $("prevMedia").addEventListener("click", function () { showMedia(state.active - 1); });
  $("nextMedia").addEventListener("click", function () { showMedia(state.active + 1); });
  $("downloadCurrent").addEventListener("click", downloadSelected);
  $("downloadAll").addEventListener("click", downloadAll);
  $("saveBtn").addEventListener("click", toggleSaved);
  $("captionExpand").addEventListener("click", function () {
    state.captionOpen = !state.captionOpen;
    $("postCaption").classList.toggle("expanded", state.captionOpen);
    $("captionExpand").textContent = state.captionOpen ? "Show less" : "Show more";
  });
  $("shareBtn").addEventListener("click", async function () {
    if (!state.url) return;
    try {
      if (navigator.share) await navigator.share({ title: "Instagram post", url: state.url });
      else { await navigator.clipboard.writeText(state.url); toast("Link copied!"); }
    } catch (e) { if (e.name !== "AbortError") toast("Unable to share this link."); }
  });
  $("clearHistory").addEventListener("click", function () {
    if (!state.history.length) return;
    if (!window.confirm("Remove all recent links from this browser?")) return;
    state.history = []; writeStore("airinsta-history", []); updateCounts(); renderLibrary("history"); toast("History cleared");
  });
  document.querySelectorAll("[data-view]").forEach(function (button) {
    button.addEventListener("click", function () { setView(button.dataset.view); });
  });
  var savedTheme = null;
  try { savedTheme = localStorage.getItem("airinsta-theme"); } catch (_) {}
  document.documentElement.dataset.theme = savedTheme === "light" ? "light" : "dark";
  function setThemeIcon() {
    $("themeToggle").replaceChildren(icon(document.documentElement.dataset.theme === "light" ? "moon" : "sun"));
  }
  setThemeIcon();
  $("themeToggle").addEventListener("click", function () {
    var next = document.documentElement.dataset.theme === "light" ? "dark" : "light";
    document.documentElement.dataset.theme = next;
    try { localStorage.setItem("airinsta-theme", next); } catch (_) {}
    setThemeIcon();
  });
  window.addEventListener("beforeinstallprompt", function (event) {
    event.preventDefault(); installPrompt = event; $("installBtn").hidden = false;
  });
  $("installBtn").addEventListener("click", async function () {
    if (!installPrompt) return;
    installPrompt.prompt();
    await installPrompt.userChoice; installPrompt = null; $("installBtn").hidden = true;
  });
  window.addEventListener("appinstalled", function () { $("installBtn").hidden = true; toast("AirInsta installed!"); });
  if ("serviceWorker" in navigator) {
    window.addEventListener("load", function () { navigator.serviceWorker.register("/sw.js").catch(function () {}); });
  }
  updateCounts();
  var initialUrl = new URLSearchParams(location.search).get("url");
  if (initialUrl) { $("linkInput").value = initialUrl; resolve(initialUrl); }
})();

