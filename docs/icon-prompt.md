# JP Media Viewer Icon Prompt

## Design Direction

Create a clean, modern Android adaptive app icon for **JP Media Viewer**, a local image and video browsing app.

The icon should communicate:

- Media browsing
- Image and video support
- Favorites or saved items
- A calm, focused viewing experience

Avoid making the icon look like a generic camera app. The core metaphor should be a **media card / gallery card**, not a camera lens.

## Visual Style

- Style: modern flat vector icon
- Mood: clean, quiet, polished, app-store ready
- Shape language: rounded rectangles, simple geometric symbols, soft contrast
- Detail level: low to medium; must remain readable at 48 px
- Lighting: flat with subtle layered depth, no realistic shadows
- Text: no text, no letters, no app name inside the icon

## Composition

- Use a dark navy adaptive icon background.
- Place a centered rounded media card in the safe zone.
- Inside the card, show an abstract image landscape using blue and cyan shapes.
- Add a small play triangle to suggest video support.
- Add a small yellow favorite star or sparkle near the top-right area.
- Keep all important shapes inside the Android adaptive icon safe zone.
- Ensure the icon still works with circular, rounded-square, and square launcher masks.

## Color Palette

- Background: `#0B1020` dark navy
- Main card: `#FFFFFF` white
- Primary blue: `#1D4ED8`
- Cyan accent: `#38BDF8`
- Light highlight: `#E0F2FE`
- Dark symbol color: `#0B1020`
- Favorite accent: `#FFC857` warm yellow
- Secondary neutral: `#CBD5E1`

## Android Adaptive Icon Requirements

- Design for a 108 x 108 dp adaptive icon viewport.
- Keep core artwork within the central safe zone.
- Background and foreground should be separable.
- Avoid tiny details near the outer edges because launcher masks may crop them.
- The icon should remain legible at 48 px and 64 px.
- It should work on dark and light launchers.
- Do not rely on text, gradients, or photographic details.

## High-Quality Prompt

```text
Create a clean modern Android adaptive app icon for an app called JP Media Viewer.
The app is a local image and video browser with favorites support.

Use a dark navy background (#0B1020). In the center, place a rounded white media card
inside the adaptive icon safe zone. The media card contains an abstract image landscape
made from deep blue (#1D4ED8) and cyan (#38BDF8) geometric shapes, with a pale blue
highlight circle (#E0F2FE). Add a small dark navy play triangle on the card to suggest
video playback. Add a small warm yellow favorite star or sparkle (#FFC857) near the
top-right area of the card.

Style: modern flat vector, clean geometric shapes, app-store ready, high contrast,
minimal but recognizable, no text, no letters, no watermark, no camera lens.
The design must remain readable at 48 px and should work with Android circular,
rounded-square, and square launcher masks. Keep all important elements inside the
central safe zone of a 108 x 108 dp adaptive icon.
```

## Negative Prompt

```text
No text, no letters, no app name, no watermark, no realistic camera lens, no busy UI,
no tiny details, no photo-realistic rendering, no 3D skeuomorphism, no excessive glow,
no cluttered background, no hard-to-read symbols near the edges, no complex gradients.
```
