# AJ Notes icon update — original upload required

This branch prepares the approved 512×512 transparent PNG source (SHA-256
`826bdf53430580a9b2d4bfd0efb7fbd8ede1908bac04c522d89b8fd96876955f`).
Upload the exact original as `mobile/play/assets/aj-notes-mascot.png`.
The branch-only workflow then generates Android legacy/adaptive icons, iOS RGB
1024×1024 and Play RGBA 512×512, checks the source hash, validates store assets,
commits only the icon changes, and dispatches the existing read-only CI.
The image is not redrawn; the original is retained, and the iOS asset is upscaled.
The background is cream (#FFF9EF). The old feature graphic and screenshots
are unchanged. No store upload or review submission is performed.
Do not merge until the generated assets are committed and actual Android, iOS
and Play CI results are reviewed. Remove the temporary branch-only workflow
before merging. If the workflow cannot write or dispatch, inspect its logs
and run the generator manually on this branch.
