Project checkup — updated 6 September 2026

All six findings from the initial review have been addressed. The collection
support was added in commit b29b853; this report describes the follow-up fixes.

**Resolved findings**

| Original finding | Fix |
| --- | --- |
| P1: Shared configuration selected the wrong Android variant | Standalone Android application/library projects now register separate generation/logging tasks and directories per variant. Build type follows AGP's debuggable flag; dimensions use exact Android flavor-dimension metadata. JVM/KMP shared generation rejects conflicting automatic selections; explicit properties remain available. |
| P1: Output-directory cleanup could delete unrelated files | Gradle owns two output files: generated Kotlin and a relative ownership record. The directory is not an output. Renames remove only the previous owned file, and cache restoration preserves neighbors. Existing user files and paths escaping through symbolic links are rejected. Validation completes before replacement. |
| P2: Incomplete identifier validation | Object names, active dimension object names, flat variant constants, fields, and package segments are validated for Kotlin identifier rules, hard keywords, and underscore-only names. Default package derivation also sanitizes invalid segments. Internal dimension delimiters/control characters and ambiguous variant-name tabs are rejected. |
| P2: Reserved-name and object collisions | Global fields cannot reuse BUILD_TYPE, MODULE_NAME, or IS_DEBUG. Nested dimensions reserve VARIANT. Nested object names, global fields, and flat fields share one collision check. |
| P2: Unescaped metadata | Module and selected-variant strings use the shared Kotlin literal encoder. Dimension comments escape controls and neutralize block-comment delimiters. A compiler/runtime test verifies the original values survive. |
| P2: Missing source-generation dependencies | JVM/KMP use a source-directory provider linked to the managed generated-file output. Android registers that view with addGeneratedSourceDirectory: Java sources for external KGP, Kotlin sources for AGP built-in Kotlin. Compile, lint, and source publication consume the producer dependency. |

The Android registration follows the official
[AGP generated-source API](https://developer.android.com/reference/tools/gradle-api/8.7/com/android/build/api/variant/SourceDirectories).

**Verification and maintenance**

Final local validation passed all 327 tests (179 unit and 148 functional), with
zero failures or skips, using Kotlin 2.4.0 and AGP 9.3.2 for consumer integration.
The Kotlin 2.3.0 / AGP 8.13.2 consumer integration run also passed. Local runs used
JDK 25; the additional JDK 17 leg is configured in CI. `git diff --check` passed.

- Regression tests cover invalid names, every generated scope's reserved names,
  incompatible shared selections, metadata compilation/runtime values, failed
  generation preserving the previous file, custom-directory neighbors, output
  renaming, and build-cache restoration.
- Consumer integration tests package a temporary Maven artifact, so the optional
  Kotlin/Android APIs are resolved as they are for a published plugin. These test
  real JVM and KMP compilation and source publication with configuration caching.
- Android integration tests build simultaneous prod/debug and preProd/release
  AARs and inspect the compiled constants. They also run lint and source
  publication from a clean build, reuse configuration caching, and invoke the
  aggregate generation task alongside variant builds.
- A pull-request/push check workflow covers Kotlin 2.3.0 with AGP 8.13.2 and Kotlin
  2.4.0 with AGP 9.3.2, on JDK 17/25 respectively. AGP 9.3.2 consumer tests use
  Gradle 9.5.0 to satisfy its minimum version; the project wrapper remains 9.4.1.
- Android integration requires SDK platform 35; CI installs it. The Android test
  explicitly skips on developer machines without that SDK.
- The README's incorrect debug output example is corrected. The compiler-test
  helper is shared by collection and metadata regression tests.

**Behavior to account for when upgrading**

Android generation writes under build/generated/konfig/<variant>/, or the same
variant child beneath a custom outputDir. Tasks are named generateProdDebugKonfig
and konfigProdDebugInfo, for example. generateKonfig and konfigInfo aggregate the
variant tasks. Explicit build-type/dimension properties retain higher priority.

KMP retains one object in commonMain, including with Android targets. Conflicting
automatic selections fail; aggregate/custom tasks without selection in their
names require explicit properties when defaults are unsuitable. Project-wide
isDebug/currentDimension queries represent one selection and cannot describe
several different Android variants simultaneously.

Invalid identifiers and conflicting automatic selections that previously
produced broken or incorrect source now fail with an actionable error.

**Optional feature proposals remaining**

These were roadmap suggestions, not defects, and were not added in this fix pass:

| Proposal | Purpose |
| --- | --- |
| strictResolution and per-dimension required | Treat missing/unknown optional selections as errors when requested |
| Field-schema validation across variants | Require fields and types to agree across selected configurations |
| Configurable propertiesFile | Share an explicitly selected, tracked configuration file across modules |
| Target/source-set selection and automatic-wiring toggle | Support custom KMP layouts and integrations |
| Structured konfigInfo output and configurable verbosity | Export resolution decisions to CI and control normal log output |
| Collection emission policy | Optionally retain boxed arrays or generate fresh-array getters |

The initial diagnostic projects remain under ignored build/checkup.
