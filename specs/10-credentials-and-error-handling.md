# Phase 10 — Credentials and error handling

> **Extension phase.** Follows [Phase 9](09-from-pretend-to-real-maven.md).
>
> Correlates with: `groovy-learning/specs/11-error-handling-and-credentials.md` and
> `java-maven-proj01/specs/04-cloudsmith-artifact-publish.md`.

## Why this phase exists

Phase 9 built a real jar. The next thing any real pipeline does is **publish** it — and that needs a
password.

Two new problems arrive together:

1. **Secrets.** A password has to reach Maven without ending up in the build log, in Git, or in an
   archived file.
2. **Failure.** Publishing is the step most likely to fail, and a failure in the middle of a build
   leaves things half-done.

Both are library concerns. If you get them right once, here, every consuming project gets them right
for free. That is the strongest argument there is for a shared library.

---

## Part 1 — Where secrets live

Not in your code. Not in `resources/`. Not in a Git repo at all.

They live in Jenkins' **credential store**: *Manage Jenkins → Credentials*. Each one has an **ID**,
which is the only part your code ever sees.

```text
your library code          Jenkins credential store
─────────────────          ────────────────────────
'cloudsmith-creds'   ──►   username: prakash
   (just an ID)            password: ••••••••
```

The four kinds you will meet:

| Kind | Holds | Typical use |
|---|---|---|
| Username with password | two strings | registries, artifact repositories |
| Secret text | one string | API tokens |
| Secret file | a file | kubeconfig, service account JSON |
| SSH key | a private key | git over SSH |

Your library takes the **ID** as config and never the secret itself:

```groovy
buildJava(name: 'catalog', credentialsId: 'cloudsmith-creds')
```

If a step's config Map ever contains an actual password, something has gone wrong upstream of you.

---

## Part 2 — `withCredentials`: borrow a secret for a few lines

```groovy
withCredentials([usernamePassword(
        credentialsId: 'cloudsmith-creds',
        usernameVariable: 'CS_USER',
        passwordVariable: 'CS_PASS')]) {

    sh 'mvn -B deploy -Dcs.user=$CS_USER -Dcs.pass=$CS_PASS'
}
```

What it does:

```text
withCredentials([...]) {
     │
     ├── looks up the credential by ID
     ├── puts the values into environment variables, INSIDE this block only
     │
     │      ... your steps run ...
     │
     └── removes them again on the way out
}
```

The variables exist only inside the braces. After the closing brace they are gone.

### The quotes rule from Phase 3 — now it really matters

Look closely at the `sh` line above. **Single quotes.**

```groovy
sh 'mvn deploy -Dcs.pass=$CS_PASS'      // ✓ the shell expands it
sh "mvn deploy -Dcs.pass=${CS_PASS}"    // ✗ Groovy expands it — into the log
```

With double quotes, Groovy builds the command string *with the real password in it*, and Jenkins
prints the command it is about to run:

```text
+ mvn deploy -Dcs.pass=hunter2          ← now in the log, forever
```

With single quotes, the log shows:

```text
+ mvn deploy -Dcs.pass=$CS_PASS         ← the shell resolved it, nobody else saw it
```

This is why Phase 3 made a fuss about `'` and `"`. That was the rehearsal; this is the real thing.

---

## Part 3 — The `environment { }` shortcut

Declarative has a shorter form:

```groovy
environment {
    CLOUDSMITH = credentials('cloudsmith-creds')
}
```

For a username/password credential, Jenkins creates **three** variables:

```text
CLOUDSMITH        "user:password"
CLOUDSMITH_USR    the username
CLOUDSMITH_PSW    the password
```

The `_USR` / `_PSW` suffixes are not a convention you chose — Jenkins adds them. `java-maven-proj01`
already uses exactly this.

| | `withCredentials` | `environment { credentials() }` |
|---|---|---|
| Scope | one block | the whole pipeline or stage |
| Variable names | you choose | fixed `_USR` / `_PSW` suffixes |
| Works in Scripted | yes | no |
| Accepts a variable for the ID | yes | often **no** (Phase 7, Rule 3) |

That last row matters in a library. `credentials(someVariable)` is one of the places Declarative
insists on a literal, so a parameterised credential ID usually pushes you to `withCredentials`.

---

## Part 4 — Masking, and what it does not cover

Jenkins masks known credential values in the log:

```text
+ echo hunter2
****
```

Useful. Also easy to over-trust. Masking works by matching the **exact string**. It does not follow
your value anywhere it changes shape:

| What you do | Masked? |
|---|---|
| `echo "$CS_PASS"` | yes |
| base64-encode it, then print | **no** |
| write it into a file, then `cat` the file | **no** |
| put it in a file that gets `archiveArtifacts`-ed | **no** — now it is downloadable |
| send it to an external service | **no**, and it has left the building |

So masking is a safety net, not a strategy. The rules that actually keep you safe:

* Never `echo` a secret, even to check it.
* If you must write a credentials file (`settings.xml`), write it, use it, **delete it** — and never
  archive it.
* Keep the secret inside the smallest block you can.

`java-maven-proj01`'s Publish stage does exactly this: writes `settings-cloudsmith.xml`, runs
`mvn deploy`, then `rm -f`s it.

---

## Part 5 — A wrapper step: your first closure-taking step

Every project that publishes repeats the same `withCredentials` block. That is a copy-paste problem,
which means it is a library problem.

You want consuming code to look like this:

```groovy
withCloudsmith('cloudsmith-creds') {
    sh 'mvn -B deploy -s settings.xml'
}
```

To build that, a step has to accept **a block of code** as an argument.

### What a closure is

A closure is a piece of code you can pass around like a value.

```groovy
def block = { echo 'hello' }     // not run yet — just stored
block()                          // now it runs
```

That is it. A block in `{ }` that you can hand to something else, which decides when to run it.

Groovy has one convenience that makes it look like built-in syntax: **if the last argument is a
closure, it can go outside the parentheses.**

```groovy
withCloudsmith('cloudsmith-creds', { sh 'mvn deploy' })    // how it really is
withCloudsmith('cloudsmith-creds') { sh 'mvn deploy' }     // how you write it
```

Every wrapper step you have ever used — `withCredentials`, `dir`, `timeout`, `node` — is this same
shape. None of them are special syntax.

### The step

```groovy
// vars/withCloudsmith.groovy
def call(String credentialsId = 'cloudsmith-creds', Closure body) {

    withCredentials([usernamePassword(
            credentialsId: credentialsId,
            usernameVariable: 'CS_USER',
            passwordVariable: 'CS_PASS')]) {

        body()          // ← run the caller's block, here, with the secret available
    }
}
```

```text
withCloudsmith('id') { sh 'mvn deploy' }
        │                     │
        │                     └── arrives as `body`
        ▼
credentials bound
        │
        ▼
     body()  ──► the caller's sh runs, with CS_USER / CS_PASS set
        │
        ▼
credentials unbound
```

The caller never sees the credential mechanics. They write two lines and get it right by default.
**That is what a good library step feels like.**

---

## Part 6 — Failing well

A publish step fails more often than a compile step: the network, the token, the version already
existing. So the library should fail *tidily*.

### `error` versus an exception

```groovy
error 'buildJava: name is required'      // your own deliberate stop
```

`error` throws an exception and marks the build FAILED. Anything else that throws — a failing `sh`,
a missing file — does the same thing. Both land in the same place, so both can be caught.

### `try` / `catch` / `finally`

```groovy
try {
    sh 'mvn -B deploy -s settings.xml'
} catch (e) {
    echo "Publish failed: ${e.message}"
    throw e                                  // ← put it back
} finally {
    sh 'rm -f settings.xml'                  // ← runs whether or not it failed
}
```

Two habits worth forming now:

* **`finally` for cleanup.** A credentials file must be deleted even when the build fails —
  *especially* then.
* **Rethrow unless you mean it.** A `catch` that swallows the exception turns a failed publish into a
  green build. That is worse than the failure, because nobody finds out.

### When you genuinely want to continue

```groovy
catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
    sh 'run-the-optional-scan'
}
```

This marks the stage failed and the build UNSTABLE, then carries on. Right for a nice-to-have
(a lint pass, an optional scan). Wrong for anything that matters.

### Say which step failed

In a pipeline made of library calls, `ERROR: script returned exit code 1` tells you nothing. Catch,
add the step name and the useful detail, rethrow:

```groovy
catch (e) {
    error "buildJava: 'mvn deploy' failed for ${appName}. Check the credential '${credentialsId}'."
}
```

Error messages are a feature of your library, not an afterthought.

---

## What to do

### 1. Write `withCloudsmith`

Create `vars/withCloudsmith.groovy` following Part 5. Take the credential ID as an argument with a
sensible default; take a `Closure body`; bind with `withCredentials`; run `body()`.

Add `vars/withCloudsmith.txt` documenting it — Phase 3's habit, and a wrapper step badly needs it,
because the usage shape is not obvious.

### 2. Use it for real

Add a **Publish** stage to `buildJava` (Phase 9) that:

* runs only on `main` — `when { branch 'main' }`
* uses `withCloudsmith { ... }`
* writes `settings.xml` from a `resources/` template (Phase 6) with `@USER@` / `@PASS@` placeholders
* runs `mvn -B deploy -s settings.xml`
* deletes the file in a `finally`

Test it on a branch first, where the `when` guard skips it. Then on `main`.

### 3. Prove the masking — and its limit

Deliberately, in a scratch pipeline:

1. `echo "$CS_PASS"` inside the block. Confirm you see `****`.
2. Now `sh 'echo $CS_PASS | base64'`. Look carefully at the output.

The second one is the lesson. The encoded value is not masked, because it is not the string Jenkins
was told to look for. Delete the scratch job afterwards.

### 4. Break it on purpose

| # | Break it | Watch for |
|---|---|---|
| 1 | Use `"` instead of `'` in the deploy `sh` | the password in the log — Part 2 |
| 2 | Use a credential ID that does not exist | the error message, and how clear it is |
| 3 | Make `mvn deploy` fail, with `rm -f` in the stage instead of `finally` | the leftover file in the workspace |
| 4 | Catch the exception and do not rethrow | a green build that published nothing |

#4 is the dangerous one. It looks like success. In a real team, that is how a broken release ships.

### 5. The whitespace trap

Create a *Secret text* credential and paste a token with a trailing newline or space. Use it.

It will fail in a way that looks like an authentication problem, because it *is* one — the secret is
not what you think it is. This is a real bug, hard to see, and worth meeting on purpose once.

Add it to your `specs/troubleshooting.md`.

### 6. A question to answer

Your `withCloudsmith` hardcodes the variable names `CS_USER` and `CS_PASS`.

A team wants `MAVEN_USER` / `MAVEN_PASS` because their script already expects those. Do you add two
more config keys, or tell them to use `withCredentials` directly?

Write down your answer. Then ask the sharper question: at what point does a wrapper step have so many
options that it is harder to use than the thing it wraps?

---

## Done when

- [ ] `withCloudsmith('...') { ... }` works, and you can explain how the block reaches the step.
- [ ] A publish runs on `main`, skipped on a branch, using a template from `resources/`.
- [ ] The credentials file is deleted in a `finally`, proven by a deliberate failure.
- [ ] You have seen a secret masked, and seen an encoded version of it *not* masked.
- [ ] You can explain why `sh "...${SECRET}"` is dangerous and `sh '...$SECRET'` is not.
- [ ] You can explain why swallowing an exception is worse than the failure it hides.

---

## Troubleshooting

| What you see | What it usually means |
|---|---|
| The password appears in the log | Double quotes in `sh` — Part 2 |
| `Could not find credentials entry with ID 'x'` | Wrong ID, or the credential is in a folder scope this job cannot see |
| `401 Unauthorized` on deploy, credential looks right | Trailing whitespace or a newline in the stored secret — exercise 5 |
| `MissingMethodException: … withCloudsmith()` | The `Closure` parameter is missing, or is not the last one |
| Build green, nothing published | A `catch` with no rethrow — Part 6 |
| `settings.xml` left in the workspace | Cleanup not in `finally` |
| `Expected a symbol` on `credentials(x)` | Declarative wants a literal there — use `withCredentials` (Part 3) |

---

## Interview angle

* "How do you get a password into a pipeline without it reaching the log?"
* "What is the difference between `withCredentials` and `environment { credentials() }`?"
* "Does Jenkins masking make secrets safe? What does it miss?"
* "How would you write a `withX { }` wrapper step, and why is that shape useful?"
* "When is it right to catch an exception in a library step?"

---

## Connects to

| Next | Where |
|---|---|
| Unit-testing library code | `groovy-learning/specs/17-testing-shared-libraries.md` |
| The full Cloudsmith publish flow | `java-maven-proj01/specs/04-cloudsmith-artifact-publish.md` |
| Branch and release strategy | `java-maven-proj01/specs/07-branching-and-release-scenarios.md` |
