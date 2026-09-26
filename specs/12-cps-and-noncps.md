# Phase 12 — CPS, `@NonCPS`, and why pipeline Groovy is strange

> **Extension phase.** Follows [Phase 11](11-testing-the-library.md).
>
> Correlates with: `groovy-learning/specs/06-pipeline-groovy-mental-model.md`.

## Why this phase exists

Three things in this library were introduced with a promise to explain them later:

1. `class Greeter implements Serializable` — why?
2. `greetAll` uses a `for` loop, with a comment warning you off `names.each { }` — why?
3. **Replay** exists at all, and a build survives a Jenkins restart — how?

They all have the same single cause. Learn it once and a whole category of baffling failure becomes
predictable:

> **Jenkins does not run your pipeline Groovy the way normal Groovy runs. It rewrites it first, so
> that a build can be paused, written to disk, and resumed later.**

That rewriting is called **CPS**. This phase explains what it costs you.

---

## Part 1 — The problem being solved

A build can run for an hour. In that hour, Jenkins might be restarted — an upgrade, a crash, a
`docker compose restart`.

Normal programs die when their process dies. Jenkins decided pipelines should not:

```text
build running, halfway through stage 3
        │
        │  Jenkins restarts
        ▼
build state was already written to disk
        │
        ▼
Jenkins comes back, reads the state, carries on from stage 3
```

That is a genuinely impressive trick, and you have probably relied on it without noticing. To do it,
Jenkins must be able to **write a half-finished program to disk** — where it is, which variables
exist, what they contain.

Ordinary Groovy cannot do that. So Jenkins changes your code so it can.

---

## Part 2 — What CPS actually does

**CPS** stands for *Continuation Passing Style*. What it means in practice:

```text
your code                         what Jenkins runs
─────────                         ─────────────────
def a = 1                         a state machine:
sh 'first'                          state 0: set a = 1, then → state 1
def b = a + 1                       state 1: run sh 'first',  then → state 2
sh 'second'                         state 2: set b = a + 1,   then → state 3
                                    state 3: run sh 'second', then → done
```

Your method is chopped into pieces at every point where it might have to wait. Between pieces,
Jenkins can save everything and stop.

Two consequences follow, and everything else in this phase is a detail of one of them:

* **Every local variable must be writable to disk** — because it might be saved between two pieces.
* **The rewriting only happens to code Jenkins compiled specially** — your Jenkinsfile and your
  library. Not the JDK, not Groovy's own built-in methods.

---

## Part 3 — Consequence 1: things that cannot be saved

If a variable is alive across a step boundary, it gets serialized. Some objects cannot be.

The classic:

```groovy
def matcher = (branchName =~ /feature\/(.*)/)   // a java.util.regex.Matcher
echo "checking"                                 // ← a step: state may be saved HERE
if (matcher.matches()) { ... }                  // matcher had to survive the save
```

```text
java.io.NotSerializableException: java.util.regex.Matcher
```

`Matcher` is not serializable, so the save fails. Note what is *not* wrong here: the regex is fine,
the logic is fine. The only problem is that the object was still alive when Jenkins tried to write
the build to disk.

Usual suspects:

| Not serializable | Typical source |
|---|---|
| `java.util.regex.Matcher` | `=~` |
| `groovy.json.internal.LazyMap` | older `readJSON` / `JsonSlurper` results |
| file streams, sockets, connections | anything you opened yourself |
| non-`Serializable` classes of your own | `src/` classes without `implements Serializable` ← **mystery 1** |

### Two fixes

**Fix A — don't keep it alive.** Finish with it before the next step:

```groovy
def branch = (branchName =~ /feature\/(.*)/)[0][1]   // extract a String immediately
echo "branch is ${branch}"                           // only a String survives
```

**Fix B — put it in a `@NonCPS` method.** Part 5.

---

## Part 4 — Consequence 2: closures and iteration

This one explains mystery 2.

```groovy
names.each { name ->
    echo "Hello ${name}"
}
```

Looks harmless. Here is the problem:

```text
names.each { ... }
   │
   ├── `each` is a JDK/Groovy method — NOT CPS-transformed
   └── the closure { ... } IS CPS-transformed
             │
             └── so a non-CPS method is being asked to call CPS code
```

It sometimes works, and sometimes fails with errors that look like nonsense — `CpsCallableInvocation`
turning up somewhere it makes no sense, or the loop body silently running zero times. The mixture is
the problem.

A plain loop is CPS-transformed all the way through, so it is always safe:

```groovy
for (String name : names) {
    echo "Hello ${name}"
}
```

**The rule to carry:** inside a pipeline or a library class, prefer `for` over `.each`, `.collect`,
`.find` and friends **whenever the body calls a step**. If the body is pure computation with no
steps, the functional style is usually fine — and is safest of all inside a `@NonCPS` method.

That is exactly why `Greeter.greetAll` was written with a `for` loop back in Phase 5.

---

## Part 5 — `@NonCPS`: opting out

```groovy
import com.cloudbees.groovy.cps.NonCPS

@NonCPS
String extractTicket(String branchName) {
    def m = (branchName =~ /(PROJ-\d+)/)
    return m ? m[0][1] : 'none'
}
```

`@NonCPS` tells Jenkins: *do not rewrite this method. Run it as ordinary Groovy, start to finish.*

```text
a normal method            a @NonCPS method
───────────────            ─────────────────
chopped into states        runs in one go
can be paused mid-way      cannot be paused
locals must serialize      locals never saved — it never stops
can call steps             MUST NOT rely on calling steps
```

### The rules, and they are strict

1. **No pipeline steps inside it.** `sh`, `echo`, `checkout`, `withCredentials` — none of them. They
   may appear to work, then behave bizarrely. This is the rule people break first.
2. **It must finish quickly and not block.** There is no pause point inside it.
3. **Return something serializable** — a String, a number, a List of Strings. Returning a `Matcher`
   just moves the problem to the caller.
4. **Keep it small.** A `@NonCPS` method should be a calculation, not a workflow.

### What it is genuinely good for

Regex, parsing, sorting, string manipulation, building a Map to hand back to the pipeline — anything
that is pure computation. Put the computation in `@NonCPS`, keep the steps outside it:

```groovy
def ticket = extractTicket(env.BRANCH_NAME)   // @NonCPS: computation
echo "Ticket: ${ticket}"                       // pipeline: steps
```

That separation is the whole technique.

---

## Part 6 — And that explains Replay

Mystery 3 falls out of the same machinery. Because Jenkins compiles your pipeline and library code
itself, and knows exactly which files a build loaded, it can offer you an editable copy of them for
one run — which is what **Replay** is.

It is not a special feature bolted on. It is a side effect of Jenkins owning the compilation step.

The same fact explains something from Phase 2: **a compile error anywhere in `vars/` fails the build
before stage one**, because the whole library is compiled up front, not lazily when a step is first
called.

---

## Part 7 — The practical rules

If you remember nothing else from this phase:

| Do | Not |
|---|---|
| `for (x in list) { step() }` | `list.each { step() }` |
| Extract a `String` from a regex immediately | Hold a `Matcher` across a step |
| `implements Serializable` on library classes | Hope for the best |
| `@NonCPS` for pure computation | `@NonCPS` with `sh` or `echo` inside |
| Keep pipeline methods short | One long method doing everything |
| Return Strings, numbers, simple Lists/Maps | Return Matchers, streams, exotic objects |

And the diagnostic instinct: **`NotSerializableException` means "something was alive when Jenkins
tried to save the build"** — so find what, and either stop holding it or move it into `@NonCPS`.

---

## What to do

### 1. Reproduce the classic failure

In a scratch pipeline (not the library), write a `script { }` block that creates a `Matcher` with
`=~`, calls `echo` in between, and then uses the matcher.

Get `NotSerializableException: java.util.regex.Matcher` on purpose. Read the stack trace: it names
the class that could not be written. That name is always the clue.

### 2. Fix it twice

* **Fix A:** extract the String immediately, so nothing exotic is alive across the `echo`.
* **Fix B:** move the regex into a `@NonCPS` method that returns a String.

Both work. Say which you would use in a library step, and why.

### 3. Break the `@NonCPS` rule on purpose

Put an `echo` inside your `@NonCPS` method. Run it.

Whatever happens — it works oddly, it prints nothing, it fails strangely — that is the point. Write
down exactly what you observed; this is a failure mode that is very hard to recognise later without
having met it once.

### 4. See the iteration difference

Add a step to the library that loops over a list and calls `echo` for each item. Write it with
`.each { }` first, then with `for`. Try both, including with a list of 3+ items.

Note whether `.each` worked for you. It may well have — the honest lesson is that it is
*unpredictable*, not that it always fails, which is precisely why the rule is "don't".

### 5. Watch a build survive a restart

This is the payoff, and it takes two minutes:

1. Start a build that runs `sleep 120` in a stage.
2. While it is running: `docker compose restart jenkins` (from `java-maven-proj01`).
3. Wait for Jenkins to come back. Open the build.

It carries on. Everything in this phase — the rewriting, the serialization, the restrictions — exists
to buy that one behaviour. Decide for yourself whether you think the trade was worth it; both
answers are defensible, and it is a good interview conversation.

### 6. Audit your own library

Go through `vars/` and `src/` and find:

* any `.each`, `.collect` or `.find` whose body calls a step
* any class missing `implements Serializable`
* any place a `Matcher` or other exotic object could be alive across a step

Fix what you find, or write down why it is safe. This is the most valuable exercise in the phase,
because it is your code.

---

## Done when

- [ ] You produced `NotSerializableException` on purpose and can explain the cause in one sentence.
- [ ] You fixed it both ways and have a preference, with a reason.
- [ ] You put a step inside `@NonCPS` and saw what happens.
- [ ] You can state the `for` vs `.each` rule and why it exists.
- [ ] You restarted Jenkins mid-build and watched the build resume.
- [ ] You audited your own library and either fixed or justified every hit.

---

## Troubleshooting

| What you see | What it usually means |
|---|---|
| `java.io.NotSerializableException: java.util.regex.Matcher` | A `Matcher` alive across a step — Part 3 |
| `NotSerializableException: com.learning…` | A library class without `implements Serializable` |
| `CpsCallableInvocation` in a strange place | CPS code called from a non-CPS method — usually `.each` with a step inside |
| A loop body silently runs zero times | Same cause as above |
| A `@NonCPS` method behaves unpredictably | It probably calls a step. Part 5, rule 1 |
| `java.io.NotSerializableException: groovy.json.internal.LazyMap` | A parsed-JSON object held across a step — convert it to plain Maps/Strings first |

---

## Interview angle

* "What is CPS in Jenkins pipelines, and why does it exist?"
* "Why does `NotSerializableException` happen, and how do you fix it?"
* "What does `@NonCPS` do, and what can't you do inside it?"
* "Why avoid `.each` in a shared library?"
* "Why do shared library classes implement `Serializable`?"
* "What is the trade-off Jenkins made by transforming pipeline Groovy?"

---

## Connects to

| Next | Where |
|---|---|
| Versioning, deprecation, governance at scale | `groovy-learning/specs/18-architecture-and-versioning.md` |
| The full pipeline mental model | `groovy-learning/specs/06-pipeline-groovy-mental-model.md` |
| Testing what CPS lets you test | [Phase 11](11-testing-the-library.md) — `@NonCPS` methods are plain Groovy, so they are the easiest things in the library to unit test |
