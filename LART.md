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
- The Android APK embeds the whole desktop frontend (about 170 MB).

## Android playback and licensing

On phones, video plays in Media3 ExoPlayer (Apache-2.0) drawn behind the
transparent WebView (`NativePlayer.kt`, `src/lib/player/native-android.ts`).
Audio the device cannot decode (AC3, E-AC3, DTS, TrueHD, 7.1) goes through
Jellyfin's `media3-ffmpeg-decoder`, which is **GPL-3.0**. An APK that bundles
it is GPL-3.0 as a whole when distributed: whoever receives the APK must be
able to get the corresponding source. Lart's MIT code is compatible with that;
publish the source (or this repository) alongside any APK you share.
