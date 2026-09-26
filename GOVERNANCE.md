# Governance

> **Phase 13.** Half a page of decisions. The value is in having decided, not in the
> prose — "everyone knows" is not a policy, and this library runs unsandboxed code in
> every build that loads it.

## What this library is

A **global trusted** Jenkins shared library, registered as `shared-lib`.

Trusted means it runs **outside the Groovy sandbox**, with the Jenkins controller's
privileges. The consequence, stated plainly:

> **Push access to this repository is equivalent to admin access to Jenkins.**

Everything below follows from that sentence.

## Which version should I pin to?

| Who you are | Pin | Why |
|---|---|---|
| A normal project | `@v1.0.0` (or the newest tag) | your build must not change because someone else pushed |
| The canary job | `@main` | breakage is found here, by us, where it costs nothing |
| Developing the library | `@main` | you want your change on the next build |
| Bisecting a failure | `@<commit sha>` | exactly one commit, no ambiguity |

**Jenkins Default version:** `main` today, because this is a learning repo with one
maintainer. In a shared environment it should be the newest **tag**, so a job with no
pin gets a stable line rather than whatever landed an hour ago.

*Allow default version to be overridden* must stay ticked, or `@v1.0.0` in a
Jenkinsfile is silently ignored.

## Who may change what

| Action | Today (learning repo) | What it should be with real consumers |
|---|---|---|
| Push to `main` | the maintainer, directly | pull request + one review; direct push disabled |
| Create a tag | the maintainer | restricted; a release is a deliberate act |
| Merge without green tests | possible | blocked — `mvn test` required |
| Change a `vars/` step's config keys | freely | additive only, or follow the deprecation process |

The difference between those two columns is the whole of Phase 13. The right-hand
column is not bureaucracy — it is what "unsandboxed code in everyone's build" earns.

## Changing things without breaking people

**Additive is free.** A new optional key with a default, a new step, a new method:
ship it as a minor release, nobody notices.

**Renames and removals are not.** Three steps:

1. **Accept both**, warn on the old spelling, ship as a **minor** release.
   Nothing breaks. See `buildAppP13`, which accepts `name` and `appName`.
2. **Wait** — a quarter, not a fortnight. The warning appears in the logs of exactly
   the people who need to act, which beats an email to everyone.
3. **Remove** in a **major** release. Consumers opt in by changing their pin, when
   they are ready.

A rename is a major change regardless of the size of the diff.

## Reporting a problem

Open an issue on the repository, or — for anything affecting a running pipeline — pin
back to the previous tag first and report afterwards. Rolling back is one line in a
Jenkinsfile and always beats debugging under pressure.

## What builds this library

Honest answer today: **nothing**. Tests are run by hand:

```bash
docker run --rm -v "$PWD":/app -w /app -v "$HOME/.m2":/root/.m2 \
  maven:3.9-eclipse-temurin-17 mvn -B test
```

What it should be, in order of value:

1. A Jenkins job on this repo running `mvn test` on every push.
2. A canary job building a real project against `@main`, to catch the Declarative and
   agent problems unit tests never see.
3. Tagging only from a green `main`.

**The bootstrapping trap:** if this library's own Jenkinsfile called `buildJavaP10`
from itself, a broken commit would break the very pipeline needed to fix it. Keep the
library's own Jenkinsfile plain and self-contained, or pin it to the last known-good
tag — never `@main`.

## Related

- [CHANGELOG.md](CHANGELOG.md) — what changed, for consumers
- [specs/13-architecture-and-governance.md](specs/13-architecture-and-governance.md) — the reasoning
- [specs/troubleshooting.md](specs/troubleshooting.md) — errors and what they mean
