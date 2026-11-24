# Home Assistant TV

A small Android TV / Google TV app that shows a Home Assistant dashboard full-screen and makes it easy to drive with the TV remote.

Target devices: Xiaomi Mi Box (Gen 3), Chromecast with Google TV, and other Android TV 6.0+ boxes. It also runs on phones and tablets.

## What it does

- Opens on an **area picker** with **Main House**, **Upstairs House** and **Irrigation**. Choosing one opens that area's dashboard full-screen, with the sidebar hidden.
- **Arrow keys** move a bright teal highlight between tiles, the view tabs along the top, and buttons, including inside pop-ups. Left/Right stay on the same row.
- **OK / Select** presses the highlighted item, e.g. toggles a light.
- **Back** closes an open pop-up first, then goes back within the area, then returns to the area picker. On the picker, press Back twice to exit.
- **Long-press Back** (or the Menu key) opens **Settings**.
- Keeps the screen on while the dashboard is open (this can be turned off).
- If Home Assistant can't be reached, it shows a message and retries every 10 seconds.

## Install on the TV

1. On GitHub, open **Actions → Build APK**, pick the latest run, and download the **HomeAssistantTV-apk** artifact (a zip containing the `.apk`).
2. Copy the APK to the TV and install it. Use **Send Files to TV**, or the **Downloader** app, or run `adb install HomeAssistantTV-xxxxxxx.apk`.
   - Allow installs from unknown sources for the app you use to install it.
3. Open **Home Assistant TV** from the TV's app row.

Every build is signed with the same key (`app/tv-signing.keystore`), so a new version installs over the old one without losing your sign-in.

## First-time sign-in

Pick one of these:

- **Sign in once on the TV.** Enter your Home Assistant username and password with the on-screen keyboard, and tick *Keep me logged in*. The app remembers it.
- **Access token, no typing on the TV.** In Home Assistant, open your profile → **Security** → **Long-lived access tokens** → *Create token*. Paste the token into the app's **Settings → Access token**. The easiest way is to type it with the Google TV app on your phone, which has a phone-keyboard option for the TV.

To sign out, open **Settings → Sign out / clear data**.

## Settings

| Setting | Default | Notes |
|---|---|---|
| Home Assistant address | `http://192.168.0.29` | Add `:8123` if your server uses that port. |
| Main House page | `/main-house/home` | Dashboard opened for Main House. |
| Upstairs House page | `/upstairs-house/home` | Dashboard opened for Upstairs House. |
| Irrigation page | `/irrigation-control/control` | Dashboard opened for Irrigation. |
| Access token | *(empty)* | Optional automatic sign-in. |
| Zoom % | `100` | Try 125–150 if the tiles look small on a big screen. |
| Keep screen on | on | |

## Building locally

You need Android Studio (or JDK 17 + Android SDK 35) and Gradle 8.10+. Then run:

```
gradle assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`.

## How the remote navigation works

The file `app/src/main/assets/tvnav.js` is injected into every Home Assistant page. It looks through Home Assistant's shadow DOM for focusable controls (tiles, tabs, buttons, switches), picks the nearest control in the pressed direction, and highlights it. It includes view tabs that only become focusable when active ("roving focus"). It skips the sidebar and its ☰ button, because the app's area picker replaces them. When a pop-up is open, it limits navigation to that pop-up.
