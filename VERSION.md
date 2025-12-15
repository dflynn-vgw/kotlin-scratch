# Version updates

Updated on 2025-12-15.

The following dependencies and tools were updated:

- Gradle Wrapper: 9.0.0 → 9.2.1
- Kotlin (plugin, stdlib, test libs): 2.2.20 → 2.2.21
- JUnit Jupiter: 5.10.1 → 5.14.1
- Spotless Gradle Plugin: 7.2.1 → 8.1.0
- ktlint (via Spotless): 1.7.1 → 1.8.0
- Gradle Versions Plugin: 0.52.0 → 0.53.0

Notes:
- Java toolchain remains on Java 21.
- The foojay-resolver-convention plugin stays at 1.0.0 (latest at the time of update).
- Build and formatting checks passed: `:app:test` and `:app:spotlessCheck`.
