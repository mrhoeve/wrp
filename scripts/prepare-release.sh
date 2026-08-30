#!/usr/bin/env bash

set -euo pipefail

usage() {
    cat >&2 <<'EOF'
Usage: scripts/prepare-release.sh [options]

Options:
  --release-version VERSION  Override the release derived from the POM.
  --output-file FILE         Append the calculated values to a GitHub Actions output file.
  --dry-run                  Validate and display the release plan without changing the POM.
  --help                     Show this help.
EOF
}

release_override=""
output_file=""
dry_run=false

while (( $# > 0 )); do
    case "$1" in
        --release-version)
            if (( $# < 2 )); then
                usage
                exit 2
            fi
            release_override="$2"
            shift 2
            ;;
        --output-file)
            if (( $# < 2 )); then
                usage
                exit 2
            fi
            output_file="$2"
            shift 2
            ;;
        --dry-run)
            dry_run=true
            shift
            ;;
        --help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage
            exit 2
            ;;
    esac
done

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "$script_dir/.." && pwd)"
cd "$repository_root"

current_version="$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version | tr -d '\r' | tail -n 1)"
calculator_arguments=("$current_version")
if [[ -n "$release_override" ]]; then
    calculator_arguments+=("$release_override")
fi

plan="$($script_dir/calculate-release-version.sh "${calculator_arguments[@]}")"
release_version="$(printf '%s\n' "$plan" | awk -F= '$1 == "release_version" { print $2 }')"
next_version="$(printf '%s\n' "$plan" | awk -F= '$1 == "next_version" { print $2 }')"

printf '%s\n' "$plan"

if [[ "$dry_run" == false ]]; then
    ./mvnw -B org.codehaus.mojo:versions-maven-plugin:2.21.0:set \
        -DnewVersion="$release_version" \
        -DgenerateBackupPoms=false \
        -DprocessAllModules=true

    effective_version="$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version | tr -d '\r' | tail -n 1)"
    if [[ "$effective_version" != "$release_version" ]]; then
        echo "Expected POM version $release_version after update, found $effective_version" >&2
        exit 1
    fi
fi

if [[ -n "$output_file" ]]; then
    printf 'current_version=%s\n' "$current_version" >> "$output_file"
    printf 'release_version=%s\n' "$release_version" >> "$output_file"
    printf 'next_version=%s\n' "$next_version" >> "$output_file"
fi
