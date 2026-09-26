// PHASE 8 — versions: the same job, two different libraries
//
// Jenkins job:  shared-lib-phase08
//   New Item → Pipeline → Pipeline section → Definition: "Pipeline script" → paste this.
//
// RUN IT TWICE. Change only the first line between runs.
//
//   Run 1:  @Library('shared-lib@main') _      → prints "Running the main branch version"
//   Run 2:  @Library('shared-lib@v1.0.0') _    → does NOT print it
//
// Same job. Same library. Different commit. That difference is the whole of
// versioning: a branch moves, a tag does not.
//
// If Run 2 behaves like Run 1, check "Allow default version to be overridden" in
// Manage Jenkins → System → Global Trusted Pipeline Libraries. Without it, the
// @version part of the line is ignored and you always get the default.

@Library('shared-lib@main') _

helloPipelineP7(
    name: 'catalog',
    greeting: 'Namaste'
)

// ---------------------------------------------------------------------------
// WHAT TO CHECK IN THE LOG
//
//   Loading library shared-lib@main       ← or @v1.0.0 — it names what it resolved
//   Found match: refs/heads/main  <sha>   ← or refs/tags/v1.0.0
//
// Before reading any stack trace in this course, ask two questions first:
//   1. Did I push?
//   2. Which version did this build load?
// Those two account for most of the time people lose.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// OTHER REFERENCES YOU CAN PIN TO
//
//   @Library('shared-lib@main') _        a branch  — moves as you push
//   @Library('shared-lib@v1.0.0') _      a tag     — frozen forever
//   @Library('shared-lib@2d679c6') _     a commit  — exact, useful when bisecting
//   @Library('shared-lib') _             whatever "Default version" says in Jenkins
//
// Who should use what:
//   you, developing the library   → @main   (you want your change on the next build)
//   a sandbox / canary job        → @main   (breakage found by you, cheaply)
//   a real project's pipeline     → @v1.0.0 (must not break because someone pushed)
//   "which change broke this?"    → @<sha>
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// REPLAY — the fast loop you have been missing since Phase 2
//
//   open a finished build → Replay (left menu) → edit the Jenkinsfile AND any
//   library file that build loaded → Run
//
// Nothing is committed. Limits worth knowing:
//   * edits are NOT saved — copy anything you want to keep before closing the tab
//   * only files that build actually loaded appear; you cannot add a new one
//   * it affects this one run only
//
// Replay is for iterating, not for fixing production: a change that exists only in
// a Replay does not exist tomorrow.
// ---------------------------------------------------------------------------
