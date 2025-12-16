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

---

Updated on 2025-12-16.

Project structure:
- Baselined Spring Boot multi-module project
- Established modular architecture with: core, domn (domain), intg (integration), http, and wrkr (worker) modules
- Configured proper module dependencies and build separation

Architecture improvements:
- Added anti-corruption layer to HelloController
- Introduced GreetingResponse DTO to separate API contract from domain model
- Refactored HelloController to return ResponseEntity<GreetingResponse> for better HTTP control

Dependency status check:
- All dependencies current as of this date
- Kotlin 2.2.21 available (project remains on 2.1.0 for stability)
- Spring Boot 3.5.9 expected soon (project on 3.5.8)
