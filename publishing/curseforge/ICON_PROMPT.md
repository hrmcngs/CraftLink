# CraftLink icon generation

- Method: built-in image_gen edit (no Python or CLI fallback).
- Saved project asset: `publishing/curseforge/icon.png`.
- Current assets: `icon-32.png` is 32×32; `icon.png` is its exact 512×512 enlargement. Both have opaque backgrounds. The original generated reference was 1254×1254.
- The same file is bundled as `assets/craftlink/icon.png` for the Forge mod list.
- Input reference: the previous original CraftLink icon from this project. No third-party game artwork was supplied.
- Direction: vanilla-inspired inventory/stone-GUI pixel art, muted gray and moss green, simplified outlines and reduced shine.

## Final prompt

Use case: style-transfer / logo-brand. Edit the referenced CraftLink logo into a genuinely simple, restrained low-resolution pixel-art mod icon with a classic vanilla inventory-item / stone GUI feeling. Keep only the concept of a small phone controller linked to a block; redraw every shape. A single compact item-sprite emblem: front-facing upright rectangular phone with thick dark charcoal pixel border, flat muted gray bezel, dark dull green screen, one recognizable off-white pixel D-pad; alongside a small plain stone-gray isometric cube, joined by a short chunky muted moss-green chain link. Coherent 32x32 or 48x48 pixel-grid aesthetic visibly enlarged with hard nearest-neighbor square edges, limited 12-16 color palette, 1-pixel outlines at sprite scale, simple deliberate stepped contours, only 2-3 flat tones per material. As if a careful pixel artist drew an old sandbox-game item texture. Very sparse original pixel texture on the stone cube, NOT a copied Minecraft block. Centered compact composition on a flat very dark warm-gray tile with an understated 1-pixel inset border and ample margin. No floating confetti or decorative pixels. No words, letters, numbers, sparkles, bloom, gradients, glow, neon cyan, glass, metal shine, reflections, realistic lighting, smooth curves, rounded glossy app badge, bevelled 3D render, antialiasing, detailed sci-fi gadget design, or promotional illustration. Do not reproduce any game logo, Creeper, grass block, official game texture or copyrighted asset. It should read clearly at 32px thumbnail size. Output one square opaque PNG icon, no mockup, no sheet.

## 32×32 revision

Generated with built-in image_gen using the previous icon as a reference: "Edit this CraftLink icon into authentic 32 by 32 pixel art. Draw on an exact 32x32 logical pixel grid: small upright gray phone, one simple white D-pad on dark moss green screen, a short muted green link and a small gray cube. Very simple inventory-item sprite, flat limited palette, no blur, no noise, no gradient, no shine, no smooth shading. Every pixel must be a uniform square, edges deliberately stepped. Preserve opaque charcoal background. No text, no game logos or copied textures. Output an exact 32x32 pixel PNG if possible, otherwise a square integer nearest-neighbor enlargement of a strict 32x32 pixel grid."

The generated raster was resampled with Java ImageIO using nearest-pixel sampling to deliver the explicitly requested **32×32** file at `icon-32.png`. `icon.png` is an exact 16× nearest-neighbor enlargement (**512×512**) for CurseForge upload and the mod list. No Python, smoothing or interpolated pixels were used. These dimensions supersede the previous 1254×1254 version above.

Before final nearest-pixel sampling, the generated image was cropped to an 800×800 square at (230, 230), removing excess empty margin so the phone controls remain legible at 32×32.
