#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
calculator="$script_dir/calculate-release-version.sh"
tests_run=0

assert_plan() {
    local current="$1"
    local override="$2"
    local expected_release="$3"
    local expected_next="$4"
    local result

    if [[ -n "$override" ]]; then
        result="$($calculator "$current" "$override")"
    else
        result="$($calculator "$current")"
    fi

    grep -Fxq "release_version=$expected_release" <<< "$result"
    grep -Fxq "next_version=$expected_next" <<< "$result"
    tests_run=$((tests_run + 1))
}

assert_rejected() {
    if "$calculator" "$@" >/dev/null 2>&1; then
        echo "Expected version plan to be rejected: $*" >&2
        exit 1
    fi
    tests_run=$((tests_run + 1))
}

assert_plan "2.0.0-SNAPSHOT" "" "2.0.0" "2.1.0-SNAPSHOT"
assert_plan "2.3.7-SNAPSHOT" "" "2.3.7" "2.4.0-SNAPSHOT"
assert_plan "1.8" "2.0.0" "2.0.0" "2.1.0-SNAPSHOT"
assert_plan "2.3.7-SNAPSHOT" "3.0.0" "3.0.0" "3.1.0-SNAPSHOT"
assert_plan "10.99.4-SNAPSHOT" "" "10.99.4" "10.100.0-SNAPSHOT"

assert_rejected "1.8"
assert_rejected "2.0-SNAPSHOT"
assert_rejected "2.0.0"
assert_rejected "2.0.0-SNAPSHOT" "2.0"
assert_rejected "2.0.0-SNAPSHOT" "v2.0.0"
assert_rejected "2.0.0-SNAPSHOT" "02.0.0"
assert_rejected "2.0.0-SNAPSHOT" "2.0.0-SNAPSHOT"

echo "$tests_run release version tests passed"
