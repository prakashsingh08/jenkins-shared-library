// PHASE 7 — the whole pipeline in one call
//
// Jenkins job:  shared-lib-phase07
//   New Item → Pipeline → Pipeline section → Definition: "Pipeline script" → paste this.
//
// This is the entire Jenkinsfile. No agent, no stages, no post block — all of that
// now lives in vars/helloPipelineP7.groovy, shared by every repo that calls it.

@Library('shared-lib') _

helloPipelineP7(
    name: 'catalog',
    greeting: 'Namaste'
)

// ---------------------------------------------------------------------------
// THE EXERCISE THAT MATTERS
//
// 1. Run this job. Four stages, then the post output.
// 2. Add an echo to a stage in vars/helloPipelineP7.groovy. Push the LIBRARY only.
// 3. Do not open this job. Just build it again.
//
// Your new line appears. You changed one repo and a different repo's build changed.
// That is the whole reason shared libraries exist — and, read the other way, the
// reason Phase 8 pins versions: one bad push would break every consumer at once.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// BREAK IT ON PURPOSE (Replay is much faster than push-and-build here)
//
//  1. In helloPipelineP7.groovy, add  echo 'done'  AFTER the closing } of pipeline { }
//     →  Rule 2. Declarative expects the block to be the whole pipeline.
//
//  2. Put a step directly in a stage { }, outside steps { }
//     →  Expected one of "steps", "stages", ...   (unhelpful, but now you know it)
//
//  3. Remove  agent any
//     →  what Declarative says about a missing agent.
//
//  4. Call  helloPipelineP7()  with no name
//     →  your own validation fires before the pipeline starts at all.
//
//  5. Add  when { branch someVariable }  to a stage
//     →  Rule 3. Does your Jenkins accept it? The answer depends on the version —
//        finding out is the exercise.
// ---------------------------------------------------------------------------
