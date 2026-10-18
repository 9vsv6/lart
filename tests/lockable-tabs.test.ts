// @ts-expect-error Node test types are intentionally outside the browser-only tsconfig.
import assert from "node:assert/strict";
// @ts-expect-error Node test types are intentionally outside the browser-only tsconfig.
import test from "node:test";
import { anyTabLocked, DEFAULT_HIDDEN } from "../src/lib/lockable-tabs.ts";

test("a saved Sports restriction locks the profile while Sports is a lockable tab", () => {
  const savedTabs = { ...DEFAULT_HIDDEN, sports: true };
  assert.equal(anyTabLocked(savedTabs), true);
});

test("other tab restrictions lock a profile alongside a Sports restriction", () => {
  const savedTabs = { ...DEFAULT_HIDDEN, sports: true, movies: true };
  assert.equal(anyTabLocked(savedTabs), true);
});
