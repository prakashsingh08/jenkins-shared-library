# Phase 4 — Configuration as a Map

> Correlates with: `groovy-learning/specs/08-passing-configuration.md`, and the
> `def mavenImage = config.mavenImage ?: '...'` lines in
> `java-maven-proj01/specs/08-pipeline-types-and-shared-library.md`.

## Why this phase exists

Phase 3 gave your step one argument. That works fine.

Now imagine the step grows. It needs the app name, the Maven image, whether to skip tests, and
which branch to publish from. With positional arguments you end up here:

```groovy
buildApp('catalog', 'maven:3.9-eclipse-temurin-17', true, 'main')
```

Read that line and answer three questions:

* What is `true`?
* What happens if you only want to change the last value?
* What breaks if someone adds a new argument in the middle next year?

Nobody can answer the first one without opening the library. The second forces you to type all four
values anyway. The third silently breaks every pipeline that uses the step.

So real library steps take **one argument: a Map**.

```groovy
buildApp(
    name: 'catalog',
    skipTests: true
)
```

Now every value is labelled. You pass only what you care about. Adding a new option later breaks
nobody.

This phase teaches: Maps, named arguments, defaults, and **validation** — the check that Phase 3
kept promising.

---

## Part 1 — What a Map is

A Map is a list of **key → value** pairs. Nothing more.

```groovy
def config = [name: 'catalog', skipTests: true]
```

Picture it:

```text
config
 ├── name      →  'catalog'
 └── skipTests →  true
```

### Reading a value

Three ways, all the same thing:

```groovy
config.name            // 'catalog'     ← use this one
config['name']         // 'catalog'
config.get('name')     // 'catalog'
```

### An empty Map

```groovy
def config = [:]
```

That colon inside the brackets is what makes it a Map and not a List. `[]` is an empty **List**;
`[:]` is an empty **Map**. This trips people up once, and then never again.

### Asking for a key that isn't there

```groovy
def config = [name: 'catalog']

echo "${config.name}"        // catalog
echo "${config.skipTests}"   // null
```

It does **not** crash. You get `null`. Remember that — the next part depends on it.

---

## Part 2 — Named arguments are secretly a Map

This is the piece of Groovy that makes the whole pattern work.

When you write this:

```groovy
buildApp(name: 'catalog', skipTests: true)
```

Groovy does **not** pass two arguments. It collects the `key: value` pairs into a single Map and
passes that:

```text
buildApp(name: 'catalog', skipTests: true)
          │
          │  Groovy gathers the key: value pairs
          ▼
buildApp([name: 'catalog', skipTests: true])
          │
          │  () means call()
          ▼
call(Map config)
          │
          ▼
config.name      == 'catalog'
config.skipTests == true
```

So the step is declared with **one** parameter:

```groovy
def call(Map config) {
    ...
}
```

The call site *looks* like it has many arguments. It has one. That is the trick, and it is why this
pattern is everywhere in Jenkins libraries.

### Proof you can run

Put this in your step and look at the log:

```groovy
def call(Map config) {
    echo "I received: ${config}"
}
```

Call it with anything. The log prints the whole Map. Do this once — it makes the idea concrete.

---

## Part 3 — Default values

Your step should work when the caller leaves things out:

```groovy
buildApp(name: 'catalog')     // no skipTests given
```

So the step supplies its own value when a key is missing. Groovy has a short way to write that.

### The Elvis operator `?:`

```groovy
def image = config.image ?: 'maven:3.9-eclipse-temurin-17'
```

Read it as:

```text
config.image ?: 'maven:...'
      │
      ├── is config.image "truthy"?  →  yes  →  use config.image
      └──                            →  no   →  use 'maven:...'
```

It is called Elvis because `?:` looks like a face with a quiff. That is genuinely the reason.

### But first: what does "truthy" mean?

Groovy does not only treat `true` and `false` as true and false. It has opinions about everything:

| Value | Groovy treats it as |
|---|---|
| `true` | true |
| `false` | **false** |
| `null` | **false** |
| `'catalog'` | true |
| `''` (empty string) | **false** |
| `0` | **false** |
| `5` | true |
| `[]` (empty list) | **false** |
| `[1, 2]` | true |

This is called **Groovy truth**, and it is usually convenient.

### Where it bites you

Watch carefully:

```groovy
def skipTests = config.skipTests ?: true     // ✗ broken
```

Now a caller writes:

```groovy
buildApp(name: 'catalog', skipTests: false)
```

They asked for `false`. But `false` is not truthy, so Elvis throws it away and uses `true`. The
caller's explicit choice is silently ignored — and nothing errors, so you will not notice for weeks.

**Rule: never use `?:` for a boolean option.**

### The safe ways

For booleans, ask whether the key exists, or compare to `null`:

```groovy
def skipTests = config.containsKey('skipTests') ? config.skipTests : false
```

or use the two-argument `get`, which only fills in when the key is **absent**:

```groovy
def skipTests = config.get('skipTests', false)
```

`get('key', default)` is the one to reach for by default. It does the right thing for booleans,
empty strings and zero, and it reads well.

```text
config.get('skipTests', false)
        │
        ├── key present?  →  yes  →  use its value, even if it is false
        └──               →  no   →  use false
```

Use `?:` for Strings, where "empty" and "missing" mean the same thing anyway.

---

## Part 4 — Letting the caller pass nothing at all

Give the parameter its own default:

```groovy
def call(Map config = [:]) {
    ...
}
```

Now all three of these work:

```groovy
buildApp()                              // config = [:]
buildApp(name: 'catalog')               // config = [name: 'catalog']
buildApp(name: 'catalog', skipTests: true)
```

Without `= [:]`, the first line fails, because `call(Map)` needs a Map and got nothing.

`def call(Map config = [:])` is the single most common signature in real shared libraries. It is
worth recognising on sight.

---

## Part 5 — Validation: fail fast, fail clearly

Defaults handle *missing optional* values. Validation handles *wrong or missing required* values.

### Why bother

Without validation:

```groovy
buildApp(nmae: 'catalog')      // typo: nmae
```

`config.name` is `null`. The step runs happily and echoes:

```text
Building null
```

Then the build fails ten minutes later inside Maven, with an error that mentions nothing about a
typo. The person debugging it is not you, and they will not enjoy it.

With validation, it stops in one second with a message that names the problem.

### The `error` step

Jenkins gives you a step for this:

```groovy
error "buildApp: 'name' is required"
```

`error` stops the build immediately and puts your message in the log. Two habits worth forming:

* **Name your step in the message.** In a pipeline made of ten library calls, "name is required"
  does not say *which* step is complaining.
* **Say what was expected**, not just what was wrong.

### Checking a required value

```groovy
if (!config.name) {
    error "buildApp: 'name' is required, e.g. buildApp(name: 'catalog')"
}
```

`!config.name` catches `null`, a missing key, and an empty string — all in one, thanks to Groovy
truth. Here it works *for* you.

### Checking the value is safe — the Phase 3 promise

Remember `catalog; echo INJECTED` from Phase 3. Now you can stop it:

```groovy
if (!(config.name ==~ /^[A-Za-z0-9._-]+$/)) {
    error "buildApp: 'name' may only contain letters, digits, dot, dash and underscore. Got: ${config.name}"
}
```

`==~` means "does this whole string match this pattern". `/.../ ` is how Groovy writes a regular
expression.

What the pattern allows: letters, digits, `.`, `_`, `-`. What it blocks: spaces, `;`, `&`, `|`,
`$`, quotes, backticks — everything a shell treats as punctuation.

```text
'catalog'                →  matches    →  allowed
'catalog-v2'             →  matches    →  allowed
'catalog; echo INJECTED' →  no match   →  build stops here
```

This is called an **allow-list**: say what is permitted, reject everything else. The opposite
approach — listing the dangerous characters and blocking those — fails, because you will always
forget one.

---

## Part 6 — The order to do things in

Every well-written library step follows the same four beats:

```text
1. READ      pull values out of the Map
2. DEFAULT   fill in what the caller left out
3. VALIDATE  reject what is missing or unsafe  ← stop here if bad
4. ACT       do the actual work
```

Written out:

```groovy
def call(Map config = [:]) {

    // 1 + 2 — read and default
    def appName   = config.name
    def image     = config.image ?: 'maven:3.9-eclipse-temurin-17'
    def skipTests = config.get('skipTests', false)

    // 3 — validate
    if (!appName) {
        error "buildApp: 'name' is required"
    }
    // ... the pattern check from Part 5 ...

    // 4 — act
    echo "Building ${appName} (skipTests=${skipTests})"
}
```

Keep validation **before** the first `sh`. A step that half-runs and then complains leaves a
workspace in an unknown state, and is much harder to reason about than one that refuses at the door.

---

## What to do

### 1. Convert `buildApp` to a Map

Change `vars/buildApp.groovy` from `def call(String appName)` to `def call(Map config = [:])`.

Support three options:

| Key | Type | Required? | Default |
|---|---|---|---|
| `name` | String | **yes** | — |
| `image` | String | no | `maven:3.9-eclipse-temurin-17` |
| `skipTests` | boolean | no | `false` |

Follow the four beats from Part 6. Keep the build pretend (`echo`), and have the echo print all
three values so you can see the defaults working.

Update `hello-library-demo` to call it the new way, then push and run.

### 2. Prove the defaults behave

Run each of these and check the log says what you expect. Predict first.

| # | Call | Expect |
|---|---|---|
| 1 | `buildApp(name: 'catalog')` | defaults for image and skipTests |
| 2 | `buildApp(name: 'catalog', skipTests: true)` | skipTests true |
| 3 | `buildApp(name: 'catalog', skipTests: false)` | skipTests **false** |
| 4 | `buildApp(name: 'catalog', image: 'maven:3.8')` | your image, not the default |

**#3 is the real test.** If it prints `true`, you used `?:` on a boolean — go back to Part 3.

### 3. Prove the validation works

| # | Call | Expect |
|---|---|---|
| 1 | `buildApp()` | fails, says `name` is required |
| 2 | `buildApp(nmae: 'catalog')` | fails the same way — the typo is not a `name` |
| 3 | `buildApp(name: '')` | fails |
| 4 | `buildApp(name: 'catalog; echo INJECTED')` | fails on the pattern, and `INJECTED` never runs |

#4 is Phase 3's demo, now closed. Run it and confirm the injected command is gone from the log.

### 4. A question to answer from the code

An unknown key is silently ignored today:

```groovy
buildApp(name: 'catalog', skpTests: true)     // typo: skpTests
```

Nothing fails. The tests run anyway. The caller is confused.

Should your step reject unknown keys? Write down your answer and one reason each way. Then, if you
want the exercise, implement it: compare `config.keySet()` against the keys you support and `error`
on anything else.

(There is no single right answer. Strict is safer for a small team; lenient breaks fewer pipelines
when you add options later. Real libraries do both. Having an opinion is the point.)

### 5. Update your docs

`vars/buildApp.txt` still describes the old positional argument. Fix it: list each key, its type,
whether it is required, and its default.

Out-of-date documentation is worse than none — people trust it and then get burnt.

---

## Done when

- [ ] `buildApp(name: 'catalog')` works, and the log shows both defaults being applied.
- [ ] `skipTests: false` is respected, not overwritten by the default.
- [ ] You can explain why `config.skipTests ?: true` is a bug.
- [ ] `buildApp()` fails immediately with a message naming the step and the missing key.
- [ ] `buildApp(name: 'catalog; echo INJECTED')` is rejected before any shell runs.
- [ ] `buildApp.txt` documents every key, with types and defaults.

---

## Troubleshooting

| What you see | What it usually means |
|---|---|
| `MissingMethodException: … call() is applicable for argument types: (java.util.LinkedHashMap)` | The step is still declared `call(String ...)`. Named arguments arrive as one Map |
| `buildApp()` fails with a missing-method error, not your nice message | The parameter has no default. Use `Map config = [:]` — Part 4 |
| A `false` you passed comes out as `true` | Elvis on a boolean. Part 3 |
| `Building null` | A key typo, and no validation. Part 5 |
| `groovy.lang.MissingPropertyException: No such property: name` | You wrote a bare `name` instead of `config.name` |
| Everything works but unknown keys are ignored | Expected. That is exercise 4 |

---

## Interview angle

* "Why do shared library steps take a Map instead of positional arguments?"
* "What is wrong with `config.flag ?: true`?"
* "Where do you validate input in a library step, and why there?"
* "How would you stop a caller-supplied value reaching a shell command?"

---

## Connects to

**Phase 5** moves logic out of `vars/` and into a class in `src/`. That is where `echo` stops
working for free and you have to hand the class a `script` reference — the mechanism traced in
[02a §11](02a-how-hello-works-explained.md).
