// Phase 6 — a src/ class that reads a file from resources/.
//
// Two rules meeting:
//   * Phase 5: a class has no pipeline, so steps are reached through `script`.
//   * Phase 6: libraryResource is an ordinary pipeline step — so it is
//     script.libraryResource(...), not libraryResource(...).

package com.learning.phase06

class Banner implements Serializable {

    def script

    Banner(script) {
        this.script = script
    }

    String render(String appName) {
        // libraryResource RETURNS THE FILE'S TEXT. It does not create a file anywhere.
        // The path is relative to resources/, with no leading slash.
        String text = script.libraryResource('com/learning/phase06/banner.txt')

        // Plain .replace() — obvious, always works, and nobody reading it needs to
        // know anything. Groovy's template engines do ${} substitution properly but
        // collide with how Jenkins pauses and resumes pipelines.
        return text.replace('@APP_NAME@', appName)
                   .replace('@BUILD_NUMBER@', "${script.env.BUILD_NUMBER}")
                   .replace('@JOB_NAME@', "${script.env.JOB_NAME}")
    }

    void show(String appName) {
        script.echo "\n" + render(appName)
    }
}
