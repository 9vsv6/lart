# Lart

Lart is a Stremio client based on [Harbor](LICENSE) 0.9.132 (MIT, copyright the
Harbor contributors). It keeps Harbor's desktop app and adds a standalone
Android phone app.

## What differs from Harbor

- **Name and identity.** App id `app.lart`, so Lart keeps its own settings and
  data and never touches an installed Harbor. Window titles, wordmark, mark and
  icons are Lart's. License and attribution files still credit Harbor.
- **No Harbor servers.** Harbor's backend is gone (harbor.site shows a sunset
  page). Every server URL in `src/lib/config/endpoints.ts` now defaults to an
  unresolvable `*.lart.invalid` host and the updater has no endpoint. See
  `.env.example` to point them at your own server.
- **Android phone app.** On a phone the Android build shows the touch UI
  (`src/views/mobile/`) and plays streams locally; Android TV keeps the
  Big Picture UI. See `src/views/mobile/phone-playback.tsx`.

## Build

Desktop (Windows):

```bash
pnpm install
pnpm tauri dev
```

The mpv, ffmpeg, ffprobe and yt-dlp binaries go in `src-tauri/binaries/` and
`libmpv-2.dll` in `src-tauri/libmpv/` (see `pnpm run setup`). Capstan and the
Pokémon engine are prebuilt resources under `src-tauri/resources/`.

Android (needs the Android SDK, NDK and Rust Android targets; JDK 21):

```bash
scripts/android-build.sh aarch64 release
```

The APK lands in `src-tauri/gen/android/app/build/outputs/apk/`. The script
works without Windows Developer Mode. Release builds are signed with the key
referenced by `src-tauri/gen/android/key.properties` (git-ignored). Keep that
keystore safe: Android only accepts updates signed with the same key.

## Known gaps

- Features that need Harbor's server are off until you run your own: Trakt,
  MyAnimeList and AniList sign-in token exchange, TVDB/IMDb proxies, watch
  party relay, bug reports, subtitle community sync and updates.
- Android plays in the WebView: H.264, HEVC, VP9 and AAC work, but AC3, E-AC3
  and DTS audio do not. A native player (Media3/ExoPlayer) is the fix.
- The Android APK embeds the whole desktop frontend (about 170 MB).
