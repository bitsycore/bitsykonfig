# buildkonfig-gradle-plugin

A Gradle plugin that generates a `BuildKonfig` Kotlin object at build time — like Android's `BuildConfig`, but for any Kotlin project (JVM, Multiplatform, Android).

Fields can be constant, overridden per build type (debug/release), or scoped to named **dimensions** (e.g. environment, region) with their own variants.

**Plugin ID:** `com.bitsycore.konfig`  
**Group:** `com.bitsycore`  
**Artifact:** `konfig-gradle-plugin`  
**Version:** `0.6.0`  
**JVM target:** 17

---

## Setup

### 1. Configure plugin resolution

The plugin is published to **maven.bitsycore.com** (no authentication) and to
**GitHub Packages** as a fallback (requires a GitHub PAT with `read:packages`).

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.bitsycore.com/releases")
        gradlePluginPortal()
    }
}
```

<details>
<summary>GitHub Packages fallback (authenticated)</summary>

Store credentials in `~/.gradle/gradle.properties` — never commit them:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_PERSONAL_ACCESS_TOKEN
```

```kotlin
pluginManagement {
    repositories {
        maven {
            name = "GitHubPackages"
            url  = uri("https://maven.pkg.github.com/bitsycore/bitsykonfig-gradle-plugin")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GPR_USER")
                password = providers.gradleProperty("gpr.key").orNull  ?: System.getenv("GPR_KEY")
            }
        }
        gradlePluginPortal()
    }
}
```
</details>

### 2. Declare the plugin

Using a version catalog (`libs.versions.toml`):

```toml
[versions]
konfig = "0.6.0"

[plugins]
konfig = { id = "com.bitsycore.konfig", version.ref = "konfig" }
```

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.konfig)
}
```

Or inline:

```kotlin
plugins {
    id("com.bitsycore.konfig") version "0.6.0"
}
```

---

## Basic usage

The plugin auto-detects your project's `group` and `name` to set a default package. The generated file is placed in `build/generated/konfig/` and wired into your source sets automatically.

```kotlin
konfig {
    field("APP_NAME", "My App")
    field("VERSION_CODE", 42)
    field("ENABLE_LOGGING", false).debug(true)
}
```

Generated output (`BuildKonfig.kt`):

```kotlin
public object BuildKonfig {
    const val BUILD_TYPE: String = "release"
    const val MODULE_NAME: String = "my-app"
    const val IS_DEBUG: Boolean = false

    const val APP_NAME: String = "My App"
    const val VERSION_CODE: Int = 42
    const val ENABLE_LOGGING: Boolean = false
}
```

In debug builds (`-Pkonfig.buildtype=DEBUG`), `ENABLE_LOGGING` becomes `inline val ENABLE_LOGGING: Boolean get() = true`.

---

## Configuration reference

### Object settings

```kotlin
konfig {
    objectPackage    = "com.example.app"   // default: derived from group + project name
    objectName       = "BuildKonfig"        // default: "BuildKonfig"
    objectVisibility = Visibility.INTERNAL  // default: Visibility.PUBLIC
}
```

### Supported field types

| Kotlin type | Example                                    |
|-------------|--------------------------------------------|
| `String`    | `field("BASE_URL", "https://example.com")` |
| `Boolean`   | `field("FEATURE_X", false)`                |
| `Int`       | `field("TIMEOUT", 30)`                     |
| `Long`      | `field("MAX_SIZE", 1_000_000L)`            |
| `Float`     | `field("RATIO", 1.5f)`                     |
| `Double`    | `field("PI", 3.14159)`                     |
| `Byte`, `Short`, `Char` | `field("SEPARATOR", ':')`       |
| `List<T>`   | `field("HOSTS", listOf("api.example.com"))` |
| `Map<K, V>` | `field("PORTS", mapOf("https" to 443))`    |
| `Array<T>`  | `field("REGIONS", arrayOf("eu", "us"))`  |
| Primitive arrays | `field("RETRIES", intArrayOf(1, 3, 5))` |

### Lists, maps, and arrays

Collections generate ordinary `val` properties initialized with `listOf`,
`mapOf`, or an array factory. They work with providers, build-type overrides,
dimension variants, `common {}`, and flat dimensions:

```kotlin
konfig {
    field("HOSTS", listOf("api.example.com")).debug(listOf("localhost"))
    field("PORTS", mapOf("http" to 80, "https" to 443))
    field("REGIONS", arrayOf("eu", "us"))
    field("RETRIES", arrayOf(1, 3, 5))
    field("EMPTY", emptyList<String>())
    field("ROUTES", mapOf("primary" to listOf("/health", "/status")))
}
```

```kotlin
val HOSTS: List<String> = listOf<String>("api.example.com")
val PORTS: Map<String, Int> = mapOf<String, Int>("http" to 80, "https" to 443)
val REGIONS: Array<String> = arrayOf<String>("eu", "us")
val RETRIES: IntArray = intArrayOf(1, 3, 5)
val EMPTY: List<String> = listOf<String>()
val ROUTES: Map<String, List<String>> = mapOf<String, List<String>>("primary" to listOf<String>("/health", "/status"))
```

Arrays of non-null primitive elements are specialized: `Array<Int>` generates
`IntArray` with `intArrayOf`. The same applies to Boolean, Byte, Short, Char,
Long, Float, and Double. Existing primitive arrays retain their primitive type.
Nullable arrays such as `Array<Int?>` stay generic. Specialization also applies
inside nested collections, so `List<Array<Int>>` generates `List<IntArray>`.

Empty collections retain the type supplied in the DSL. Nullable elements,
nested lists/maps/arrays, and mixed values explicitly typed as `Any` are
supported; values must ultimately be supported scalars or collections.
Custom objects and sets are rejected. Lists and maps expose read-only Kotlin
interfaces; generated arrays are mutable. Collection properties are not `const`.
Build-type scope overrides must keep the declared field type.

### Build-type overrides

Three equivalent forms:

```kotlin
konfig {
    // Fluent handle — default + one or both overrides
    field("BASE_URL", "https://prod.example.com").debug("https://dev.example.com")

    // Scope blocks — build type is fixed, field() returns Unit, no chaining
    debug   { field("MOCK_API", true) }
    release { field("MOCK_API", false) }
}
```

> `field()` inside `debug {}` / `release {}` blocks intentionally returns `Unit` — the build type is already fixed by the enclosing scope, so `.debug()` / `.release()` chaining is impossible by design.

---

## Dimensions

Dimensions let you select a named variant at build time (e.g. `env=prod`, `env=dev`). Each active dimension generates a nested object inside `BuildKonfig`.

```kotlin
konfig {
    dimension("env", defaultTo = "prod") {
        common {
            field("TIMEOUT", 30)
            debug { field("TIMEOUT", 5) }
        }
        variant("prod") {
            field("BASE_URL", "https://prod.example.com")
            field("ANALYTICS", true)
        }
        variant("dev") {
            field("BASE_URL", "https://dev.example.com")
            field("ANALYTICS", false)
        }
    }
}
```

Generated output (with `env=dev`, debug build):

```kotlin
public object BuildKonfig {
    const val BUILD_TYPE: String = "debug"
    // ...

    public object Env /*env*/ {
        const val VARIANT: String = "dev"
        inline val TIMEOUT: Int get() = 5     // common {}, debug override
        const val BASE_URL: String = "https://dev.example.com"
        const val ANALYTICS: Boolean = false
    }
}
```

### `common {}` block

Fields declared in `common {}` act as fallbacks for all variants. A variant field with the same name takes precedence over the common field.

### Custom object name

```kotlin
dimension("env", objectNameOverride = "Environment", defaultTo = "prod") { ... }
// generates: object Environment /*env*/ { ... }
```

If no override is given, the object name is derived from the dimension name via CamelCase conversion (`my-env` → `MyEnv`).

### Flat dimensions

`flatDimension` works exactly like `dimension`, but its fields are generated
directly at the root of the konfig object instead of a nested object. The
active variant is exposed as `<NAME>_VARIANT`:

```kotlin
konfig {
    flatDimension("env", defaultTo = "prod") {
        variant("prod") { field("BASE_URL", "https://prod.example.com") }
        variant("dev")  { field("BASE_URL", "https://dev.example.com") }
    }
}
```

Generated output (with `env=prod`):

```kotlin
public object BuildKonfig {
    const val BUILD_TYPE: String = "release"
    // ...

    // dimension: env (flat), variant: prod
    const val ENV_VARIANT: String = "prod"
    const val BASE_URL: String = "https://prod.example.com"
}
```

Root-level name collisions **fail the build** — a flat field may not shadow a
built-in constant (`BUILD_TYPE`, `MODULE_NAME`, `IS_DEBUG`), a global field, or
a field from another flat dimension.

---

## Variant selection

Variants are resolved in priority order:

| Priority | Source                   | Example                                  |
|----------|--------------------------|------------------------------------------|
| 1        | Gradle property          | `-Pkonfig.dimension.env=dev`             |
| 2        | `konfig.properties` file | `konfig.dimension.env=dev`               |
| 3        | Task-name detection      | Running `assembleDevDebug` matches `dev` |
| 4        | `defaultTo` in DSL       | `dimension("env", defaultTo = "prod")`   |
| —        | Omitted silently         | No variant → no nested object generated  |

### `konfig.properties` file

Place a `konfig.properties` file in your project directory:

```properties
konfig.dimension.env=dev
```

This file is tracked as a task input — changing it invalidates the build cache.

### Task-name matching rules

Variant detection respects **camelCase word boundaries** — a variant only
matches a whole segment of the task name, never a plain substring:

- `assemblePreprodRelease` matches variant `preprod`, **not** `prod`
- `assembleProdRelease` matches variant `prod`, **not** `preprod`
- `assembleDevelopRelease` does **not** match variant `dev`

When several variants match and every match is a substring of the longest one
(e.g. `prod` inside `preProd` for `assemblePreProdRelease`), the longest wins.
Genuinely ambiguous matches are skipped with a warning.

### Selection logging

The resolved build type and every dimension decision are printed by the
`konfigInfo` task on **every** build — including fully cached / UP-TO-DATE
builds with the configuration cache enabled:

```
konfig [app]: BUILD_TYPE = release  (task-name detection matched release in [assembleProdRelease])
konfig [app]: dim 'env' -> 'prod'  (task-name detection: 'prod' found in [assembleProdRelease])
```

---

## Build-type detection

Build type is resolved in priority order:

| Priority | Source              | Example                           |
|----------|---------------------|-----------------------------------|
| 1        | Explicit property   | `-Pkonfig.buildtype=DEBUG`        |
| 2        | Task-name detection | Running `assembleDebug` → `DEBUG` |
| —        | Default             | `RELEASE`                         |

---

## Gradle properties

| Property                                    | Effect                                           |
|---------------------------------------------|--------------------------------------------------|
| `-Pkonfig.buildtype=DEBUG\|RELEASE`         | Forces build type                                |
| `-Pkonfig.dimension.<name>=<variant>`       | Selects a dimension variant                      |
| `-Pkonfig.force`                            | Disables UP-TO-DATE checks — task always re-runs |
| `-Pkonfig.android.buildtypedetection=false` | Disables task-name build-type detection          |
| `-Pkonfig.android.flavordetection=false`    | Disables task-name dimension-variant detection   |

### `konfig.force`

Forces `generateKonfig` to re-run on every build, bypassing Gradle's UP-TO-DATE and build-cache checks.

```bash
./gradlew generateKonfig -Pkonfig.force
./gradlew assembleRelease -Pkonfig.force
```

The flag is presence-based — any value (or no value) enables it.

---

## Accessing generated values

```kotlin
import com.example.app.BuildKonfig

println(BuildKonfig.BUILD_TYPE)    // "debug" or "release"
println(BuildKonfig.IS_DEBUG)      // true (debug) or false (release)
println(BuildKonfig.Env.BASE_URL)  // dimension field
println(BuildKonfig.Env.VARIANT)   // "dev" or "prod"
```

---

## Build-script queries (`konfig.isDebug`, `konfig.getCurrentDimension`)

The same recognition logic that drives generation is queryable from build
scripts — useful for wiring per-build-type dependencies in KMP projects:

```kotlin
konfig {
    dimension("env", defaultTo = "prod") { /* ... */ }
}

dependencies {
    if (konfig.isDebug) implementation(project(":debugImpl"))
    else                implementation(project(":releaseImpl"))
}

val activeEnv: String? = konfig.getCurrentDimension("env")   // "prod", or null if skipped
```

| API                                  | Type                | Description                                    |
|--------------------------------------|---------------------|------------------------------------------------|
| `konfig.isDebug`                     | `Boolean`           | True when the resolved build type is debug     |
| `konfig.isDebugProvider`             | `Provider<Boolean>` | Lazy variant for provider-based wiring         |
| `konfig.currentBuildType`            | `Provider<String>`  | `"debug"` / `"release"`                        |
| `konfig.getCurrentDimension(name)`   | `String?`           | Active variant, or `null` when skipped/unknown |
| `konfig.currentDimension(name)`      | `Provider<String>`  | Lazy variant (absent when skipped/unknown)     |

---

## Using Gradle providers as field values

Lazy `Provider<T>` values are supported — useful for reading Gradle properties or environment variables:

```kotlin
konfig {
    field("API_KEY", providers.gradleProperty("myapp.apiKey"))
    field("CI_BUILD", providers.environmentVariable("CI").map { it.toBoolean() })
}
```

> Do not call `System.getenv()` or `project.findProperty()` directly inside `field()` — these bypass the Provider API and break configuration cache.

---

## Auto source-set wiring

The generated directory (`build/generated/konfig/`) is automatically added as a Kotlin source set for:

- `org.jetbrains.kotlin.multiplatform` → `commonMain`
- `org.jetbrains.kotlin.jvm` → `main`
- `org.jetbrains.kotlin.android` → `main`
- `com.android.application` / `com.android.library` → all variants via the
  `androidComponents` Sources API (AGP 8.1+ required)

The Android wiring uses the modern variant Sources API instead of the
`AndroidSourceSet` DSL, so it keeps working on **AGP 9.2+** where
`android.sourceset.disallowProvider` defaults to `true` (passing providers to
the source-set DSL is rejected). No legacy flag needed.

The `generateKonfig` task is automatically wired as a dependency of all `compileKotlin*` and `sourcesJar` tasks.

---

## Publishing (plugin development)

```bash
# Publish to GitHub Packages (requires gpr.user + gpr.key)
./gradlew publish

# Publish only the plugin marker (fixes resolution without re-uploading the jar)
./gradlew publishKonfigPluginMarkerMavenPublicationToGitHubPackagesRepository

# Publish to local Maven for local testing
./gradlew publishToMavenLocal
```

---

## Development

```bash
# Build and publish to local Maven (primary development loop)
./gradlew publishToMavenLocal

# Run unit tests only
./gradlew test

# Run functional tests (Gradle TestKit — starts real Gradle builds)
./gradlew functionalTest

# Run a specific functional test
./gradlew functionalTest --tests "*dimension with defaultTo*"

# Run all checks (test + functionalTest)
./gradlew check

# Force re-run (skip UP-TO-DATE / cache)
./gradlew functionalTest --rerun-tasks
```
