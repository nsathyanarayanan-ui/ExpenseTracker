# Building & Installing from Your Phone (Termux, no laptop)

This builds the app with command-line Gradle inside Termux and installs the
resulting APK — no Android Studio GUI required. It's doable but takes real
time and patience on a phone. Budget ~1-2 hours the first time, ~7GB free
storage, and a phone with 4GB+ RAM for a smoother experience.

## 1. Install Termux (correct source matters)

Use **F-Droid**, not the Play Store version (Play Store Termux is outdated
and broken for this). Install F-Droid from https://f-droid.org, then install
**Termux** and **Termux:API** through it.

## 2. Base packages

```
pkg update -y && pkg upgrade -y
pkg install -y openjdk-17 wget unzip git gradle
termux-setup-storage
```
Grant the storage permission popup when it appears.

## 3. Get the project onto the phone

Easiest: download the zip I gave you directly on the phone (e.g. into
`Downloads`), then in Termux:

```
cd ~
cp /sdcard/Download/ExpenseTracker_AndroidStudio.zip .
unzip ExpenseTracker_AndroidStudio.zip
cd ExpenseTracker
```

## 4. Install the Android command-line SDK

```
cd ~
mkdir -p android-sdk/cmdline-tools
cd android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-11076708_latest.zip
mv cmdline-tools latest
cd ~
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
```

Add those two `export` lines to `~/.bashrc` so you don't retype them every
session:
```
echo 'export ANDROID_HOME=$HOME/android-sdk' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools' >> ~/.bashrc
```

Now install the actual SDK pieces the project needs (this downloads several
hundred MB — do it on wifi):
```
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

## 5. Point the project at the SDK

```
cd ~/ExpenseTracker
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

## 6. Build the debug APK

```
gradle assembleDebug --no-daemon
```

First run compiles Kotlin + resolves dependencies — expect it to take a
while and to feel like it's "stuck" at times; that's normal on mobile CPUs.
If it fails with an out-of-memory error, lower `-Xmx1536m` in
`gradle.properties` to `-Xmx1024m` and retry.

On success, the APK is at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## 7. Install it

Copy it to shared storage so your file manager can see it, then open it:
```
cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/
```
Open your phone's **Files** app, go to Download, tap `app-debug.apk`.
Android will prompt "Install unknown apps" the first time — allow it for
your file manager (or for Termux, if you used `termux-open` instead). Then
tap Install.

## Common snags

- **"SDK license not accepted"** → re-run `yes | sdkmanager --licenses`
- **Build hangs/OOM-kills itself** → close other apps, reduce `-Xmx`, retry
  with `--no-daemon` (already in the command above)
- **"Unsupported class file major version"** → make sure `openjdk-17` is
  what's active: `java -version` should show 17.x
- **Very slow first build** → subsequent builds reuse the Gradle cache and
  are much faster; only the first one is painful

## Reality check

If this feels like too much friction, the cloud-IDE route (GitHub
Codespaces / Gitpod, opened in your phone's browser) does the same steps
on a remote machine with more RAM and a real terminal — often smoother
than Termux on an actual phone, at the cost of needing an internet
connection throughout.
