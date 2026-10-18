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
     options (size, colour, outline/shadow/box, position) and sync either way for
     every track. Lart draws all subtitles itself: `player/Renderers.kt` swaps
     Media3's text renderer for one that keeps every cue Media3 parses ahead.
   - Audio delay (an offset on the audio clock), commentary tracks avoided.
   - Chromecast through Google's default media receiver (`player/Cast.kt`).
   - Skip intro, recap and credits from IntroDB, TheIntroDB, SkipDB and AniSkip
     (`data/SkipSegments.kt`), with optional auto-skip.
   - Still to do: `harbor-core` over JNI, subtitle search by release, an mpv
     engine (Anime4K, libass), sending subtitles and headers to the Chromecast.
3. **Discovery** (started): Harbor's phone layout is in: floating tab bar
   (Search, Home, My Stuff, Profile), the Home/Movies/Shows/Anime switcher, hero
   carousel, Continue watching, Top 10, genre rows, See all grids, genre tiles
   in Search, and My List on the device. Anime comes from the Kitsu addon.
   With the user's TMDB key (`data/Tmdb.kt`): TMDB rows, backdrops, logos,
   translated overviews and cast. Profiles with "Who's watching?", each with its
   own lists, progress, player settings and trackers.
   Still to do: Jikan and AniList browsing, awards, collections, services, and
   backup/restore from the Tauri app.
4. **Trackers and sync** (started, `data/Trackers.kt`): Trakt (device sign-in,
   scrobbling, paused titles in Continue watching), Simkl (PIN sign-in,
   scrobbling) and AniList (anime progress), each with the user's own API app.
   Still to do: MyAnimeList, watch history import, Stremio library writes.
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
