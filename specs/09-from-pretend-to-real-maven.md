# Phase 9 — From pretend to real: a Maven build step

> **Extension phase.** The eight-phase course is complete without this. Phase 8 ended by saying the
> most valuable next step is replacing a real pipeline with a library call. This is that step.
>
> Correlates with: `groovy-learning/specs/12-maven-library.md`, and
> `java-maven-proj01/specs/02-jenkinsfile.md`.

## Why this phase exists

Every build so far has been pretend:

```groovy
sh "echo pretend: mvn clean package for ${appName}"
```

That was deliberate. A failing Maven build teaches you nothing about `call()` or Maps.

But now the mechanics are solid, and pretending has a cost: you have never seen the parts that only
show up with a real build. Three of them:

1. **The agent.** Maven has to run *somewhere*, and that somewhere needs Maven installed.
2. **The source code.** The library has no idea what repo it is building. Someone has to check it
   out.
3. **The results.** Test reports and jar files need collecting, or the build tells you nothing.

This phase does all three, against `java-maven-proj01` — a real project with a real `pom.xml`.

---

## Part 1 — Where does Maven come from?

`sh 'mvn -B package'` only works if `mvn` exists on the machine running it.

Your Jenkins runs in a container that does **not** have Maven. So you give the stage an agent that
does:

```groovy
agent {
    docker {
        image 'maven:3.9-eclipse-temurin-17'
        args '-v jenkins-maven-repo:/root/.m2'
    }
}
```

Reading that:

```text
docker { image '...' }   run the steps inside this container
args '-v vol:/root/.m2'  mount a volume at Maven's cache directory
```

### Why the volume matters

Maven downloads every dependency into `~/.m2`. A fresh container has an empty one, so **every build
re-downloads the internet**. Mounting a named volume keeps that cache between builds.

```text
without the volume            with the volume
──────────────────            ───────────────
build 1: downloads 200 jars   build 1: downloads 200 jars
build 2: downloads 200 jars   build 2: reuses them        ← minutes saved
```

This exact setup already works in `java-maven-proj01/Jenkinsfile`. You are moving it into the
library, not inventing it.

### Agent for the whole pipeline, or per stage?

Both are valid:

| Style | Effect |
|---|---|
| `agent { docker { ... } }` at the top | every stage runs in the container |
| `agent none` at the top, `agent` per stage | each stage picks its own |

Top-level is simpler and right for now. Per-stage matters when one stage needs Maven and another
needs, say, Docker or kubectl — a real thing, just not today's problem.

---

## Part 2 — The library has no source code

This is the part that catches everyone.

Your library is a *different repo* from the application. When `helloPipeline()` runs, the workspace
does not automatically contain `pom.xml`. It depends entirely on **how the job got its Jenkinsfile**:

| Job type | Source checked out? |
|---|---|
| Pipeline → *Pipeline script* (typed in the UI) | **No.** Jenkins has no repo to check out |
| Pipeline → *Pipeline script from SCM* | **Yes**, automatically, before your code runs |
| Multibranch Pipeline | **Yes**, automatically |

```text
Pipeline script (inline)            Pipeline script from SCM
────────────────────────            ────────────────────────
workspace: empty                    workspace: your repo, already checked out
mvn package → no pom.xml            mvn package → works
```

So a library step that runs Maven needs the job to be *from SCM*. That is why this phase switches
away from the inline script you have used since Phase 2.

### `checkout scm`

You will see this line in library pipelines:

```groovy
stage('Checkout') {
    steps {
        checkout scm
    }
}
```

`scm` means "whatever repo this job's Jenkinsfile came from". It is only defined for SCM-backed
jobs — which is the same condition as above.

You usually do **not** need it: Declarative already checks out the source before the first stage. It
earns its place when you have turned that off (`options { skipDefaultCheckout() }`), or when a stage
runs on a different agent that starts with an empty workspace.

Know what it means; do not cargo-cult it into every pipeline.

---

## Part 3 — Collecting results

A build that runs tests and says nothing about them is barely better than not running them.

### Test reports

```groovy
post {
    always {
        junit 'target/surefire-reports/*.xml'
    }
}
```

`junit` reads Maven's test XML and turns it into the graph and the "Test Result" page on the build.

Two details worth knowing:

* Put it in `post { always { } }`, not in the stage. If tests fail, the stage fails — and you want
  the report **especially** then.
* Failing tests make the build **UNSTABLE** (yellow), not FAILED (red). Yellow means "it built, but
  tests failed". People misread this constantly.

### Artifacts

```groovy
archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
```

Saves the jar against the build so you can download it later. `fingerprint: true` records a checksum,
which lets Jenkins tell you later which build produced a given file.

### Version numbers

`java-maven-proj01` already does this:

```groovy
environment {
    APP_VERSION = "1.0.${BUILD_NUMBER}"
}
```

and passes it with `-Drevision=${APP_VERSION}`. Every build gets a unique version. That is enough for
now — proper release versioning is its own topic, covered in that project's Phase 10.

---

## Part 4 — What to move into the library, and what to leave

The temptation is to move everything. Resist it.

| Belongs in the library | Belongs in the app repo |
|---|---|
| The stage skeleton: build, test, package, archive | which app this is (`name:`) |
| The agent and the Maven cache volume | anything genuinely unusual about this app |
| `junit` and `archiveArtifacts` paths | — |
| `post` behaviour: notifications, cleanup | — |
| The Maven flags everyone should use (`-B`) | — |

The test: **if every project would want it, it goes in the library.** If it is specific to one
project, it goes in that project's config Map — or that project should not be using the standard
pipeline at all.

---

## What to do

### 1. Write `buildJava`

Create `vars/buildJava.groovy`. A step that runs a real Maven build.

Config:

| Key | Required | Default |
|---|---|---|
| `name` | **yes** | — |
| `image` | no | `maven:3.9-eclipse-temurin-17` |
| `skipTests` | no | `false` |
| `goals` | no | `clean package` |

Follow Phase 4's four beats — read, default, validate, act — then the `pipeline { }` block from
Phase 7:

* `agent { docker { image ... args ... } }`
* `environment { APP_VERSION = "1.0.${BUILD_NUMBER}" }`
* stages: **Build**, **Test** (skip it when `skipTests` is true), **Package**, **Archive**
* `post { always { junit ... } }` plus success and failure messages

Use `-B` on every Maven command — it means "batch mode", which stops Maven printing a download
progress bar thousands of lines long into your log.

### 2. Point a real repo at it

In `java-maven-proj01`, create a **branch** — not `main`. That repo's real pipeline works and there
is no reason to break it while experimenting.

On that branch, replace the Jenkinsfile with:

```groovy
@Library('shared-lib@main') _

buildJava(name: 'java-maven-proj01')
```

Then create a Jenkins job of type **Pipeline → Pipeline script from SCM**, pointed at that repo and
that branch. (Part 2 explains why inline will not do.)

Run it.

### 3. Compare

Open the old build (the full inline Jenkinsfile on `main`) and your new one side by side.

* Same stages?
* Same test results?
* Same jar archived?

If yes, you have just done the thing this whole course was about: **the pipeline is unchanged from
the outside, and the logic now lives in one place for every project.**

### 4. Break it on purpose

| # | Break it | Watch for |
|---|---|---|
| 1 | Run it as an inline *Pipeline script* instead of from SCM | the missing-`pom.xml` failure — Part 2 |
| 2 | Remove the `args '-v ...'` volume, run twice | how long the second build takes |
| 3 | Make a test fail on purpose | yellow vs red, and whether `junit` still reported |
| 4 | Drop `-B` from a Maven command | the log length |

#3 is the one to sit with. Understanding UNSTABLE vs FAILURE saves real confusion later.

### 5. A question to answer

Your `buildJava` archives `target/*.jar`.

A team has a project that produces a `.war`. Do you:

* add an `artifacts:` key to the config Map,
* change the default to `target/*.{jar,war}`, or
* tell them to use the individual steps instead?

Write down your answer and a reason. This is Phase 7's question again — *how far do you
parameterise?* — but now with a concrete case, which is harder and more honest.

---

## Done when

- [ ] `buildJava(name: '...')` runs a real Maven build from a six-line Jenkinsfile.
- [ ] Test results appear on the build page, from the library's `junit` call.
- [ ] A jar is archived and downloadable.
- [ ] You can explain why an inline Pipeline script cannot build a repo, and what `checkout scm` is
      for.
- [ ] You saw the difference the `.m2` volume makes to build time.
- [ ] You know what makes a build UNSTABLE rather than FAILED.
- [ ] `java-maven-proj01`'s `main` branch is untouched.

---

## Troubleshooting

| What you see | What it usually means |
|---|---|
| `The goal you specified requires a project to execute but there is no POM in this directory` | Empty workspace — inline job instead of from SCM. Part 2 |
| `mvn: not found` | No Maven on the agent. The `docker` agent is missing or was ignored |
| `Cannot run program "docker"` | Jenkins cannot reach the Docker socket — check the compose file's socket mount |
| Build is yellow, not red | Tests failed. That is UNSTABLE, and it is correct |
| `No test report files were found` | Tests never ran, or the path is wrong. Check `target/surefire-reports/` really exists |
| Every build re-downloads everything | The `.m2` volume is missing from `args` |
| `scm is not defined` | `checkout scm` in a job that has no SCM. Part 2 |

---

## Interview angle

* "Where does `mvn` come from in a containerised Jenkins build?"
* "Why is the `.m2` cache mounted as a volume?"
* "What is the difference between a Pipeline script and a Pipeline script from SCM, and why does a
  library care?"
* "What makes a build UNSTABLE instead of FAILED?"
* "Which parts of a pipeline belong in the library and which belong in the app repo?"

---

## Connects to

Natural follow-ons, in the order they usually matter:

| Next | Where |
|---|---|
| Credentials and safe failure handling | `groovy-learning/specs/11-error-handling-and-credentials.md` |
| Publishing the artifact to Cloudsmith | `java-maven-proj01/specs/04-cloudsmith-artifact-publish.md` |
| Unit-testing the library itself | `groovy-learning/specs/17-testing-shared-libraries.md` |
