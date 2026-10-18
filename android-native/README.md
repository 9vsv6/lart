# Lart native (Android)

A fully native Android Lart: Kotlin, Jetpack Compose and Media3, with no WebView.
The goal is everything the Tauri app does on Android, built in phases. Until it
catches up, the Tauri APK keeps shipping and this app installs beside it
(application id `app.lart.next`); at parity it takes over `app.lart` and the
release key, so it updates the Tauri app in place.

## Phases

1. **Core streaming** (done): Stremio sign-in, installed addons, catalog
   rows on Home, search, detail pages with seasons and episodes, the stream list,
   the Media3 player (FFmpeg audio fallback, embedded and addon subtitles, audio
   tracks), resume points, and the Stremio library.
2. **Lart's picker and player** (in progress). Done: stream ranking
   (`data/Ranking.kt`, a Kotlin stand-in for `harbor-core`'s ranker), Real-Debrid
   and TorBox accounts for torrent sources, auto-play of the best source, and
   next episode (button for the last 45 seconds, automatic at the end). Still to
   do: `harbor-core` over JNI, skip intro, subtitle styling and search.
3. **Discovery**: TMDB metadata and artwork, anime (Jikan and AniList), awards,
   collections, services and genres, profiles, settings, backup/restore from the
   Tauri app.
4. **Trackers and sync**: Trakt, Simkl, MyAnimeList, AniList, continue watching
   across devices.
5. **The rest of Harbor**: Live TV, sports, manga and books, music, games,
   watch-together, themes, Android TV layout.

## Build

Needs the Android SDK (platform 36, build-tools 36) and JDK 21, the same
toolchain as the Tauri app (see `scripts/android-env.sh`).

```bash
cd android-native
./gradlew assembleDebug
```

The debug APK is in `app/build/outputs/apk/debug/`. Debug and release builds
are separate apps from the Tauri `app.lart` until phase 5.

## License note

The Media3 FFmpeg audio decoder (Jellyfin's build) is GPL-3.0, as in the Tauri
app; share the source with any APK you distribute (see `../LART.md`).
