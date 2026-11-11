# Roadmap

## 0.1.0: first release

- [x] Parse Android `strings.xml` files from a `res` directory, one per `values-<locale>` folder, including escaping and `translatable="false"`.
- [x] Parse iOS String Catalogs (`.xcstrings`), which are JSON, for every locale they contain, including `sourceLanguage` and `"shouldTranslate": false`. Plural and device variations are skipped for now.
- [ ] Report keys missing from either platform and per locale.
- [ ] Normalise placeholders so `%1$s`, `%s`, `%@` and `%d` compare correctly, and report mismatches.
- [ ] Map keys that are named differently on each platform with a YAML file.
- [ ] Text and JSON output, with a non-zero exit code when problems are found.
- [ ] GitHub Actions workflow that builds and tests every pull request.
- [ ] A composite GitHub Action so other projects can run StringParity in CI.

## Later

- Compare plural forms (`<plurals>` against String Catalog plural variations).
- Gradle plugin for Android projects.
- Native binaries and a Homebrew formula, so it runs without a JVM.
- Support older iOS `.strings` and `.stringsdict` files.
- Warn when a translation is much longer than the source string.
- SARIF output for GitHub code scanning.
- `--fix` to add missing keys as stubs.
- HTML report.
- Test fixtures taken from real open-source apps.
- A documentation site.
