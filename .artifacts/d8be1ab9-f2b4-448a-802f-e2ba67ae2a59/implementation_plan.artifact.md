# Implementation Plan - Registration and Login Pages

This plan covers the creation of Login and Registration screens using Jetpack Compose, following the MVVM architecture. It also includes setting up navigation between these screens.

## User Review Required

> [!NOTE]
> I will be using standard Material 3 components for the UI. You can later customize the themes and styles in `ui/theme`.

## Proposed Changes

### Dependencies

#### [MODIFY] [libs.versions.toml](file:///D:/Triqx_Android_App/gradle/libs.versions.toml)
Add versions and library definitions for Navigation and ViewModel Compose.

#### [MODIFY] [build.gradle.kts](file:///D:/Triqx_Android_App/app/build.gradle.kts)
Include the new libraries in the project dependencies.

---

### UI & Logic

#### [NEW] [AuthViewModel.kt](file:///D:/Triqx_Android_App/app/src/main/java/com/example/triqx/ui/auth/AuthViewModel.kt)
Handle state for login and registration (email, password, loading states, etc.).

#### [NEW] [LoginScreen.kt](file:///D:/Triqx_Android_App/app/src/main/java/com/example/triqx/ui/auth/LoginScreen.kt)
The login UI with fields for email and password, and a link to the registration page.

#### [NEW] [RegisterScreen.kt](file:///D:/Triqx_Android_App/app/src/main/java/com/example/triqx/ui/auth/RegisterScreen.kt)
The registration UI with fields for name, email, and password.

#### [MODIFY] [MainActivity.kt](file:///D:/Triqx_Android_App/app/src/main/java/com/example/triqx/MainActivity.kt)
Set up `NavHost` to manage transitions between Login and Register screens.

---

## Verification Plan

### Automated Tests
- I will run `gradlew build` to ensure the project compiles with the new dependencies.

### Manual Verification
- Deploy the app to a device/emulator.
- Verify that the Login screen appears first.
- Navigate to the Register screen and back.
- Check that the input fields and buttons are functional.
