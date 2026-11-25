# AJ Notes icon update

Owner-approved source: `mobile/play/assets/aj-notes-mascot.png` (512×512 RGBA).
SHA-256: `826bdf53430580a9b2d4bfd0efb7fbd8ede1908bac04c522d89b8fd96876955f`.

Regenerate from repository root using Pillow 11.3.0:

```sh
python3 mobile/scripts/generate-aj-notes-icons.py
python3 mobile/scripts/google_play_listing.py validate
```

The generator retains the original artwork, uses cream (#FFF9EF), creates Android legacy and adaptive resources, an opaque 1024×1024 iOS AppIcon (upscaled), and a 512×512 RGBA Play icon. Existing feature graphic, screenshots, application IDs and note data are unchanged.

The temporary handoff workflow successfully generated and validated the files but its bot push was rejected by the owner-only branch rule. The verified generated tree is applied through the authorized repository connection, without changing the rule. Review Android, iOS and Google Play CI on the resulting PR before merging or submitting any store changes. No store upload is performed by this change.
