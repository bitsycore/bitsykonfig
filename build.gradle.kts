plugins {
	`kotlin-dsl`
	`maven-publish`
}

group = "com.bitsycore"
version = providers.gradleProperty("konfig.version").get()

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
	withSourcesJar()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
	compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

repositories {
	mavenCentral()
	google {
		mavenContent {
			includeGroupByRegex(".*google.*")
			includeGroupByRegex(".*android.*")
		}
	}
	gradlePluginPortal()
}

val functionalTest by sourceSets.creating

gradlePlugin {
	testSourceSets(functionalTest)
	plugins {
		register("konfig") {
			id = "com.bitsycore.konfig"
			implementationClass = "com.bitsycore.konfig.KonfigPlugin"
			displayName = "BuildKonfig"
			description = "A Gradle plugin that generates a BuildConfig-like object for Kotlin Multiplatform projects with build-type variant support."
		}
	}
}

configurations[functionalTest.implementationConfigurationName]
	.extendsFrom(configurations.testImplementation.get())

dependencies {
	compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:$embeddedKotlinVersion")
	// 8.7+ needed for the variant Sources API (variant.sources.kotlin) used instead of
	// the AndroidSourceSet DSL (whose provider support is removed by AGP 9.2).
	compileOnly("com.android.tools.build:gradle:8.7.3")
	testImplementation(kotlin("test"))
    add("functionalTestImplementation", gradleTestKit())
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
	description = "Runs functional tests."
	group = "verification"
	testClassesDirs = functionalTest.output.classesDirs
	classpath = functionalTest.runtimeClasspath
	listOf("kotlinVersion", "androidVersion").forEach { setting ->
		providers.gradleProperty("konfig.test.$setting").orNull?.let {
			systemProperty("konfig.test.$setting", it)
		}
	}
}

tasks.check { dependsOn(functionalTestTask) }

// ========================================================
// MARK: Publishing
// ========================================================

fun prop(name: String): String? =
    providers.gradleProperty(name).orNull
        ?: System.getenv(name.replace('.', '_').uppercase())

publishing {
    // The `kotlin-dsl` + `gradlePlugin {}` combo automatically creates two publications:
    //   - "pluginMaven"                       → the real implementation jar (groupId:artifactId:version)
    //   - "konfigPluginMarkerMaven"            → the plugin marker   (pluginId:pluginId.gradle.plugin:version)
    //
    // We must NOT create a third "pluginMaven" manually — that breaks the marker.
    // Instead we configure the existing ones via withType.
    publications.withType<MavenPublication>().configureEach {
        // Only decorate the implementation publication, not the marker
        if (artifactId != "com.bitsycore.konfig.gradle.plugin") {
            artifactId = providers.gradleProperty("konfig.artifactId").get()

            pom {
                name        = providers.gradleProperty("konfig.pom.name").get()
                description = providers.gradleProperty("konfig.pom.description").get()
                url         = providers.gradleProperty("konfig.pom.url").get()

                licenses {
                    license {
                        name = "MIT License"
                        url  = "https://opensource.org/licenses/MIT"
                    }
                }

                developers {
                    developer {
                        id   = providers.gradleProperty("konfig.pom.developer.id").get()
                        name = providers.gradleProperty("konfig.pom.developer.name").get()
                        url  = providers.gradleProperty("konfig.pom.developer.url").get()
                    }
                }

                scm {
                    connection          = providers.gradleProperty("konfig.pom.scm.connection").get()
                    developerConnection = providers.gradleProperty("konfig.pom.scm.developerConnection").get()
                    url                 = providers.gradleProperty("konfig.pom.scm.url").get()
                }
            }
        }
    }

    repositories {
        mavenLocal()

        maven {
            name = "GitHubPackages"
            url  = uri(providers.gradleProperty("konfig.publish.url").get())
            credentials {
                username = prop("gpr.user")
                password = prop("gpr.key")
            }
        }

        maven {
            name = "Bitsycore"
            url  = uri("https://maven.bitsycore.com/releases")
            credentials {
                // props resolve from gradle.properties or env BITSYCORE_MAVEN_USER / BITSYCORE_MAVEN_TOKEN
                username = prop("bitsycore.maven.user")
                password = prop("bitsycore.maven.token")
            }
        }
    }
}
