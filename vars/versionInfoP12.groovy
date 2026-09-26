// PHASE 12 — the shape to copy: computation in a class, steps in the step.
//
//   versionInfoP12(branch: 'feature/PROJ-123-login', from: 'v1.2.0', to: 'v2.0.0')
//
// com.learning.phase12.VersionParser does the regex and the arithmetic and hands back
// Strings and plain Maps. This file does the echoing. Nothing exotic is ever alive
// across a step boundary, so CPS has nothing to complain about.

import com.learning.phase12.VersionParser

def call(Map config = [:]) {

    def branch = config.branch ?: (env.BRANCH_NAME ?: 'feature/PROJ-123-example')
    def from   = config.from   ?: 'v1.0.0'
    def to     = config.to     ?: 'v1.1.0'

    // All three calls return serializable values: a String and a Map of simple types.
    def ticket = VersionParser.ticketFrom(branch)
    def parsed = VersionParser.parse(to)
    def bump   = VersionParser.bumpKind(from, to)

    echo "Branch:  ${branch}"
    echo "Ticket:  ${ticket}"
    echo "Version: ${parsed.text}  (major=${parsed.major}, minor=${parsed.minor}, patch=${parsed.patch}, valid=${parsed.valid})"
    echo "Change:  ${from} -> ${to} is a ${bump} bump"

    if (bump == 'major') {
        echo "A major bump means a caller was broken. Consumers opt in by changing their pin."
    }

    return [ticket: ticket, bump: bump, version: parsed]
}
