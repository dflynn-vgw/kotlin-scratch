# Contributing

This project uses a local Git pre-commit hook for formatting and a GitHub Actions workflow for CI. This guide explains how both are set up and how to work with them.

## Prerequisites
- JDK 21 (Temurin recommended)
- Gradle Wrapper (use `./gradlew`)

## Pre-commit hook (formatting)
Location: `.githooks/pre-commit`

What it does when you commit:
- Temporarily stashes unstaged changes (keeps your index intact).
- Runs `:app:spotlessApply` to auto-fix formatting.
- Re-stages only the files you had staged.
- Runs `:app:spotlessCheck` to verify; blocks the commit if issues remain.
- Restores your unstaged changes.

Enable the hook after cloning (one-time):
```bash
# from repo root
git config core.hooksPath .githooks
# (executable bit is tracked; only run if needed)
chmod +x .githooks/pre-commit
```

Useful commands:
```bash
# Manually fix formatting
./gradlew :app:spotlessApply
# Verify formatting
./gradlew :app:spotlessCheck
# Full build + tests
./gradlew :app:build
```

Bypassing the hook (not recommended):
```bash
git commit --no-verify -m "..."
```

Troubleshooting:
- If the hook reports it couldn’t re-apply your unstaged changes, resolve conflicts, then continue.
- If Gradle download or plugin resolution fails, re-run the commit (transient network issues).

## Continuous Integration (GitHub Actions)
Workflow file: `.github/workflows/ci.yml`

Triggers:
- `push` and `pull_request` on any branch
- Ignores changes under `docs/**` and any `*.md` files

What CI runs:
1. Checkout
2. Gradle wrapper validation
3. Java 21 setup (Temurin)
4. Gradle setup with caching
5. `:app:spotlessCheck`
6. `:app:build` (compiles and runs tests)

Re-running a workflow:
- GitHub UI: Actions tab → open the run → Re-run (or on PR: Checks tab → Re-run).
- GitHub CLI:
  ```bash
  gh run list --workflow "CI" -L 5
  gh run rerun <run-id>
  gh run rerun <run-id> --rerun-failed
  ```
- Push an empty commit:
  ```bash
  git commit --allow-empty -m "ci: retrigger"
  git push
  ```

Notes:
- Cache restore/save warnings can occur during service outages; builds usually succeed, and retries often clear the warnings.

## Local development checklist
- Write code, then run:
  ```bash
  ./gradlew :app:spotlessCheck :app:build
  ```
- Commit normally; the pre-commit hook will format and verify.
