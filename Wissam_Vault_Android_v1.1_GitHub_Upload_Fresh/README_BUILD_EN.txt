Wissam Vault Android / Smart Bridge v1.1

Android application source. Not a compiled APK. Designed for existing Windows
Local Update Installer v4.7 HTTP LAN server (port 18764).

Requirements: Android Gradle Plugin 8.5.2, Gradle 8.7, Java 17, Android SDK 35.
Build: gradle :app:assembleDebug
Output: app/build/outputs/apk/debug/app-debug.apk

No dependencies besides Android Gradle Plugin and SDK.
Use included GitHub Actions workflow if Android Studio isn't available.

First-run host field must be RFC1918 private IPv4 (no http://). Pair with
8-digit PIN from Windows bridge. The trusted token is AndroidKeyStore-encrypted.

The app stores downloaded files in app-private storage and registers a read-only
content provider for opening them in other apps. ACTION_CREATE_DOCUMENT exports
copies. On uninstall/clear-data, vault files are lost. File transfer is HTTP
on the local LAN and must not be used on untrusted Wi-Fi networks.

Phone <-> PC: upload/download; receipt after file save.
Text snippets: one-tap clipboard copy, explicit clipboard paste, shared snippets with receipts.
