# Phase 8 — Versions, Replay, and reading errors

> Correlates with: `groovy-learning/specs/18-architecture-and-versioning.md`, and the *Version
> pinning* exercise in `groovy-learning/specs/07-first-shared-library.md`.

## Why this phase exists

Phase 7 ended on an uncomfortable note:

> Push a broken library change and **every** pipeline using it fails — including the ones nobody
> touched.

That is real, and it is the main objection people raise to shared libraries. This phase is the
answer, and it has two halves:

1. **Versions** — so a repo chooses *when* to take your changes, instead of taking them all
   instantly.
2. **Reading errors** — so when something does break, you spend two minutes on it, not two hours.

Then the course is done.

---

## Part 1 — What "a version" means here

When Jenkins loads your library, it checks out **something** from Git. That something can be:

```text
@Library('shared-lib@main')      a branch
@Library('shared-lib@v1.0.0')    a tag
@Library('shared-lib@a3f9c21')   a commit SHA
@Library('shared-lib')           whatever "Default version" says in the Jenkins config
```

All four are ordinary Git references. There is no special Jenkins versioning system — you are just
telling Git which commit to check out.

```text
@Library('shared-lib@v1.0.0') _
                     └──┬──┘
                        │
              "check out this ref before the build"
```

### One setting has to be on

For a Jenkinsfile to override the default, this must be ticked in the library config:

**Manage Jenkins → System → Global Pipeline Libraries → Allow default version to be overridden**

If it is off, `@Library('shared-lib@v1.0.0')` is rejected and you get the default regardless. Check
it now; you set it in Phase 2.

---

## Part 2 — Branch or tag?

The difference is one sentence:

> **A branch moves. A tag does not.**

```text
main (a branch)                     v1.0.0 (a tag)
──────────────────────►             ───────●───────
 keeps moving as you push            stuck on one commit, forever
```

So:

| Reference | Gets your new work | Good for |
|---|---|---|
| `@main` | instantly, on the next build | you, while developing the library |
| `@v1.0.0` | never — it is frozen | production pipelines that must not change under them |

A repo pinned to `v1.0.0` is unaffected by anything you push. That is the whole safety mechanism.

### Version numbers

The usual scheme is **SemVer**: `v<major>.<minor>.<patch>`.

| Change | Bump | Example |
|---|---|---|
| Fixed a bug, behaviour unchanged | patch | `v1.0.0` → `v1.0.1` |
| Added a new optional feature | minor | `v1.0.1` → `v1.1.0` |
| Broke something existing callers rely on | **major** | `v1.1.0` → `v2.0.0` |

The rule that matters for a library: **a major bump is a promise you broke something.** Renaming a
config key, making an optional key required, or removing a step are all major changes, even if the
diff is one line.

### The moving `v1` tag

Many teams also keep a tag called just `v1`, and move it forward to the newest `v1.x.y` release.

```text
v1.0.0   v1.1.0   v1.1.1
  ●────────●────────●
                    ▲
                   v1   ← moved here on each 1.x release
```

Consumers then write `@Library('shared-lib@v1')` and get bug fixes automatically, but never the
breaking `v2`. It is a good middle ground between "always latest" and "frozen forever".

It does mean a tag that moves, which is unusual in Git, so say so in your README when you do it.

---

## Part 3 — Who should pin to what

| Who | Uses | Why |
|---|---|---|
| You, developing the library | `@main` | you want your change on the next build |
| A test or sandbox job | `@main` | catches breakage early, where it costs nothing |
| A real project's pipeline | `@v1` or `@v1.2.3` | must not break because someone else pushed |
| Debugging "which change broke this?" | `@<sha>` | pins to exactly one commit |

The pattern to take away: **one job builds against `main` so breakage is found by you, not by
someone else's release.** That job is doing the same work a test suite would — not as good as real
tests, but far better than nothing.

---

## Part 4 — Replay: editing library code without pushing

By now the edit → commit → push → build loop has become tiring. Jenkins has a shortcut.

Open any finished build and click **Replay** in the left menu.

```text
a finished build
      │ Replay
      ▼
an editable copy of the Jenkinsfile
        AND of every library file that build loaded
      │ Run
      ▼
a new build using your edits — nothing is committed
```

That is the fast loop: change, run, look, change again. When it finally works, copy the change into
your editor, commit and push.

### The limits, which matter

* **Nothing is saved.** Close the tab and your edits are gone. Copy anything you want to keep.
* **Only files that build loaded appear.** A file that was never used is not there to edit, and you
  cannot add a new one.
* **It is per-build.** No other job is affected. That is the point.

Replay is for iterating, not for fixing production. A change that only exists in a Replay does not
exist tomorrow.

---

## Part 5 — The error cheat-sheet

You have broken things on purpose in every phase since Phase 2. Here is the consolidated table.

### The library will not load

| Message | Cause |
|---|---|
| `No library named shared-lib found` | The Jenkins **Name** field and the `@Library('...')` string differ |
| `Could not resolve shared-lib@main` / `Couldn't find any revision to build` | Branch or tag does not exist, or nothing was pushed |
| `Authentication failed` in the clone | Private repo, no credential on the library config |
| Version ignored, always gets the default | *Allow default version to be overridden* is off — Part 1 |

### The library loaded, the code is wrong

| Message | Cause |
|---|---|
| `No such DSL method 'hello'` | Missing `@Library` line, wrong file name, or wrong case |
| `No such DSL method 'cleanup'` | Called a non-`call` method without its object: use `buildApp.cleanup()` |
| `unable to resolve class com.learning.Greeter` | `src/` folder path and `package` disagree, or not pushed |
| `No such property: echo for class: …` | A bare `echo` inside a `src/` class — needs `script.echo` |
| `No such library resource …` | Wrong `resources/` path. The message prints what it looked for |
| `NotSerializableException` | A library class held across steps without `implements Serializable` |
| `expecting '}', found ''` and no stages ran | Compile error **anywhere** in `vars/` — the whole library is compiled |

### Declarative pipeline structure

| Message | Cause |
|---|---|
| `Expected one of "steps", "stages", …` | A step outside `steps { }` |
| `Expected a symbol` on `credentials(x)` or `branch x` | That slot wants a literal, not a variable |
| `Perhaps you forgot to surround the code with a step that provides this, such as: node` | No agent |

### It runs, but does the wrong thing

| Symptom | Cause |
|---|---|
| A literal `${name}` in the log | Single quotes — you needed `"` |
| A `false` you passed comes out `true` | Elvis on a boolean: `config.x ?: true` |
| `Building null` | A key typo and no validation |
| A step does nothing, build still green | Missing `()` on a no-argument call |
| Your change did not appear | Not pushed, or the job is pinned to a different version |

### The two questions to ask first

Before reading any stack trace:

1. **Did I push?**
2. **Which version did this build load?** (the `Loading library shared-lib@…` line, near the top)

Those two account for most of the time people lose.

---

## What to do

### 1. Make a version

Tag your current commit `v1.0.0` and push the tag. (Tags are not pushed by `git push` on its own —
find out what the extra argument is.)

### 2. Make `main` and the tag differ

Change something visible in `vars/helloPipeline.groovy` — a different greeting, an extra `echo`.
Commit and push to `main`. Do **not** move the tag.

Now `main` and `v1.0.0` genuinely differ.

### 3. Prove pinning works

Run `hello-pipeline-demo` twice:

| Run | First line of the Jenkinsfile | Expect |
|---|---|---|
| 1 | `@Library('shared-lib@main') _` | your new change |
| 2 | `@Library('shared-lib@v1.0.0') _` | the old behaviour |

Same job, same library, different output. Check the `Loading library …` line in each log and confirm
it names the version you asked for.

This is the exercise that makes versioning real. Do not skip it.

### 4. Try Replay

Replay one of those builds. Change an `echo` inside the library file, run it, see the change. Then
confirm the obvious: run the job normally afterwards and watch your Replay edit be gone.

### 5. Write your cheat-sheet

Create `specs/troubleshooting.md` — your own version of Part 5.

Use **your** notes from Phases 2 through 7: the actual messages you saw, and what each one turned
out to be. Yours will be more useful than mine, because you will recognise your own wording.

This is the most valuable file you produce in the whole course. It is also the file a teammate will
thank you for.

### 6. A design question to answer

`buildApp` takes `name`. Six months on, you want to rename it to `appName` because it reads better.
Two hundred repos call it the old way.

What do you do?

Write down your answer before reading the next paragraph.

> The usual answer: accept **both** for a while. Read `config.appName ?: config.name`, echo a
> deprecation warning when the old key is used, document the change, and remove the old key only in
> a major version that consumers opt into by changing their pin. The cost of "just rename it" is 200
> broken pipelines belonging to people who never asked for the improvement.

That instinct — *how do I change this without breaking people who trust me?* — is most of what
separates a library that survives from one everybody routes around.

---

## Done when

- [ ] `v1.0.0` exists as a tag on GitHub.
- [ ] The same job produces different output on `@main` and `@v1.0.0`.
- [ ] You checked the `Loading library …` line and it matched what you pinned.
- [ ] You have used Replay once, and seen that the change did not persist.
- [ ] `specs/troubleshooting.md` exists, in your own words.
- [ ] You can explain what you would do to rename a config key used by 200 repos.

---

## Interview angle

* "How do you version a shared library?"
* "A library change broke production. What do you change so it cannot happen again?"
* "What is the difference between pinning to a branch, a tag and a SHA?"
* "How do you deprecate a parameter in a library used by hundreds of pipelines?"
* "How do you test library changes without breaking everyone?"

---

## You have finished the course

Look at what exists now:

```text
jenkins-shared-library/
├── vars/
│   ├── hello.groovy          a step                              (Phase 2)
│   ├── greet.groovy          a step backed by a class            (Phase 3, 5)
│   ├── buildApp.groovy       Map config, defaults, validation    (Phase 3, 4)
│   ├── buildApp.txt          documentation                       (Phase 3)
│   └── helloPipeline.groovy  an entire pipeline                  (Phase 7)
├── src/com/learning/
│   ├── Greeter.groovy        logic outside vars/                 (Phase 5)
│   └── Banner.groovy         a class reading a resource          (Phase 6)
├── resources/com/learning/   templates                           (Phase 6)
└── specs/troubleshooting.md  your own error notes                (Phase 8)
```

…driven by a six-line Jenkinsfile, tagged `v1.0.0`.

And you can explain every piece of it: why `call()`, where `echo` comes from, why a class needs
`script`, why config is a Map, why `false` needs `get()` and not `?:`, and what happens to 200 repos
when someone pushes a bad commit.

### Where to go next

| Want | Go to |
|---|---|
| The Groovy underneath this, properly | `groovy-learning/specs/` — 21 phases, starting at closures |
| Real Maven, Docker, Kubernetes steps | `groovy-learning/specs/12`–`14` |
| Unit-testing library code | `groovy-learning/specs/17-testing-shared-libraries.md` |
| CPS, `@NonCPS`, and why pipelines behave oddly | `groovy-learning/specs/06-pipeline-groovy-mental-model.md` |
| Make `java-maven-proj01` use a real library pipeline | `java-maven-proj01/specs/08-pipeline-types-and-shared-library.md` |

The single most valuable next step: replace a **real** pipeline with a library call. Everything up to
now has been a rehearsal with `echo` standing in for the work. Doing it once against a build that
actually matters is where the learning finishes.
