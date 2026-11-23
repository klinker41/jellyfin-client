# Google Play Store Release Publishing

Jenkins automatically builds a release-signed Android App Bundle
(`.aab`) for `xyz.klinker.jellyfin` and publishes it to the Google Play
`internal` and `alpha` (Closed testing) tracks via
`fastlane android publish_release`.

Until both the signing keystore and Google Play Service Account credentials are
configured in Jenkins, builds still compile and archive an unsigned `.aab` but
skip the Google Play upload step.

## 1. Generate the Android Upload Keystore

If you do not already have an upload keystore for `xyz.klinker.jellyfin`,
generate one locally on a trusted machine:

```bash
keytool -genkeypair -v \
  -keystore jellyfin-upload.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Keep an offline backup of `jellyfin-upload.jks` and its passwords in a password
manager. Never commit keystores, `key.properties`, or service-account JSON
files to Git.

## 2. Create a Google Play Service Account JSON Key

1. Open Google Cloud Console and select (or create) the project linked to your
   Play Developer account.
2. Enable the **Google Play Android Developer API**.
3. Create a **Service Account** under **IAM & Admin -> Service Accounts**,
   create a **JSON** key, and download it (for example
   `play-service-account.json`).
4. Open **Google Play Console -> Users and permissions -> Invite new users**.
5. Enter the service account's email address (`...@...iam.gserviceaccount.com`).
6. Grant app-level permissions on `xyz.klinker.jellyfin` for:
   - **View app information and download bulk reports (read-only)**
   - **Release apps to testing tracks** (and **Manage testing tracks and edit
     tester lists** if needed)
7. Click **Invite user** and confirm. Note that Play Console API permission
   propagation can take up to 24 hours, though it usually takes effect faster
   after editing the app's Play Store listing or saving a draft.

## 3. Add Credentials to Jenkins

Open **Manage Jenkins -> Credentials -> System -> Global credentials -> Add
Credentials** and create these five credentials:

| Credential ID | Kind | Env Var Binding | Value |
| --- | --- | --- | --- |
| `jellyfin-android-keystore` | Secret file | `ANDROID_KEYSTORE_FILE` | `jellyfin-upload.jks` |
| `jellyfin-android-keystore-password` | Secret text | `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `jellyfin-android-key-alias` | Secret text | `ANDROID_KEY_ALIAS` | Key alias (e.g. `upload`) |
| `jellyfin-android-key-password` | Secret text | `ANDROID_KEY_PASSWORD` | Key password |
| `google-play-service-account` | Secret file | `PLAY_SERVICE_ACCOUNT_JSON` | Service account JSON |

## 4. Bind Credentials in the `jellyfin-client` Job

1. Open the `jellyfin-client` job in Jenkins and click **Configure**.
2. Under **Build Environment**, enable **Use secret text(s) or file(s)**.
3. Add bindings matching the table above:
   - **Secret file**: Variable `ANDROID_KEYSTORE_FILE` ->
     `jellyfin-android-keystore`
   - **Secret text**: Variable `ANDROID_KEYSTORE_PASSWORD` ->
     `jellyfin-android-keystore-password`
   - **Secret text**: Variable `ANDROID_KEY_ALIAS` ->
     `jellyfin-android-key-alias`
   - **Secret text**: Variable `ANDROID_KEY_PASSWORD` ->
     `jellyfin-android-key-password`
   - **Secret file**: Variable `PLAY_SERVICE_ACCOUNT_JSON` ->
     `google-play-service-account`
4. Click **Save**.

## 5. Perform the One-Time Manual First Upload

Google Play requires the very first `.aab` for `xyz.klinker.jellyfin` to be
uploaded manually in Google Play Console before the Play Developer API accepts
automated uploads:

1. Bind at least the four `ANDROID_KEYSTORE_*` credentials first and run a
   Jenkins build so Jenkins archives a release-signed `.aab` (without
   `PLAY_SERVICE_ACCOUNT_JSON` bound yet, Jenkins skips the API upload and
   still archives the signed `.aab`).
2. Download `app/build/outputs/bundle/release/*.aab` from the Jenkins build
   artifacts.
3. In Google Play Console, open **Testing -> Closed testing (Alpha)** (or
   **Internal testing**), upload that signed `.aab`, and roll out the initial
   release.
4. Once `google-play-service-account` (`PLAY_SERVICE_ACCOUNT_JSON`) is also
   bound in Jenkins, every subsequent successful build automatically runs
   `fastlane android publish_release` to upload the next release build to the
   `internal` track and promote the same build to the `alpha` (Closed testing)
   track.

> [!NOTE]
> If your Play Console app is still in **Draft** state while testing automated
> uploads, set `PLAY_RELEASE_STATUS=draft` in the job's environment until your
> first release is published, then remove it so releases default to `completed`.

## 6. Versioning and Overrides

- **Version code**: Computed automatically as
  `app.versionCode * 10000 + BUILD_NUMBER` from `gradle.properties`, or
  `max(Play Store version codes) + 1` if Google Play already has a higher code
  on any track (`production`, `beta`, `alpha`, `internal`).
- **Version name**: `app.versionName` from `gradle.properties`, unchanged.
- **Optional environment overrides**:
  - `PLAY_TRACKS` (default: `internal,alpha`): comma-separated tracks. The
    build is uploaded to the first track and promoted to the others.
  - `PLAY_RELEASE_STATUS` (default: `completed`)
  - `PLAY_PACKAGE_NAME` (default: `xyz.klinker.jellyfin`)
