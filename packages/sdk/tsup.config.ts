import { defineConfig } from "tsup";

export default defineConfig({
  entry: ["src/index.ts", "src/cli/index.ts"],
  format: ["esm"],
  target: "node18",
  clean: true,
  sourcemap: true,
  // Inlined, types included, so consumers install a single package.
  noExternal: ["@simwire/protocol"],
  dts: { resolve: ["@simwire/protocol"] },
});
