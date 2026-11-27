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

Early development. `--android` reads a `res` directory or one `strings.xml`, and `--ios` reads a String Catalog. Each prints the number of strings per locale. With both, StringParity also reports missing keys and placeholder mismatches. Plural checks are not written yet. The plan is in [ROADMAP.md](ROADMAP.md).

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

Give `--android`, `--ios` or both. At least one is required. `--mapping` and `--format json` are optional and need both.

With both, StringParity compares the keys and placeholders and prints a report like this:

```text
Missing on Android (1):
  Welcome back
Locale fr-CA is missing on iOS (2 strings on Android)
Locale fr:
  Missing on Android (1):
    bye
Placeholder mismatches (2):
  price in default, types differ: Android %.2f, iOS %lld
  greeting in fr, count differs: Android %1$s %2$d, iOS %@
Placeholders that differ from the default (1):
  greeting in fr on iOS, count differs: %@, default %@ %d
Found 3 missing items and 3 placeholder mismatches.
```

It exits with code 1 when anything is missing or a placeholder doesn't match, and 0 otherwise. Missing keys are checked in three ways:

- Default-locale keys that only one platform has.
- Locales that only one platform has.
- Keys translated in a locale on one platform and not on the other. A key that neither platform translates in a locale is not reported.

Keys marked not translatable on the platform that has them are left out, since they are often platform-only values such as API keys. Locale tags must match exactly, so Android `values-zh-rCN` (`zh-CN`) and iOS `zh-Hans` count as different locales. If Android has a folder for the iOS source language, such as `values-en`, it is compared with the iOS source strings.

### JSON output

Pass `--format json` to print the report as one JSON object instead of text. The default is `--format text`. For the report above, the output is:

```sh
./app/build/install/stringparity/bin/stringparity \
  --android path/to/app/src/main/res \
  --ios path/to/Localizable.xcstrings \
  --format json
```

```json
{
    "missingKeys": {
        "android": [
            "Welcome back"
        ],
        "ios": []
    },
    "missingLocales": {
        "android": [],
        "ios": [
            {
                "locale": "fr-CA",
                "strings": 2
            }
        ]
    },
    "localeGaps": [
        {
            "locale": "fr",
            "missingOnAndroid": [
                "bye"
            ],
            "missingOnIos": []
        }
    ],
    "placeholderMismatches": [
        {
            "key": "price",
            "locale": "default",
            "reason": "types differ",
            "android": [
                "%.2f"
            ],
            "ios": [
                "%lld"
            ]
        },
        {
            "key": "greeting",
            "locale": "fr",
            "reason": "count differs",
            "android": [
                "%1$s",
                "%2$d"
            ],
            "ios": [
                "%@"
            ]
        }
    ],
    "placeholderDrift": [
        {
            "key": "greeting",
            "locale": "fr",
            "platform": "ios",
            "reason": "count differs",
            "translated": [
                "%@"
            ],
            "default": [
                "%@",
                "%d"
            ]
        }
    ],
    "summary": {
        "missing": 3,
        "placeholderMismatches": 3,
        "problems": 6
    }
}
```

Each field is always present, in this order, and an empty list means nothing was found. The fields are:

- `missingKeys`: default-locale keys missing on `android` and on `ios`.
- `missingLocales`: locales missing on `android` and on `ios`. `strings` is the number of strings the other platform has in that locale.
- `localeGaps`: locales both platforms have, with the keys each one is missing.
- `placeholderMismatches`: keys whose placeholders differ between the platforms, with the placeholders on each side.
- `placeholderDrift`: translations whose placeholders differ from the default value on the same platform.
- `summary`: the number of missing items, the number of placeholder mismatches, and their total in `problems`.

Keys and locales are sorted, and placeholder entries list the default locale first and then each locale and key in sorted order, so the same input always gives the same output. The exit codes are the same as for text. Only the JSON object goes to stdout. The per-locale counts are left out, and errors such as an unreadable file still go to stderr.

### Keys named differently on each platform

If the same string has one key on Android and another on iOS, list the pair in a YAML file and pass it with `--mapping`:

```yaml
# Android key on the left, iOS key on the right.
keys:
  login_title: Log in
  settings_header: Settings
```

```sh
./app/build/install/stringparity/bin/stringparity \
  --android path/to/app/src/main/res \
  --ios path/to/Localizable.xcstrings \
  --mapping key-mapping.yaml
```

StringParity renames each listed Android key to its iOS key before comparing, in every locale. The two strings are then checked for missing translations and placeholder mismatches like any other pair, and the report uses the iOS key. Keys not in the file are compared by name. The file is invalid, and the command exits with code 1, when it is not valid YAML, has an entry other than `keys`, has a key or value that is not text, or maps two Android keys to the same iOS key.

### Placeholders

StringParity reads the format placeholders in each value and turns each one into an argument position and a type:

| Type | Android | iOS |
| --- | --- | --- |
| string | `%s` | `%@`, `%s` |
| integer | `%d`, `%x`, `%o` | `%d`, `%i`, `%u`, `%ld`, `%lld`, `%x` |
| float | `%f`, `%.2f`, `%e`, `%g` | `%f`, `%.2f`, `%e`, `%g` |

The position is the explicit index, as in `%2$d`, or the order of appearance among placeholders without one. So `%1$s %2$d` on Android matches `%@ %ld` on iOS, and `%2$ld ... %1$@` in a translation that swaps the words. A position used twice counts once. `%%` is a literal percent sign and is skipped. Flags, width, precision and length modifiers such as `l`, `ll` and `h` don't change the type.

Placeholders are checked in two ways, and each difference counts as one mismatch:

- For a key both platforms have, the default values are compared, and so is each locale where both platforms translate the key. The report gives the key, the locale and the placeholders on each side.
- Each translation is compared with the default value on its own platform, for every translatable default key, including keys only one platform has.

A wrong translation can therefore be reported twice: once against the other platform and once against its own default.

Some cases are not handled yet:

- A `%` followed by a space, as in `50% off`, is read as text. Other text after a lone `%` may be read as a placeholder, for example `5%d` or `100%s`.
- Android's `formatted="false"` is not read, so such a string is still checked.
- `%c`, `%b`, a `*` width such as `%*d`, and Android date conversions such as `%tY` are not read as placeholders.
- Placeholders inside plural and device variations are not checked, because those strings are skipped.

Run the tests with `./gradlew test`.

## License

MIT. See [LICENSE](LICENSE).
