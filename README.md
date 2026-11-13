# Simple Messenger

A simple Android chat app, with a small server it talks to.

- **Everyone:** a group chat with every user on the server.
- **Private chats:** one-to-one chats with each person.
- **Also included:** online status, unread counts, message history, and sign-in with a name and password.

The app is written in plain Java with no libraries, so it builds quickly and the APK is small.

## 1. Start the server

You need [Node.js](https://nodejs.org) 18 or newer on a computer.

```bash
cd server
node server.js
```

It prints the address to type into the app, for example `http://192.168.1.10:3000`. Phones must be on the same Wi-Fi network as that computer. Messages are saved in `server/data.json`.

## 2. Get the APK

Pick one way.

**A. GitHub (nothing to install)**

1. Create a new repository on github.com and upload this whole folder, including the hidden `.github` folder.
2. Open the repository's **Actions** tab. The **Build APK** job starts on its own and takes about 3 minutes.
3. Open the finished run and download **SimpleMessenger-apk** under Artifacts. Unzip it to get `app-release.apk`.

**B. Android Studio**

1. Open the `android` folder in Android Studio and wait for it to finish syncing.
2. Choose **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
3. The APK is saved in `android/app/build/outputs/apk/`.

**C. Command line** (needs JDK 17 and the Android SDK)

```bash
cd android
./gradlew assembleRelease
```

## 3. Install and chat

1. Copy the APK to an Android phone (Android 5.0 or newer) and open it. Allow installing from this source if the phone asks.
2. Enter the server address, a name and a password, then select **Sign in**. A new name creates an account. An existing name signs you back in.
3. Tap **Everyone** or a person to chat.

To test with the Android emulator on the same computer, use the server address `10.0.2.2:3000`.

## Limits

- **Not encrypted.** Messages travel as plain HTTP and are stored as plain text on the server. Use it only on networks you trust, and not for sensitive information.
- **No notifications while the app is closed.** New messages arrive while the app is open or recently used.
- **Text only.** There are no photos, voice notes or calls.
- **Local network by default.** To chat over the internet, run the server on a public host and put it behind HTTPS.
- **Debug key.** The APK is signed with Android's debug key, which is fine for your own phones. To publish on Google Play, sign it with your own key.

## Files

```
server/server.js                     The chat server (no dependencies)
android/app/src/main/java/.../core/  Networking and chat state, plain Java
android/app/src/main/java/...        The three screens: sign in, chats, chat
.github/workflows/build-apk.yml      Builds the APK on GitHub
```
