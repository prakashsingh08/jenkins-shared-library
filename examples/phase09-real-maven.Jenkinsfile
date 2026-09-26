// PHASE 9 — a real Maven build from the library
//
// THIS ONE IS DIFFERENT. It does not go into a "Pipeline script" text box.
// It belongs in the APPLICATION repo, as that repo's Jenkinsfile:
//
//   1. cd ../java-maven-proj01
//   2. git checkout -b try-shared-library        ← a branch, never main
//   3. replace Jenkinsfile with the four lines below
//   4. commit and push that branch
//
//   5. In Jenkins: New Item → Pipeline → shared-lib-phase09
//        Pipeline → Definition: "Pipeline script from SCM"
//        SCM: Git
//        Repository URL: git@github-prakash:prakashsingh08/java-maven-proj01.git
//                        (or the https URL, with credentials)
//        Branch: */try-shared-library
//        Script Path: Jenkinsfile
//
// "from SCM" is the point: that is what checks the source out, so Maven has a POM
// to build. An inline Pipeline script job has an empty workspace and Maven will say
// "there is no POM in this directory".

@Library('shared-lib@main') _

buildJavaP9(name: 'java-maven-proj01')

// ---------------------------------------------------------------------------
// COMPARE IT WITH WHAT IT REPLACED
//
// java-maven-proj01's own Jenkinsfile on main is ~60 lines: agent, environment,
// Build, Test, Package, Archive, Publish, post. This is four.
//
// Open the old build and this one side by side:
//   * same stages?
//   * same test results?
//   * same jar archived?
//
// If yes, the pipeline is unchanged from the outside and the logic now lives in one
// place for every project. That is the entire point of the course.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// BREAK IT ON PURPOSE
//
//  1. Paste these lines into an inline "Pipeline script" job instead
//     →  "The goal you specified requires a project to execute but there is no POM
//         in this directory" — the empty-workspace lesson.
//
//  2. In buildJavaP9.groovy, drop the  args  line (the .m2 volume), then build twice
//     →  time the second build. That is what the cache is worth.
//
//  3. Make a test fail on purpose in the app repo
//     →  yellow (UNSTABLE), not red (FAILED) — and check junit still reported,
//        because it runs in post { always }.
//
//  4. Remove  -B  from a Maven command
//     →  scroll the log. That is why batch mode exists.
//
//  5. Pass  skipTests: true
//     →  the Test stage is skipped, and junit does not fail the build thanks to
//        allowEmptyResults.
// ---------------------------------------------------------------------------
