// PHASE 6 — files that are not code: resources/ and libraryResource
//
// Jenkins job:  shared-lib-phase06
//   New Item → Pipeline → Pipeline section → Definition: "Pipeline script" → paste this.
//
// Two different uses of the same resources/ folder:
//   bannerP6     reads a file and echoes it            (text only, no file created)
//   buildInfoP6  fills a template and writeFile's it   (a real file in the workspace)

@Library('shared-lib') _

pipeline {
    agent any

    stages {
        // The banner text lives in resources/com/learning/phase06/banner.txt and is
        // read by a src/ class via script.libraryResource.
        stage('Banner') {
            steps {
                bannerP6(name: 'catalog')
            }
        }

        // Fill a template, write it into the workspace, then prove it is really there.
        stage('Build info') {
            steps {
                buildInfoP6(name: 'catalog')
                sh 'cat build-info.txt'
            }
        }

        // The same thing a real pipeline does with a generated settings.xml or
        // values.yaml — except that file would be deleted, not archived. This one is
        // safe to keep because it contains nothing secret.
        stage('Archive it') {
            steps {
                archiveArtifacts artifacts: 'build-info.txt', fingerprint: true
            }
        }

        // Validation still comes first, before anything is read or written.
        // Uncomment to see it fail with our own message:
        //
        // stage('No name') {
        //     steps {
        //         bannerP6()
        //     }
        // }
    }
}

// ---------------------------------------------------------------------------
// BREAK IT ON PURPOSE (use Replay so you do not have to push each time)
//
//  1. In Banner.groovy, ask for  'banner.txt'  with no folders
//     →  No such library resource banner.txt could be found
//        Read the message: it prints the exact path it looked for.
//
//  2. Use a leading slash:  '/com/learning/phase06/banner.txt'
//     →  does it still work? Find out rather than guessing.
//
//  3. In Banner.groovy, call  libraryResource(...)  without  script.
//     →  the Phase 5 error again, in a new place.
//
//  4. Delete the  writeFile  line from buildInfoP6, keep the  sh 'cat ...'
//     →  cat: build-info.txt: No such file or directory
//        libraryResource returned the text; nothing ever wrote it to disk.
//
//  5. Change a marker in the template to @APP@ but leave the .replace('@APP_NAME@')
//     →  the raw marker appears in the output file.
// ---------------------------------------------------------------------------
