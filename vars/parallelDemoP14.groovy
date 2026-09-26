// PHASE 14 — parallel branches built at runtime, and the bug everyone hits.
//
//   parallelDemoP14(mode: 'broken')    every branch runs the LAST value
//   parallelDemoP14(mode: 'fixed')     each branch gets its own value
//   parallelDemoP14(mode: 'chunked')   branches built from a split list
//
// Needs no Maven and no agent beyond `agent any` — it is here so the mechanics can be
// seen on their own, before they are buried inside a real build.

import com.learning.phase14.Chunker

def call(Map config = [:]) {

    def mode    = config.mode ?: 'fixed'
    def modules = config.modules ?: ['alpha', 'beta', 'gamma']
    def chunks  = config.chunks ?: 2

    switch (mode) {

        // -------------------------------------------------------------------
        // THE BUG.
        //
        // The closure does not COPY `name` — it refers to the variable. By the time
        // the branches actually run, the loop has finished and `name` holds its final
        // value. So all three branches print 'gamma'.
        //
        // Nothing throws. That is what makes it expensive: you get three identical
        // branches and a confused afternoon.
        // -------------------------------------------------------------------
        case 'broken':
            echo 'Building branches the WRONG way — watch every branch print the same module:'
            def broken = [:]
            for (String name : modules) {
                broken[name] = {
                    echo "  building ${name}    <-- should differ per branch"
                }
            }
            parallel broken
            break

        // -------------------------------------------------------------------
        // THE FIX: a fresh variable, declared INSIDE the loop, so each closure
        // captures its own.
        //
        // This is ordinary Groovy closure capture, not a Jenkins quirk — but Jenkins
        // is where most people meet it.
        // -------------------------------------------------------------------
        case 'fixed':
            echo 'Building branches correctly:'
            def branches = [:]
            for (String name : modules) {
                def moduleName = name          // ← the entire fix
                branches[moduleName] = {
                    echo "  building ${moduleName}"
                    sh "echo pretend: mvn -B test -pl ${moduleName}"
                }
            }
            // `parallel` here is the STEP taking a Map of name -> closure, not the
            // Declarative parallel { } block. The Declarative form is fixed at parse
            // time, so a library that builds branches from caller input needs this one.
            parallel branches
            break

        // -------------------------------------------------------------------
        // Splitting work into a FIXED number of branches, rather than one per module.
        // One branch per module sounds obvious and is usually wrong: 40 modules on a
        // two-CPU agent is 40 lots of start-up cost and no extra speed.
        // -------------------------------------------------------------------
        case 'chunked':
            def groups = Chunker.split(modules, chunks)
            echo "Split ${modules.size()} modules into ${groups.size()} branches"

            def chunked = [:]
            for (int i = 0; i < groups.size(); i++) {
                def index = i                        // fresh variable again
                def group = groups[i]                // and again
                chunked["branch-${index + 1}"] = {
                    echo "  branch ${index + 1} handles: ${group.join(', ')}"
                    sh "echo pretend: mvn -B test -pl ${group.join(',')}"
                }
            }
            parallel chunked
            break

        default:
            error "parallelDemoP14: unknown mode '${mode}'. Use broken, fixed or chunked."
    }
}
