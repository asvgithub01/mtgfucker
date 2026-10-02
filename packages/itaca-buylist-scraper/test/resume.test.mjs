import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, writeFile, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { execFileSync } from "node:child_process";

for (const complete of [false, true]) {
  test(`--resume ${complete ? "actualiza un catálogo completo" : "conserva un checkpoint parcial"}`, async () => {
    const dir = await mkdtemp(join(tmpdir(), "itaca-resume-"));
    try {
      const output = join(dir, "catalog.json");
      await writeFile(output, JSON.stringify({ schemaVersion: 1, provider: "itaca.gg", complete,
        expansions: [{ code: "TST", name: "Test", cardCount: 0, cards: [] }] }));
      const mock = join(dir, "fetch.mjs");
      await writeFile(mock, `globalThis.fetch = async url => {
        console.log('MOCK_FETCH', String(url));
        return new Response(String(url).endsWith('/expansions')
          ? '<a href="/buylist/expansion?expansionName=Test&amp;expansionAbbreviation=TST">Test</a>'
          : '<html></html>');
      };`);
      const stdout = execFileSync(process.execPath, ["--import", pathToFileURL(mock).href,
        fileURLToPath(new URL("../src/cli.mjs", import.meta.url)), "--all", "--resume", "--delay-ms", "1000", "--output", output], { encoding: "utf8" });
      assert.equal((stdout.match(/MOCK_FETCH/g) || []).length, complete ? 2 : 1);
      assert.equal(JSON.parse(await readFile(output, "utf8")).complete, true);
    } finally {
      await rm(dir, { recursive: true, force: true });
    }
  });
}
