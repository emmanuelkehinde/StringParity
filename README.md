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

Early development. The command-line options are in place. Parsing and comparison are in progress, and the plan is in [ROADMAP.md](ROADMAP.md).

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

Run the tests with `./gradlew test`.

## License

MIT. See [LICENSE](LICENSE).
