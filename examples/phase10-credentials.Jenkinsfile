// PHASE 10 — credentials and error handling
//
// Like Phase 9, this belongs in the APPLICATION repo as its Jenkinsfile, and needs a
// job that checks source out:
//
//   Jenkins: New Item → Pipeline → shared-lib-phase10
//     Pipeline → Definition: "Pipeline script from SCM"
//     SCM: Git → java-maven-proj01 → branch */try-shared-library
//
// The Publish stage only runs when env.BRANCH_NAME equals publishBranch ('main' by
// default). In a plain Pipeline job BRANCH_NAME is null, so Publish is SKIPPED —
// which is the right way round for a stage that publishes. Use a Multibranch job to
// exercise it for real.
//
// Requires a Jenkins credential of kind "Username with password" with the ID
// cloudsmith-creds (Manage Jenkins → Credentials).

@Library('shared-lib@main') _

buildJavaP10(
    name: 'java-maven-proj01',
    credentialsId: 'cloudsmith-creds',
    publishBranch: 'main'
)

// ---------------------------------------------------------------------------
// THE WRAPPER STEP ON ITS OWN
//
// withCloudsmithP10 is usable outside buildJavaP10. Paste this into a scratch
// Pipeline job to see the shape of a closure-taking step:
//
//   @Library('shared-lib@main') _
//   pipeline {
//       agent any
//       stages {
//           stage('Use credentials') {
//               steps {
//                   withCloudsmithP10 {
//                       sh 'echo user is $CS_USER'        // single quotes!
//                   }
//               }
//           }
//       }
//   }
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// PROVE THE MASKING — AND ITS LIMIT   (scratch job; delete it afterwards)
//
//   withCloudsmithP10 {
//       sh 'echo $CS_PASS'              // → ****            masked
//       sh 'echo $CS_PASS | base64'     // → look carefully.  NOT masked
//   }
//
// Masking matches the exact string. Change the value's shape — encode it, write it
// to a file, send it somewhere — and the mask is gone. It is a safety net, not a
// strategy.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// BREAK IT ON PURPOSE
//
//  1. In withCloudsmithP10's block, use  sh "echo ${CS_PASS}"  with DOUBLE quotes
//     →  the password appears in the log, because Groovy built the command string
//        before Jenkins printed it. This is the Phase 3 quote rule, for real.
//
//  2. Pass  credentialsId: 'does-not-exist'
//     →  read the message. Is it clear enough to debug at 6pm?
//
//  3. Move the  rm -f settings-cloudsmith.xml  out of the finally and into the try,
//     after the deploy. Then make the deploy fail.
//     →  the credentials file is left behind in the workspace.
//
//  4. In the catch, remove the  error "..."  rethrow and just echo the message.
//     →  a GREEN build that published nothing. In a real team, that is how a broken
//        release ships unnoticed. This is the dangerous one.
//
//  5. Create a "Secret text" credential and paste a token with a trailing newline.
//     →  it fails looking exactly like a wrong password. Hard to see, real, and
//        worth meeting once on purpose. Add it to specs/troubleshooting.md.
// ---------------------------------------------------------------------------
