import * as explicitPreference from "../../src/lib/music/explicit-preference.ts";
import * as queueAutomation from "../../src/lib/music/queue-automation.ts";
import * as queueSource from "../../src/lib/music/queue-source.ts";
import * as sourceVersion from "../../src/lib/music/source-version.ts";
import * as transport from "../../src/lib/music/transport.ts";

const noop = () => {};
const settled = async () => {};

/**
 * Inert stand-ins for the src/lib/music/player.ts dependencies a test does not
 * exercise. Pure modules are the real ones.
 */
function musicPlayerDefaults(): Record<string, Record<string, unknown>> {
  return {
    "./source-version": sourceVersion,
    "./queue-automation": queueAutomation,
    "./explicit-preference": explicitPreference,
    "./queue-source": queueSource,
    "@/lib/active-profile-id": { activeProfileId: () => "test", activeProfileIsPrimary: () => true },
    "@/lib/platform": { isWindowsDesktop: () => false },
    "./listening-affinity": { hydrateListeningAffinity: settled, observeMusicListening: noop },
    "./artist-blocks": {
      filterBlockedTracks: <T>(tracks: readonly T[]) => [...tracks],
      hydrateArtistBlockStore: settled,
    },
    "./liked-artists": { hydrateLikedArtistStore: settled },
    "./pinned-sources": {
      loadPinnedSources: settled,
      peekPinnedSource: () => null,
      pinSourceFor: noop,
      unpinSourceFor: noop,
    },
    "./recent-context": { hydrateMusicContextTracks: settled, hydrateMusicRecentContexts: settled },
    "./recent-destinations": { hydrateMusicDestinations: settled },
    "./source-consent": { hydrateMusicSourceConsent: settled },
    "./playback-origin": {
      beginMusicQueue: noop,
      getMusicPlaybackOrigin: () => null,
      restoreMusicPlaybackOrigin: noop,
    },
    "./deck-sync": {
      answerDeckRequests: () => noop,
      broadcastDeckState: noop,
      isDeckWindow: () => false,
      sendDeckAdopted: noop,
      sendDeckCommand: noop,
      serveDeckCommands: () => noop,
    },
    "./deck-primary": { createDeckAdoption: () => ({ deck: () => 0, adopt: () => false }) },
    "./hidden-recents": { unhideMusicRecent: noop },
    "@/lib/cast-ownership": { stopCastOwner: settled },
    "./transport": transport,
  };
}

/** The test's own mocks, filled in per module with the defaults it leaves out. */
export function withMusicPlayerDefaults(mocks: Record<string, unknown>): Record<string, unknown> {
  const merged: Record<string, unknown> = { ...musicPlayerDefaults() };
  for (const [name, mock] of Object.entries(mocks)) {
    const fallback = merged[name];
    merged[name] =
      fallback && mock && typeof mock === "object" ? { ...fallback, ...mock } : mock;
  }
  return merged;
}
