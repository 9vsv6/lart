// @ts-expect-error Node test types are outside the browser-only tsconfig.
import assert from "node:assert/strict";
// @ts-expect-error Node test types are outside the browser-only tsconfig.
import test from "node:test";
import { prepareSubtitle, SubtitlePreparationError } from "../src/lib/subtitles/prepare.ts";
import { clearTranslationJobs, completeTranslationJob, expiredTranslationJobs, registerTranslationJob, TRANSLATION_JOB_MAX_MS } from "../src/lib/subtitles/translation-jobs.ts";
import { fetchSubtitlesIntoPlayer } from "../src/lib/subtitles/fetch-into-player.ts";
import { rankSubtitleCandidates } from "../src/lib/subtitles/candidate-ranking.ts";
import { prepareConsensusCandidate } from "../src/lib/subtitles/autosync/consensus.ts";
import { isGeneratedLangLabel, isPlausibleLang } from "../src/lib/subtitles/language.ts";
import { subtitleLoadMetadataOf, subtitleTitleOf } from "../src/lib/subtitles/provider-label.ts";
import { deferred, flushBridge, mpvBridgeHarness } from "./helpers/mpv-bridge-harness.ts";

const url = "https://subtitles.test/addon/config/translate/source/hi.srt";
const pending = () => new SubtitlePreparationError("translation-pending", "fixture pending");
const srt = "1\n00:00:01,000 --> 00:00:03,000\nOne\n\n2\n00:00:04,000 --> 00:00:06,000\nTwo\n";
const candidate = { id: "translation", url, source: "addon" as const, lang: "Make Hindi", label: "Make Hindi • source release", title: "SubMaker" };
const prepared = (cleanup = () => {}) => ({ playableUrl: "memory:final.srt", format: "srt", cues: [{ start: 1, end: 3, text: "Final translation" }], cleanup });

test("only advertised pending responses are retried; corrupt/final error payloads stay failures", async () => {
  for (const filename of ["translating_hi.srt", "click_to_translate_hi.srt"]) {
    await assert.rejects(prepareSubtitle({ url, translation: true }, {
      fetchBytes: async () => new Response(srt, { headers: { "content-disposition": `attachment; filename="${filename}"` } }),
      createPlayable: async () => { throw new Error("Pending bytes must not be played"); },
    }), (error) => error.reason === "translation-pending");
  }
  for (const body of ["<html>Bad gateway</html>", "1\n00:00:00,000 --> 04:00:00,000\nTranslation failed"]) {
    await assert.rejects(prepareSubtitle({ url, translation: true }, {
      fetchBytes: async () => new Response(body, { headers: { "content-disposition": 'attachment; filename="translated_hi.srt"' } }),
    }), (error) => error.reason !== "translation-pending");
  }
  const final = await prepareSubtitle({ url, translation: true }, {
    fetchBytes: async () => new Response(srt, { headers: { "content-disposition": 'attachment; filename="translated_hi.srt"' } }),
    createPlayable: async () => ({ url: "memory:final", cleanup() {} }),
  });
  assert.equal(final.cues.length, 2);
  final.cleanup();
});

for (const deep of [false, true]) {
  test(`translation offers are discoverable but never prefetched (${deep ? "deep" : "initial"})`, async () => {
    const downloads: string[] = [];
    const result = await fetchSubtitlesIntoPlayer({
      bridge: { addSubtitle: async (url) => { downloads.push(url); return true; } } as never,
      src: { url: "fixture.mkv", meta: { id: "tt123", name: "Fixture", type: "movie" } } as never,
      settings: { subProvidersEnabled: {} } as never,
      addons: [], langs: [], isActive: () => true, deep,
    }, {
      search: async (_query, options) => { options.onPartial?.([candidate], 0); return [candidate]; },
      prepare: async (input) => { downloads.push(input.url); throw new Error("Must not fetch an action"); },
    });
    assert.deepEqual(downloads, []);
    assert.equal(result.generated[0].label, "Make Hindi");
    assert.equal(result.generated[0].count, 1);
    assert.equal(subtitleTitleOf(candidate), candidate.label);
    assert.equal(subtitleLoadMetadataOf(candidate).translation, true);
    assert.equal(isGeneratedLangLabel("Brazilian Portuguese"), false);
    assert.equal(isGeneratedLangLabel("SubMaker Notice"), false);
    assert.equal(isPlausibleLang("Make Portuguese (Brazil)"), true);
  });
}

test("a pending translation is tracked once per URL and gives up at ten minutes", (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: 0 });
  t.after(() => { clearTranslationJobs(); t.mock.timers.reset(); });
  registerTranslationJob({ url, lang: "hi", title: "Translation" });
  t.mock.timers.tick(60_000);
  registerTranslationJob({ url, lang: "hi", title: "Translation" });
  assert.deepEqual(expiredTranslationJobs(TRANSLATION_JOB_MAX_MS - 1), []);
  const expired = expiredTranslationJobs(TRANSLATION_JOB_MAX_MS);
  assert.equal(expired.length, 1, "re-registering keeps the first start time");
  assert.equal(expired[0].url, url);
  completeTranslationJob(url);
  assert.deepEqual(expiredTranslationJobs(TRANSLATION_JOB_MAX_MS), []);
});

test("clearing translation jobs drops work for media that no longer applies", (t) => {
  t.after(() => clearTranslationJobs());
  registerTranslationJob({ url, lang: "hi" });
  clearTranslationJobs();
  assert.deepEqual(expiredTranslationJobs(Number.MAX_SAFE_INTEGER), []);
});

test("the bridge refuses to start a translation from automatic or preload calls", async () => {
  const h = mpvBridgeHarness();
  let calls = 0;
  h.prepareWith(async () => { calls++; return prepared(); });
  assert.equal(await h.bridge.addSubtitle(url, "Make Hindi", "Translation", false, { translation: true }), false);
  assert.equal(await h.bridge.addSubtitle(url, "Make Hindi", "Translation", true, { translation: true }, "automatic"), false);
  assert.equal(calls, 0);
  h.bridge.destroy();
});

test("automatic ranking and timing consensus never download translation offers", async () => {
  assert.deepEqual(rankSubtitleCandidates([candidate], []), []);
  assert.equal(await prepareConsensusCandidate(candidate, 100, {} as never), null);
});
