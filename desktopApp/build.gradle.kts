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
            implementation(project(":library"))
            // Reproductor: necesita VLC (64 bits) instalado en el PC
            implementation("uk.co.caprica:vlcj:4.8.3")
            // Convierte el DEX (Android) de los plugins .cs3 a bytecode JVM
            implementation("de.femtopedia.dex2jar:dex-translator:2.4.38")
            implementation(libs.jackson.module.kotlin)
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
            modules("java.net.http", "java.naming", "java.sql", "jdk.unsupported")
            // El MSI exige MAJOR.MINOR.BUILD: se toma de versionName (gradle/libs.versions.toml)
            packageVersion = libs.versions.versionName.get()

            val iconsRoot = project.file("src/desktop-icons")
            macOS {
                // iconFile.set(iconsRoot.resolve("icon-mac.icns"))
            }
            windows {
                iconFile.set(iconsRoot.resolve("icon-windows.ico"))
                menuGroup = "Lumora"
                shortcut = true
                // GUID fijo: cada MSI nuevo actualiza al instalado en vez de instalarse al lado
                upgradeUuid = "a635097b-9164-41cf-8ed4-e1b4c74072bf"
            }
            linux {
                iconFile.set(iconsRoot.resolve("icon-linux.png"))
            }
        }

        //buildTypes.release.proguard {
        //    configurationFiles.from(project.file("rules.pro"))
        //}
    }
}