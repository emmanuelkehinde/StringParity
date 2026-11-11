# StringParity

StringParity checks that an Android app and an iOS app ship the same strings. It reads Android `strings.xml` files and an iOS String Catalog (`.xcstrings`), then reports:

- keys that exist on one platform but not the other
- locales where a string is translated on one platform and missing on the other
- placeholders that don't match, such as `%1$s` on Android against `%@` on iOS
- plural forms that differ between platforms

It exits with a non-zero code when it finds a problem, so it can fail a CI build before mismatched strings reach users.

## Who it's for

Teams that build the same app natively for Android and iOS and keep two sets of string files in step by hand.

## Status

Early development. `--android` reads a `res` directory or one `strings.xml`, and `--ios` reads a String Catalog. Each prints the number of strings per locale. With both, StringParity also reports missing keys. Placeholder and plural checks are not written yet. The plan is in [ROADMAP.md](ROADMAP.md).

In a String Catalog, the `sourceLanguage` locale is listed as `default` so it lines up with Android's `values` folder. A key with no source translation uses the key as its value, as Xcode does. Keys marked `"shouldTranslate": false` count as not translatable. Plural and device variations are skipped and counted, because Android `<plurals>` are not read yet either.

## Built with

- Kotlin on the JVM (Java 17 or later)
- [Clikt](https://ajalt.github.io/clikt/) for the command line
- Gradle with the Kotlin DSL

## Running it

```sh
git clone https://github.com/emmanuelkehinde/StringParity.git
cd StringParity
./gradlew installDist
./app/build/install/stringparity/bin/stringparity \
  --android path/to/app/src/main/res \
  --ios path/to/Localizable.xcstrings
```

Give `--android`, `--ios` or both. At least one is required.

With both, StringParity compares the keys and prints a report like this:

```text
Missing on Android (1):
  Welcome back
Locale fr-CA is missing on iOS (2 strings on Android)
Locale fr:
  Missing on Android (1):
    bye
Found 3 missing items.
```

It exits with code 1 when anything is missing and 0 when nothing is. Missing keys are checked in three ways:

- Default-locale keys that only one platform has.
- Locales that only one platform has.
- Keys translated in a locale on one platform and not on the other. A key that neither platform translates in a locale is not reported.

Keys marked not translatable on the platform that has them are left out, since they are often platform-only values such as API keys. Locale tags must match exactly, so Android `values-zh-rCN` (`zh-CN`) and iOS `zh-Hans` count as different locales. If Android has a folder for the iOS source language, such as `values-en`, it is compared with the iOS source strings.

Run the tests with `./gradlew test`.

## License

MIT. See [LICENSE](LICENSE).
