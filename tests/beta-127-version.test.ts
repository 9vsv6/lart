// @ts-expect-error Node test types are outside the browser tsconfig.
import assert from "node:assert/strict";
// @ts-expect-error Node test types are outside the browser tsconfig.
import { readFileSync } from "node:fs";
// @ts-expect-error Node test types are outside the browser tsconfig.
import test from "node:test";

const read = (path: string) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
// App version agreement is covered by beta-0.9.126-preparation.test.mjs.
test("bundled 0.9.128 notes stay offline and keep their release title", () => {
  const notes = JSON.parse(read("src/lib/updater/bundled-release-notes.json")).notes;
  assert.equal(notes["0.9.128"].title, "Harbor Beta 0.9.128");
  assert.ok(notes["0.9.127"]);
  assert.doesNotMatch(JSON.stringify(notes["0.9.128"]), /harborsystem\.online/);
});
