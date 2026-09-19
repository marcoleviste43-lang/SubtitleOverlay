# Building the APK with GitHub Actions (phone-only)

This project includes `.github/workflows/build-apk.yml`, so GitHub can build the Android APK in the cloud. You do not need Android Studio or a PC.

## Phone-only steps

1. Create a new GitHub repository.
2. Upload the contents of this project to the repository. Make sure the repository root contains `settings.gradle.kts`, `build.gradle.kts`, `app/`, and `.github/`.
3. Open the repository's **Actions** tab.
4. Select **Build Android APK**.
5. If GitHub asks to enable workflows, enable them.
6. Press **Run workflow**.
7. Wait for the workflow to finish with a green check.
8. Open the completed workflow run and scroll to **Artifacts**.
9. Download `Floating-Subtitles-debug-apk`.
10. Extract the downloaded artifact and install `app-debug.apk` on your Android phone.

The workflow uses Java 17, Gradle 8.7, and Android SDK 34 to match this project.

## If you push changes later

A push to the `main` or `master` branch automatically starts another build.
