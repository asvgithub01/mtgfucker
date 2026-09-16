import { build } from "esbuild";
import { cp, mkdir, rm } from "node:fs/promises";

await rm("dist", { recursive: true, force: true });
await mkdir("dist", { recursive: true });
await build({
  entryPoints: ["src/content.ts"],
  outfile: "dist/content.js",
  bundle: true,
  minify: false,
  sourcemap: true,
  target: "chrome120",
  format: "iife"
});
await cp("manifest.json", "dist/manifest.json");
