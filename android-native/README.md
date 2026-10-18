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
2. **Lart's picker and player** (mostly done):
   - Stream ranking (`data/Ranking.kt`, a Kotlin stand-in for `harbor-core`'s
     ranker), Real-Debrid and TorBox for torrent sources, auto-play.
   - Harbor's phone player (`player/PlayerUi.kt`): seek, brightness and volume
     gestures, double-tap ±10 s, lock, fit/fill/stretch, picture-in-picture,
     speed, sleep timer, "Still watching?", resume prompt, Up next and
     automatic next episode, episode and source switching in the player.
   - Subtitles: embedded and addon tracks, remembered language, Harbor's style
     options (size, colour, outline/shadow/box, position) and sync for addon
     subtitles.
   - Skip intro, recap and credits from IntroDB, TheIntroDB, SkipDB and AniSkip
     (`data/SkipSegments.kt`), with optional auto-skip.
   - Still to do: `harbor-core` over JNI, subtitle search by release, audio
     delay, casting.
3. **Discovery** (started): Harbor's phone layout is in: floating tab bar
   (Search, Home, My Stuff, Profile), the Home/Movies/Shows/Anime switcher, hero
   carousel, Continue watching, Top 10, genre rows, See all grids, genre tiles
   in Search, and My List on the device. Anime comes from the Kitsu addon.
   Still to do: TMDB metadata and artwork, Jikan and AniList, awards,
   collections, services, profiles, and backup/restore from the Tauri app.
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
