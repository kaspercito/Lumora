import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm {}
    sourceSets {
        jvmMain.dependencies {
            implementation(libs.bundles.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinx.collections.immutable)
            implementation(compose.desktop.currentOs) {
                // compose.desktop.currentOs imports the wrong material 2, so we exclude it
                exclude(group = "org.jetbrains.compose.material", module = "material")
            }
            implementation(project(":shared"))
            // Image loading (posters/backdrops): Coil needs a network fetcher + HTTP engine on desktop
            implementation(libs.coil.network.ktor3)
            implementation(libs.ktor.client.java)
            implementation(project(":library"))
            // Reproductor: necesita VLC (64 bits) instalado en el PC
            implementation("uk.co.caprica:vlcj:4.8.3")
            // Convierte el DEX (Android) de los plugins .cs3 a bytecode JVM
            implementation("de.femtopedia.dex2jar:dex-translator:2.4.38")
            implementation(libs.jackson.module.kotlin)
            // OkHttp types for the CloudflareKiller shim, and ASM to repair converted plugin bytecode
            implementation(libs.nicehttp)
            implementation("org.ow2.asm:asm:9.7")
        }
    }
}

// java.lang.System::load has been called by org.jetbrains.skiko.LibraryLoader in an unnamed module
tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

compose.desktop {
    application {
        mainClass = "com.lagradost.cloudstream4.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Lumora"
            // The slimmed-down runtime that jpackage builds by default lacks jdk.crypto.ec (HTTPS to
            // GitHub then fails with SSL errors, so repositories "never download" in the installed
            // app but work from the IDE) and many modules Android plugins need. Ship them all.
            includeAllModules = true
            // El MSI exige MAJOR.MINOR.BUILD: se toma de versionName (gradle/libs.versions.toml)
            packageVersion = libs.versions.versionName.get()
            // Todo lo que haya en desktopApp/resources/windows-x64/ se copia junto a la app instalada
            // (ahí va el VLC portable: resources/windows-x64/vlc/libvlc.dll, libvlccore.dll, plugins/)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            val iconsRoot = project.file("src/desktop-icons")
            macOS {
                // iconFile.set(iconsRoot.resolve("icon-mac.icns"))
            }
            windows {
                iconFile.set(iconsRoot.resolve("icon-windows.ico"))
                menuGroup = "Lumora"
                shortcut = true
                menu = true
                dirChooser = true
                // GUID fijo: cada MSI nuevo actualiza al instalado en vez de instalarse al lado
                upgradeUuid = "a635097b-9164-41cf-8ed4-e1b4c74072bf"
            }
            linux {
                iconFile.set(iconsRoot.resolve("icon-linux.png"))
            }
        }

        jvmArgs("-Xmx2g", "-Dfile.encoding=UTF-8", "--enable-native-access=ALL-UNNAMED")

        //buildTypes.release.proguard {
        //    configurationFiles.from(project.file("rules.pro"))
        //}
    }
}