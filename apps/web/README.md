# simwire.machinal.agency

The public site: landing, documentation and the Android app download page.
Built with [Astro](https://astro.build), deployed on Vercel.

```bash
npm install
npm run dev        # http://localhost:4321
npm run build      # static output in dist/
```

## Layout

| Path | What it holds |
| --- | --- |
| `src/pages/` | One file per route: `index`, `docs`, `download` |
| `src/layouts/Base.astro` | Document shell, metadata, global stylesheet |
| `src/components/` | `Nav`, `Footer`, `Wordmark` shared by every page |
| `src/styles/global.css` | Fonts, design tokens, reset, nav and footer |
| `src/assets/` | Brand SVGs, inlined at build time |
| `public/fonts/` | Inter and Geist Mono, served once and cached |

Page-specific CSS lives in the page itself. It is declared `is:global` because
the landing builds parts of its demo in JavaScript at runtime, and scoped styles
would not reach those elements.

## Deploying

```bash
vercel deploy --prod
```

Vercel detects Astro and runs the build. `vercel.json` keeps the clean URLs,
redirects the older `.html` paths and caches the fonts.
