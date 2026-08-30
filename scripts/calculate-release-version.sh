#!/usr/bin/env bash

set -euo pipefail

usage() {
    echo "Usage: $0 <current-version> [release-version]" >&2
}

if (( $# < 1 || $# > 2 )); then
    usage
    exit 2
fi

current_version="$1"
release_override="${2:-}"
number='(0|[1-9][0-9]*)'
release_pattern="^${number}\\.${number}\\.${number}$"
snapshot_pattern="^${number}\\.${number}\\.${number}-SNAPSHOT$"

if [[ -n "$release_override" ]]; then
    if [[ ! "$release_override" =~ $release_pattern ]]; then
        echo "Release version must use major.minor.patch without a suffix: $release_override" >&2
        exit 1
    fi
    release_version="$release_override"
elif [[ "$current_version" =~ $snapshot_pattern ]]; then
    release_version="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.${BASH_REMATCH[3]}"
else
    echo "POM version must use major.minor.patch-SNAPSHOT when no release override is supplied: $current_version" >&2
    exit 1
fi

if [[ ! "$release_version" =~ $release_pattern ]]; then
    echo "Unable to determine a valid release version: $release_version" >&2
    exit 1
fi

major="${BASH_REMATCH[1]}"
minor="${BASH_REMATCH[2]}"
next_minor=$((10#$minor + 1))
next_version="${major}.${next_minor}.0-SNAPSHOT"

printf 'current_version=%s\n' "$current_version"
printf 'release_version=%s\n' "$release_version"
printf 'next_version=%s\n' "$next_version"
