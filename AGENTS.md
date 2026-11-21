# jellyfin-client (AFinity Fork)

- **Fork (`origin`):** https://github.com/klinker41/AFinity
- **Upstream (`upstream`):** https://github.com/MakD/AFinity

## Upstream Mergeability

All changes in this repository must be structured so that merging future
upstream changes remains straightforward and conflict-free:

- Isolate new functionality and state logic into standalone files or helper
  functions rather than expanding existing upstream files.
- Keep edits to upstream files (especially large shared files like
  `MainNavigation.kt`) minimal and surgical.
- Avoid re-indenting, reformatting, or restructuring large upstream code blocks
  when a targeted parameter or hook suffices.

## Verification

Run formatting, static checks, compilation, and unit tests via Gradle:

```bash
bash ./gradlew ktfmtCheck checkCancellationRethrow checkItemProjection \
  compileDebugKotlin testDebugUnitTest
```
