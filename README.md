## Patched FlorisBoard

This directory contains a patch for building [FlorisBoard](https://github.com/florisboard/florisboard) with MPClipboard integration.

## Installation from pre-build F-Droid source

Can be installed from [GitHub Pages](https://iliabylich.github.io/florisboard-mpclipboard/repo/), requires F-Droid client.

## Build Process

Release builds of Android apps require a signing key (can be self-signed). However (**and this is important**) MPClipboard and its clients must be built with the same signing key. You can generate it yourself with

```sh
keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias release
```

Once it's generated **make sure to save it**.

[Mise](https://mise.jdx.dev/) is the tool that is used to download and setup all dependencies:

```sh
mise install # to download and install Java/Gradle/other build tools
mise setup:android-sdk # download and install all build dependencies of Florisboard
```

Then dump these 2 variables into `.env`:

```
ANDROID_KEYSTORE_PATH=/path/to/generated.jks
ANDROID_KEYSTORE_PASSWORD=<password that you entered during key generation>
```

Then finally build the app:

```sh
mise diff:apply
mise build:release
```

It takes quite a lot of time to build it, but once it's done the APK file will be available at:

```
./florisboard/app/build/outputs/apk/release/app-release.apk
```

Debug builds are preferred if you are trying to change/debug something, replace the word "release" in all mise commands above with "debug", the app still must be signed, so ENV variables are still required.
