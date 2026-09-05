# Changelog

## Unreleased

- Added list, map, and array fields using `listOf`, `mapOf`, and `arrayOf`, including nested, nullable, empty, and provider-backed values.
- Optimized arrays of non-null primitive elements into primitive arrays, such as `Array<Int>` becoming `IntArray`.
- Added `Byte`, `Short`, and `Char` fields and fixed `Long.MIN_VALUE` generation.
- Fixed standalone Android projects to generate separate configuration for each variant, including simultaneous debug/release and flavor builds. KMP keeps one shared configuration in `commonMain`.
- Changed Android detection to use variant metadata: build types follow `debuggable`, and Konfig dimension names must match Android flavor dimension names for automatic selection. Explicit properties still take precedence.
- Changed Android output to `build/generated/konfig/<variant>/` with tasks such as `generateProdDebugKonfig`. `generateKonfig` and `konfigInfo` now run all variant tasks; custom paths and task integrations may need updating.
- Fixed conflicting JVM/KMP task selections to fail clearly instead of silently choosing the wrong configuration. Use separate builds or explicit selection properties to resolve conflicts.
- Excluded project-path segments from task-name detection, so `:prod:compileKotlin` no longer selects the `prod` variant by itself.
- Added validation for invalid Kotlin names, package names, reserved fields, name collisions, unsupported field types, and incompatible field override types. Previously accepted invalid configurations may now fail earlier.
- Fixed default package derivation for unusual project/group names and escaping of module names, variant names, and generated comments.
- Made regeneration and build-cache restoration preserve neighboring files. Renames remove only the previously owned generated file, and existing user files are protected from overwriting.
- Fixed generated-source dependencies for compilation, Android lint, and source publication.
- Kept the existing DSL syntax and project-wide query APIs. On Android, `konfig.isDebug` and dimension queries describe one project-wide selection; use `androidComponents.onVariants` for decisions that vary by Android variant.
- Added regression tests, JVM/KMP/Android consumer integration tests, and a CI check workflow. Local validation passed 327 tests, with Android integration also checked on AGP 8.13.2 and 9.3.2.
