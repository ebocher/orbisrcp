#!/usr/bin/env bash
# Run Maven, retrying when a repository cannot be reached (network errors only).
#
# download.eclipse.org is sometimes unreachable from the GitHub runners ("HTTP connect timed out"): the build is
# retried, and the last attempt uses a mirror of the Eclipse releases (property eclipse-repo-url of dbeaver-common).
# The p2 metadata cached by Tycho (in ~/.m2, saved by setup-java) is reused for a week: the Eclipse release
# repositories never change.
#
# Usage: mvn-retry.sh <maven arguments>
set -u

ECLIPSE_MIRROR="${ECLIPSE_MIRROR:-https://ftp.fau.de/eclipse/releases}"
ATTEMPTS=3

for attempt in $(seq 1 $ATTEMPTS); do
    extra=()
    if [ "$attempt" -eq "$ATTEMPTS" ]; then
        echo "::notice::Attempt $attempt: using the Eclipse mirror $ECLIPSE_MIRROR"
        extra=("-Declipse-repo-url=$ECLIPSE_MIRROR")
    fi
    log=$(mktemp)
    mvn -B -Dtycho.p2.transport.min-cache-minutes=10080 "${extra[@]}" "$@" 2>&1 | tee "$log"
    status=${PIPESTATUS[0]}
    if [ "$status" -eq 0 ]; then
        exit 0
    fi
    # Only the network errors are retried: a compilation error fails at once
    if ! grep -qE "Failed to load p2 repository|Unable to read repository|timed out|Could not transfer|Connection reset|Connection refused|status code: 5[0-9][0-9]" "$log"; then
        exit "$status"
    fi
    if [ "$attempt" -lt "$ATTEMPTS" ]; then
        echo "::warning::Maven failed (attempt $attempt of $ATTEMPTS), retrying in 60 s"
        sleep 60
    fi
done
echo "::error::Maven failed after $ATTEMPTS attempts"
exit 1
