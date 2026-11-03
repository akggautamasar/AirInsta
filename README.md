# AirInsta ✨

**AirInsta** is a beautifully designed, installable Instagram media viewer and downloader powered by your existing **AirIG Cloudflare Worker**.

**Live extractor:** https://airig.worksbeyondworks.workers.dev/

## Features

- Play Instagram reels and post videos in the browser (native video controls, playback and seeking).
- Preview public Instagram images, video posts, and mixed carousels.
- Navigate carousel slides with thumbnail previews and previous/next buttons.
- Download one photo or video directly, or **download all carousel media together as a ZIP**.
- Display creator handle, caption, media type and item count when the extractor provides them.
- Quickly paste links, open original posts and share links.
- Save favorite links and recent links **locally in the device's browser**.
- Responsive dark/light UI with an Instagram-inspired aesthetic.
- Install as an Android/desktop Progressive Web App (PWA), including an offline application shell.
- Basic API validation, safe download filename handling, approved CDN domain validation and streaming with HTTP Range support.

## Stack

| Component | Implementation |
|---|---|
| Web app | Vanilla JavaScript, CSS, responsive HTML |
| Metadata extraction | Existing Cloudflare Worker (AirIG) |
| API route | Vercel Function `/api/resolve` |
| Media relay | Vercel Function `/api/media` |
| Installable app | PWA manifest + service worker |
| Saved links/history | Browser localStorage |
| ZIP creation | Client-side, dependency-free ZIP (stored / uncompressed) |

## Deploy

1. Import this GitHub repository as a **Vercel project**, or use the existing connected `airinsta` project.
2. Framework preset: **Other** (no build command and no output directory). Node.js backend functions live in the root `api/` directory.
3. Optional environment variable `AIRINSTA_WORKER_URL`: `https://airig.worksbeyondworks.workers.dev/`. This is also the built-in default.
4. Deploy the `main` branch. Vercel will serve the static interface and both API functions on the same origin.

To run the static interface locally with working API functions, use `vercel dev` from the project directory after linking your Vercel account. Opening `index.html` directly in a file browser will not run the API functions.

## API response compatibility

The current extractor returns a format like:

```json
{
  "status": "success",
  "result": [
    {
      "username": "example",
      "caption": "Example post",
      "is_album": true
    },
    {
      "video_img": "https://instagram.fna.fbcdn.net/thumbnail.jpg",
      "video_url": "https://instagram.fna.fbcdn.net/video.mp4",
      "is_video": true
    }
  ]
}
```

The app treats metadata separately from media items and uses the actual number of returned media files (rather than trusting `is_album`) to determine when to display a gallery or a ZIP button. It also supports image URLs and several common nested response forms.

## Boundaries / important notes

- AirInsta is **not an Instagram client** and does not reproduce Instagram's authenticated feed, DMs, followers, likes or account content. Those features need official Instagram APIs, appropriate permissions and an account login flow.
- Only **public content supported by your existing Cloudflare extractor** can be resolved; private, close-friends or login-restricted content is not bypassed.
- Signed Instagram CDN URLs expire. Resolve the link again if playback or download fails.
- The ZIP feature is designed for normal carousel posts. For exceptionally large collections (over 220 MB), download files separately to avoid excessive browser memory use.
- The app does not store media on a server. Links saved by the user are kept in the local browser; clearing site data removes them.
- Some browsers restrict the Clipboard API or PWA installation. Manual paste and the website remain available.
- Vercel function limits may affect extremely large or very slow media streams. Video playback tries the CDN first and falls back to a same-origin proxy.
- This independent project is not endorsed by or affiliated with Instagram/Meta. Only download media that you own or are authorized to save, and respect creator rights and platform rules.

## Project layout

```
AirInsta/
├── index.html
├── assets/
│   ├── app.css
│   └── app.js
├── api/
│   ├── _shared.js
│   ├── resolve.js
│   └── media.js
├── manifest.webmanifest
├── icon.svg
├── icon-192.svg
├── sw.js
└── vercel.json
```

Built for **WorksBeyondWorks**.
