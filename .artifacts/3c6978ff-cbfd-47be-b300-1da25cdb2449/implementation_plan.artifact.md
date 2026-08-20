# Add Hilt Dependency Injection

This plan adds Hilt and Dagger to the project to enable Dependency Injection. It involves configuring the build system, setting up the Application class, and annotating the existing Activity and ViewModel.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///D:/GenXAI%20Android%20Projects/Triqx_Android_App/gradle/libs.versions.toml)
- Add Hilt and KSP versions.
- Add Hilt and Hilt-Navigation-Compose libraries.
- Add Hilt and KSP plugins.

#### [MODIFY] [build.gradle.kts (root)](file:///D:/GenXAI%20Android%20Projects/Triqx_Android_App/build.gradle.kts)
- Apply Hilt and KSP plugins (with `apply false`).

#### [MODIFY] [app/build.gradle.kts](file:///D:/GenXAI%20Android%20Projects/Triqx_Android_App/app/build.gradle.kts)
- Apply Hilt and KSP plugins.
- Add Hilt dependencies.

### Application and Android Components

#### [NEW] [TriqxApplication.kt](file:///D:/GenXAI%20Android%20Projects/Triqx_Android_App/app/src/main/java/com/example/triqx/TriqxApplication.kt)
- Create the Application class and annotate it with `@HiltAndroidApp`.

#### [MODIFY] [AndroidManifest.xml](file:///D:/GenXAI%20Android%20Projects/Triqx_Android_App/app/src/main/AndroidManifest.xml)
- Register `TriqxApplication` in the `<application>` tag.

#### [MODIFY] [MainActivity.kt](file:///D:/GenXAI%20Android%20Projects/Triqx_Android_App/app/src/main/java/com/example/triqx/MainActivity.kt)
- Annotate `MainActivity` with `@AndroidEntryPoint`.
- Update `AuthViewModel` retrieval to use Hilt (optional but recommended for complete setup).

#### [MODIFY] [AuthViewModel.kt](file:///D:/GenXAI%20Android%20Projects/Triqx_Android_App/app/src/main/java/com/example/triqx/ui/auth/AuthViewModel.kt)
- Annotate `AuthViewModel` with `@HiltViewModel` and add `@Inject constructor()`.

## Verification Plan

### Automated Tests
- Run `./gradlew build` to ensure the project compiles with Hilt.

### Manual Verification
- Deploy the app to a device/emulator to verify it launches without crashes.
