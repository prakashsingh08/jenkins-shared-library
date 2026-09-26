# Phase 13 — Architecture, versioning and governance

> **Extension phase.** Follows [Phase 12](12-cps-and-noncps.md).
>
> Correlates with: `groovy-learning/specs/18-architecture-and-versioning.md`.

## Why this phase exists

Everything so far has been about making the library **work**. This phase is about what changes when
it is used by people who are not you.

One repo, one user, and you can do anything: rename a key, delete a step, push straight to `main`.
Twenty repos and four teams, and every one of those becomes someone else's broken Monday morning.

```text
one consumer                    twenty consumers
────────────                    ────────────────
rename a key → fix your call    rename a key → twenty broken pipelines
push to main → you find out     push to main → everyone finds out
no docs → you remember          no docs → four people ask you the same question
```

None of this is Groovy. It is the part of the job that decides whether a library gets adopted or
routed around — and it is what a DevOps Lead interview actually probes.

---

## Part 1 — Where a library can be registered

You have used one option. There are three.

| Scope | Set up in | Available to | Runs sandboxed? |
|---|---|---|---|
| **Global Trusted** | Manage Jenkins → System | every job on the controller | no — full privilege |
| **Global Untrusted** | Manage Jenkins → System | every job | yes — restricted |
| **Folder-level** | a folder's configuration | jobs inside that folder | untrusted by default |
| **Dynamic** | `library identifier: 'x@main', retriever: modernSCM(...)` in the Jenkinsfile | that one pipeline | untrusted |

What this is actually for:

* **Global trusted** — the platform team's library. Reviewed, versioned, privileged.
* **Folder-level** — a team's own library, scoped to their folder, so their experiments cannot
  affect anyone else.
* **Dynamic** — trying something out, or loading a library whose name you only know at runtime.
  Useful; rarely the right default, because the dependency stops being visible in one place.

The security point is worth stating plainly: **a trusted library runs arbitrary code on the Jenkins
controller, with the controller's privileges.** Push access to that repo is equivalent to admin
access to Jenkins. That single sentence justifies most of the rest of this phase.

---

## Part 2 — One library, or several?

The instinct is one library for everything. It works until it doesn't.

```text
ONE LIBRARY                         SEVERAL LIBRARIES
every team's steps in one repo      platform-lib   (build, publish, deploy)
                                    team-a-lib     (their odd requirements)
+ easy to find                      team-b-lib
+ one version to track
- every change risks everyone       + blast radius stays small
- one team's urgency blocks another + teams move at their own speed
- reviewers own code they don't use - which library has the step I want?
                                    - versions multiply
```

The pattern most organisations settle on: **one platform library that everyone uses, plus optional
team libraries for genuinely local needs.** A Jenkinsfile can load more than one:

```groovy
@Library(['platform-lib@v2', 'team-a-lib@main']) _
```

The rule of thumb for what goes in the platform library: **would every team want this, and would you
be willing to support it at 2am?** If not, it belongs to the team that wants it.

---

## Part 3 — A release process, not just a tag

Phase 8 gave you `v1.0.0`. A release *process* is what makes tags trustworthy.

```text
work on a branch
     │  tests pass (Phase 11)
     ▼
merge to main
     │  the canary job builds against @main and stays green
     ▼
tag v1.1.0          ← a deliberate act, not an accident
     │  move the v1 tag forward
     ▼
note what changed in CHANGELOG.md
```

Three decisions to write down, because "everyone knows" is not a policy:

| Decision | A reasonable answer |
|---|---|
| What is the **default version** in Jenkins? | `v1` — so a job with no pin gets a stable, maintained line |
| Who pins what? | platform pipelines `@v1`; the canary `@main`; nobody pins `@main` for production |
| When do you cut a release? | when something is worth telling consumers about — not every merge |

**SemVer, applied to a library:** patch = fix with no behaviour change; minor = new optional
capability; **major = you broke a caller**. Renaming a config key is major. Making an optional key
required is major. The size of the diff is irrelevant.

---

## Part 4 — Changing things without breaking people

This is the skill the whole phase exists for.

**Additive changes are free.** A new optional key with a default, a new step, a new method — no
consumer notices.

**Removals and renames are not.** The three-step deprecation:

```text
1. ACCEPT BOTH          config.appName ?: config.name
                        plus: echo "WARNING: 'name' is deprecated, use 'appName'"
                        ship as a MINOR version. Nothing breaks.

2. WAIT                 a real amount of time — a quarter, not a fortnight.
                        Tell people. Grep the org for the old key if you can.

3. REMOVE               drop the old key in a MAJOR version.
                        Consumers opt in by changing their pin, when they are ready.
```

The point of step 1 is that **the deprecation warning appears in the logs of the people who need to
act**, which is far more effective than an email to everyone.

And the honest trade: this costs you a version where both spellings exist and the code is uglier.
That cost is the price of not breaking twenty pipelines belonging to people who never asked for the
improvement.

---

## Part 5 — Who is allowed to push

Given Part 1's security point, "anyone with repo access" is not an answer.

Minimum sensible controls, in the order they are worth adding:

| Control | What it stops |
|---|---|
| Branch protection on `main` | an accidental direct push breaking every consumer at once |
| Required pull request review | a change nobody else has read running with controller privileges |
| Tests required to pass (Phase 11) | the obvious class of regression |
| `CODEOWNERS` | the platform team seeing changes to their steps |
| Restricting who can create tags | a "release" nobody agreed to |

None of this is exotic; it is what any application repo has. The reason it matters *more* here is
that the blast radius of a bad merge is every pipeline in the organisation, and the code runs
unsandboxed.

---

## Part 6 — Discoverability

A library nobody can navigate gets copy-pasted around instead of used.

What is worth maintaining, roughly in order of value per minute spent:

1. **`vars/*.txt` for every step** (Phase 3) — these show up in Jenkins' own *Global Variable
   Reference*, exactly where someone goes looking.
2. **A README that lists the steps** with one line each, so the answer to "is there a step for X?"
   takes ten seconds.
3. **`examples/`** — a runnable Jenkinsfile beats a paragraph of description.
4. **A CHANGELOG** — so "what do I get if I move from `v1.2.0` to `v1.3.0`?" has an answer.

The test for all of it: **can a new team adopt your library without talking to you?** If not, you
are the documentation, and you will be interrupted forever.

---

## Part 7 — The library needs a pipeline of its own

Phase 11 ended on this question. Here it is properly.

The library is code. Code that runs unsandboxed in every build in the company deserves at least
what an application repo gets:

```text
pull request opened
      │
      ▼
a Jenkins job on THIS repo runs  mvn test          ← Phase 11's tests
      │
      ▼
merged to main
      │
      ▼
the canary job builds a real project against @main  ← catches Declarative errors
      │
      ▼
tag when you choose to release
```

The bootstrapping puzzle worth thinking through: **what builds the library?** If the library's own
Jenkinsfile calls `buildJavaP10` from itself, a broken commit breaks the very pipeline you need in
order to fix it.

The usual answers: keep the library's own Jenkinsfile plain and self-contained (no `@Library` at
all), or pin it to the last known-good tag rather than `@main`. Either is defensible. Having thought
about it before it happens is the point.

---

## What to do

### 1. Write the policy down

Add a short section to the README, or a `GOVERNANCE.md`, answering:

* Which version should a normal project pin to, and why?
* What is the Jenkins **Default version** set to?
* Who may merge to `main`? Who may tag?
* Where do consumers report a problem?

Half a page. The value is in having decided, not in the prose.

### 2. Start a CHANGELOG

Create `CHANGELOG.md` and back-fill it from your own history — you have `v1.0.0` and everything on
`main` since. Group by version, one line per change, written for a **consumer** rather than for you:

```text
## v1.1.0
- buildJavaP10: new optional `serverId` key (default: cloudsmith)
- buildAppP4: 'name' is now validated; values with shell punctuation are rejected
```

That second line is a behaviour change some caller might depend on. Is it really minor, or is it
major? Decide, and defend it.

### 3. Do a real deprecation

Pick a step and rename a config key — `name` → `appName` is the obvious one.

Implement step 1 of Part 4: accept both, warn on the old one, keep every existing call working.
Verify with two builds: one using the old key (works, warns) and one using the new (works, silent).

Then write down what would have to be true before you delete the old key.

### 4. Protect the branch

On GitHub, turn on branch protection for `main`: require a pull request, and disallow direct
pushes.

Then notice the immediate consequence — **you** can no longer push straight to `main`, which is
inconvenient exactly as intended. Decide whether you keep it. For a solo learning repo, turning it
off again is a legitimate choice; being able to explain the trade is the exercise.

### 5. Try a folder-level library

Create a Jenkins folder, register a library on the folder rather than globally, and run a job inside
it. Note that it is **untrusted** by default, and what that changes.

This is how a team gets to experiment without a platform admin's involvement — worth having seen
once.

### 6. Answer the bootstrapping question

Write down, in three sentences, what builds your library and what happens if a bad commit lands on
`main`. If the answer is "nothing builds it", say what you would add first.

---

## Done when

- [ ] A written policy exists: default version, who pins what, who may merge and tag.
- [ ] `CHANGELOG.md` exists and is written for consumers, not for you.
- [ ] One config key is deprecated the proper way — both spellings work, the old one warns.
- [ ] You can explain why renaming a key is a **major** version change regardless of diff size.
- [ ] You can explain why push access to a trusted library is equivalent to Jenkins admin.
- [ ] You have an answer to "what builds the library?"

---

## Troubleshooting

| What you see | What it usually means |
|---|---|
| A step works in one folder and not another | Folder-level library registered in only one of them |
| `Scripts not permitted to use …` | The library is registered as **untrusted** — it is sandboxed |
| Consumers pinned to `@v1` do not get your fix | You never moved the `v1` tag |
| `@Library(['a', 'b']) _` fails | Both libraries must be registered and resolvable; check each name and version separately |
| Everyone broke at once | Someone pushed to `main` and consumers were not pinned. Part 3 |
| Nobody uses your new step | Nobody knew it existed. Part 6 |

---

## Interview angle

* "How do you version a shared library used by many teams?"
* "How do you deprecate a parameter without breaking anyone?"
* "Would you put every team's pipeline code in one library? Why or why not?"
* "What are the security implications of a trusted global library?"
* "What builds your shared library, and what stops a bad change reaching everyone?"
* "A team says your standard pipeline does not fit them. What do you do?"

---

## Connects to

This closes the arc. What is left is practice on real pipelines:

| Next | Where |
|---|---|
| Production scenarios and debugging drills | `groovy-learning/specs/19-production-scenarios.md` |
| The interview question bank | `groovy-learning/specs/20-interview-preparation.md` |
| A full capstone library | `groovy-learning/specs/21-capstone.md` |
| Branch and release strategy on the app side | `java-maven-proj01/specs/07-branching-and-release-scenarios.md` |
