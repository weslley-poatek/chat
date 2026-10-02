This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/app](./app/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./app/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./app/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./app/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :app:testAndroidHostTest`
- iOS tests: `./gradlew :app:iosSimulatorArm64Test`

### Troubleshooting

#### Xcode: "Unable to locate a Java Runtime"

The Xcode build fails in the **Compile Kotlin Framework** build phase with:

```text
The operation couldn’t be completed. Unable to locate a Java Runtime.
Please visit http://www.java.com for information on installing Java.
```

**Cause.** That phase runs `./gradlew` through `/bin/sh`, and Xcode never loads `~/.zshrc`.
A JDK that only your shell knows about (SDKMAN, jenv, an `export JAVA_HOME` in `.zshrc`) is
invisible to it, so Gradle falls back to the macOS `java` stub, which only sees JDKs
registered with the system. The same build works in your terminal because the terminal
does load `.zshrc`.

**Check.** Run these from the repository root:

```bash
/usr/libexec/java_home -V
```

If it prints `Unable to locate a Java Runtime`, no JDK is registered with macOS and Xcode
cannot build. This reproduces what Xcode sees (no `.zshrc`, minimal `PATH`):

```bash
env -i HOME="$HOME" PATH=/usr/bin:/bin:/usr/sbin:/sbin ./gradlew --version
```

It fails with the same message while the problem exists, and prints the Gradle version once
it is fixed.

**Fix.** Register a JDK (21 or newer) with macOS. Pick one:

- Android Studio's bundled JDK, if you have Android Studio installed. No download needed.
  The link breaks if the app is moved or renamed, so repeat it then:

  ```bash
  mkdir -p ~/Library/Java/JavaVirtualMachines
  ln -s "/Applications/Android Studio.app/Contents/jbr" ~/Library/Java/JavaVirtualMachines/android-studio-jbr.jdk
  ```

- A system-wide Temurin JDK (asks for your admin password):

  ```bash
  brew install --cask temurin@21
  ```

- A JDK you already manage with SDKMAN. SDKMAN's folders are not macOS `.jdk` bundles, so this
  wraps one in a small bundle that points at it. Set `JDK` to a version listed by
  `sdk list java` that is installed:

  ```bash
  JDK=21.0.11-tem
  test -d "$SDKMAN_DIR/candidates/java/$JDK" || echo "Not found: $SDKMAN_DIR/candidates/java/$JDK (stop here)"
  V=${JDK%%-*}
  B=~/Library/Java/JavaVirtualMachines/sdkman-$JDK.jdk
  mkdir -p "$B/Contents/MacOS"
  ln -s "$SDKMAN_DIR/candidates/java/$JDK" "$B/Contents/Home"
  ln -s ../Home/lib/libjli.dylib "$B/Contents/MacOS/libjli.dylib"
  cat > "$B/Contents/Info.plist" <<EOF
  <?xml version="1.0" encoding="UTF-8"?>
  <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
  <plist version="1.0">
  <dict>
    <key>CFBundleIdentifier</key><string>sdkman.$JDK.jdk</string>
    <key>CFBundleName</key><string>SDKMAN $JDK</string>
    <key>CFBundleExecutable</key><string>libjli.dylib</string>
    <key>CFBundlePackageType</key><string>BNDL</string>
    <key>CFBundleInfoDictionaryVersion</key><string>7.0</string>
    <key>JavaVM</key>
    <dict>
      <key>JVMCapabilities</key><array><string>CommandLine</string></array>
      <key>JVMPlatformVersion</key><string>$V</string>
      <key>JVMVendor</key><string>SDKMAN</string>
      <key>JVMVersion</key><string>$V</string>
    </dict>
  </dict>
  </plist>
  EOF
  ```

  The bundle follows that one installed version: if you uninstall it with `sdk uninstall`,
  delete the bundle and repeat this with the version you keep.

Then run the two **Check** commands again: the first must list a JDK and the second must
print a Gradle version. Build from Xcode again afterwards; no project change is needed.

The JDK registered with macOS only launches Gradle. The build itself runs on the Java 21
(Azul Zulu) toolchain pinned in `gradle/gradle-daemon-jvm.properties`, which Gradle
downloads to `~/.gradle/jdks` on the first build, so that first build needs network access.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…