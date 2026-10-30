Wissam QuickShare Android 1.2 (companion installation)
====================================================
This is an Android Studio/Gradle source project, NOT an installable APK.
The package ID is com.wissam.smartbridge.quickshare, intentionally distinct
from the existing com.wissam.smartbridge app installed from GitHub's debug APK.
Do not uninstall the old app: its token and stored files stay separate.

For a GitHub repository, upload this project's files under:
  Wissam_Vault_Android_v1.2_QuickShare/
and upload the root workflow as:
  .github/workflows/build-quickshare.yml
Run Actions -> Build Wissam QuickShare APK -> Run workflow (main).
Download artifact WissamQuickShare-APK and extract app-debug.apk.

The Windows server v5.1.1 needs no update: /upload and /api/text/send
are already compatible with the companion app. The user must set an
auto-project destination inside Windows if desired.

The first time, open the Windows bridge and use its local IP and PIN to
pair the new companion once. Subsequent Android ACTION_SEND shares will
start transferring immediately. Keep the device on the same trusted LAN.

Important: Sharing from ChatGPT may send a URL rather than the downloaded ZIP.
If so, download ZIP first then share the file from Android Files.
There is no background Downloads scan or automatic ChatGPT download hook.

The Android debug APK built in Actions is signed with an ephemeral signing
key unless the workflow is configured with a persistent private keystore.
Future upgrades of this companion may need a stable signing setup. Never
commit a signing private key or PIN to a public repository.
