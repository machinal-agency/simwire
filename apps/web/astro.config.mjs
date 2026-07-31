import { defineConfig } from "astro/config";

export default defineConfig({
  site: "https://simwire.machinal.agency",
  build: {
    // One stylesheet shared by every page instead of the fonts being inlined
    // three times.
    inlineStylesheets: "never",
  },
});
