# Skippy

Android app (Kotlin + Jetpack Compose) that loads your timetable from the internal **Zeus** API, lets you record
attendance and tells you which classes you can skip while staying above the required attendance (default 60 %).

## Build
1. Open the `Skippy` folder in **Android Studio** (JDK 17) and let Gradle sync.
2. Fill in the Microsoft sign-in settings (below), then Run on your phone.

## Microsoft sign-in (MSAL)
MSAL needs the app's **signature hash** in three places. With the debug key:

    keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android -keypass android | openssl sha1 -binary | openssl base64

1. `gradle.properties` -> `MSAL_SIGNATURE_HASH=<hash>` (raw base64, used in the manifest).
2. `app/src/main/res/raw/auth_config.json` -> `redirect_uri`: `msauth://com.skippy.app/<hash, URL-encoded>` (`/` -> `%2F`, `+` -> `%2B`, `=` -> `%3D`).
3. The redirect URI must also be registered on the Azure app for that Client ID. **If it is not, Microsoft
   refuses the sign-in (AADSTS50011 / redirect URI mismatch)**: use "paste an access token" in the app instead,
   or register your own Azure app and put its Client ID in `auth_config.json`.

`Auth.SCOPES` (in `Auth.kt`) is `User.Read` by default: replace it with the scope the official app requests.

The token is never logged. A pasted token is stored encrypted (AES-GCM, key in the Android Keystore).

## Data
- `POST api/reservation/filter/displayable` (one week per request, `groups: [<id>]`) -> reservations.
- `GET api/reservation/{id}/details` -> shown in the "Details" dialog of a session.
- First sync: from the school-year start (editable) to ~30 weeks ahead. Then a quick refresh (last week to +9 weeks)
  every 6 h in the background.
- `idType` is a number: name each type (CM, TD, TP...) in Settings > Activity types.

## Rule
For each (subject, activity type), on **past** sessions only: `rate = (present + excused) / answered`.
Skips still allowed in a row: `floor((100*present - pct*answered) / pct)`. Unanswered past sessions are ignored.
