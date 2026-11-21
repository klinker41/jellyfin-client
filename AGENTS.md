# jellyfin-client (AFinity Fork)

- **Fork (`origin`):** https://github.com/klinker41/jellyfin-client
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

## Screen Sizes and Responsive Layout

Every change must work well across all screen sizes, including phones,
foldables (unfolded and folded states), and tablets:

- Verify layouts, navigation (bottom bar vs. navigation rail), spacing, and
  adaptive sizing across compact, medium, and expanded window size classes.
- Avoid hard-coded dimensions or assumptions tied to a single phone portrait
  viewport.

## Verification

Run formatting, static checks, compilation, and unit tests via Gradle:

```bash
bash ./gradlew ktfmtCheck checkCancellationRethrow checkItemProjection \
  compileDebugKotlin testDebugUnitTest
```
