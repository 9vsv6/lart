# Pokémon engine

A separate, self-contained .NET program the app runs for its Pokémon save tools.
It builds PKForge and its PKHeX and PKHeX-Plugins dependencies at the commits
pinned in `upstream.json`. It is licensed GPL-3.0-or-later (see `LICENSE`),
separately from the app.

## Build

Needs the .NET SDK and git.

```bash
node tools/pokemon-engine/build.mjs \
  --fetch-source <work dir>/pokemon-source \
  --output src-tauri/resources/pokemon-engine \
  [--rid win-x64|linux-x64|linux-arm64|osx-x64|osx-arm64]
```

`--fetch-source` clones the pinned sources and refuses a checkout at any other
commit or with local changes. The output holds the engine, an `engine.json`
with its revision and SHA-256, and `sources/`: the corresponding source for
every pinned project plus these build files, so a build that ships the engine
also ships its source.
