# Changelog

Written for **consumers** of this library — people whose pipelines call these steps —
not for whoever wrote the commit. The question it answers is: *what do I get, and what
might break, if I move my pin from one version to the next?*

Versions follow [SemVer](https://semver.org): `v<major>.<minor>.<patch>`.

| Bump | Means |
|---|---|
| patch | a fix; behaviour unchanged |
| minor | something new and optional; existing calls keep working |
| **major** | **a caller was broken.** Renaming a config key or making an optional key required counts, however small the diff |

---

## Unreleased

Everything on `main` since `v1.0.0`. Pin to `@v1.0.0` if you want none of it.

### Added
- `buildJavaP9` — a real Maven build on a container agent: compile, test, package,
  archive, with the `.m2` cache on a named volume. Needs a *Pipeline from SCM* or
  Multibranch job, because an inline script job has no source checked out.
- `withCloudsmithP10` — binds Cloudsmith credentials for the length of one block.
- `buildJavaP10` — `buildJavaP9` plus a Publish stage, guarded by branch, with the
  generated `settings.xml` deleted in a `finally`.
- `cpsDemoP12`, `versionInfoP12` and `com.learning.phase12.VersionParser` — CPS and
  `@NonCPS` demonstrations, plus a parser that returns only serializable values.
- `buildAppP13` — `buildAppP4` with the config key `name` renamed to `appName`.
  **Both spellings work**; the old one logs a deprecation warning. See *Deprecations*.
- Unit tests (`test/`, `pom.xml`): run with
  `docker run --rm -v "$PWD":/app -w /app maven:3.9-eclipse-temurin-17 mvn -B test`.

### Changed
- `helloPipelineP7` now echoes which library version it is running from. Cosmetic, but
  it is how the `@main` vs `@v1.0.0` difference is demonstrated.

### Deprecations
| Deprecated | Use instead | Still works until |
|---|---|---|
| `buildAppP13(name: …)` | `buildAppP13(appName: …)` | the next **major** release |

Nothing has been removed. No consumer action is required yet.

---

## v1.0.0 — the beginner course library

First tagged release. Frozen at the end of Phase 7.

### Added
- `helloP2` — a step with no arguments.
- `greetP3(name)` — a positional argument.
- `buildAppP3(appName)` + `buildAppP3.cleanup()` — a second method on a step.
  **No input validation**; kept deliberately, to contrast with `buildAppP4`.
- `buildAppP4(Map)` — Map configuration, defaults, and an allow-list check on `name`
  that rejects shell metacharacters.
- `greetP5(Map)` backed by `com.learning.phase05.Greeter` — logic in a `src/` class.
- `bannerP6`, `buildInfoP6` — files from `resources/`, one echoed, one written into the
  workspace with `writeFile`.
- `helloPipelineP7(Map)` — an entire pipeline in one step; the consuming Jenkinsfile is
  six lines.

### Known limitations
- `buildAppP3` interpolates its argument into `sh` without validation. Use
  `buildAppP4` or later for anything with untrusted input.
- Builds are pretended with `echo`. Real Maven arrives in `buildJavaP9`.

---

## How a release is cut

See [GOVERNANCE.md](GOVERNANCE.md). In short: tests pass → merge to `main` → the canary
job stays green → tag → add a section here.
