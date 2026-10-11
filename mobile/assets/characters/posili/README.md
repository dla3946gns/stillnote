# Posili 2D mascot — draft integration guide

Five 256×256 SVG assets are in this directory: `idle`, `writing`, `thinking`, `celebrate`, and `sleep`.

## Visual rules
- Potato #F7D58B; sprout #7DBB4F; outline #5B4A32; blush #FFB3C1; accent #F4B94A.
- Keep the two-leaf silhouette and face consistent across states.
- SVG is the source of truth. Produce platform-specific Android VectorDrawable or PNG and iOS asset catalog resources from these SVGs as required.

## Planned placement
- Onboarding: idle.
- Empty notes list: writing.
- Idea prompt: thinking.
- Explicit successful save: celebrate (do not animate on each autosave).
- Focus mode: sleep.

## Accessibility and privacy
- Respect system reduce-motion preferences; allow disabling character motion.
- Treat purely decorative mascot instances as hidden from screen readers.
- Do not use note contents to infer the mascot's mood.
- Do not change the NotesStore save/cancel/backup semantics.

## Implementation status
SVG sources only. Android Compose and iOS SwiftUI integration, animation, settings, build, and device QA are pending. This branch is based on PR #1 head rather than main.
