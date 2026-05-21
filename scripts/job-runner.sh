#!/usr/bin/env bash
set -euo pipefail

# Cross-platform-style helper for explicit job-config flows on Linux/macOS.
#
# Prepare model classes:
#   ./scripts/job-runner.sh prepare tmp-test-config/customer-load-reject-demo/job-config.yaml
#
# Run a specific job config:
#   ./scripts/job-runner.sh run tmp-test-config/customer-load-reject-demo/job-config.yaml
#
# Do both in one call:
#   ./scripts/job-runner.sh both tmp-test-config/customer-load-reject-demo/job-config.yaml

usage() {
  cat <<'EOF'
Usage:
  ./scripts/job-runner.sh <prepare|run|both> <job-config-path> [--no-skip-tests] [--dry-run]

Examples:
  ./scripts/job-runner.sh prepare tmp-test-config/customer-load-reject-demo/job-config.yaml
  ./scripts/job-runner.sh run tmp-test-config/customer-load-reject-demo/job-config.yaml
  ./scripts/job-runner.sh both private-jobs/acme/order-flow/config/job-config.yaml --dry-run
EOF
}

if [[ $# -lt 2 ]]; then
  usage
  exit 1
fi

action="$1"
job_config_input="$2"
shift 2

skip_tests=true
dry_run=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-skip-tests)
      skip_tests=false
      ;;
    --dry-run)
      dry_run=true
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
  shift
done

case "$action" in
  prepare|run|both)
    ;;
  *)
    echo "Invalid action: $action" >&2
    usage
    exit 1
    ;;
esac

# Keep repo root dynamic: resolve from script location, not current shell location.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"

resolve_job_config() {
  local configured_path="$1"
  local absolute_path

  # Accept either direct path or repo-root-relative path.
  if [[ -f "$configured_path" ]]; then
    absolute_path="$(cd "$(dirname "$configured_path")" && pwd)/$(basename "$configured_path")"
  elif [[ -f "$repo_root/$configured_path" ]]; then
    absolute_path="$(cd "$(dirname "$repo_root/$configured_path")" && pwd)/$(basename "$configured_path")"
  else
    echo "Job config path not found: '$configured_path'" >&2
    exit 1
  fi

  if [[ "$absolute_path" == "$repo_root"/* ]]; then
    local relative_path="${absolute_path#$repo_root/}"
    echo "$relative_path"
  else
    echo "$absolute_path"
  fi
}

run_maven() {
  local cmd=("$@")
  echo "> mvn ${cmd[*]}"
  if [[ "$dry_run" == true ]]; then
    return 0
  fi
  mvn "${cmd[@]}"
}

job_config_for_maven="$(resolve_job_config "$job_config_input")"

cd "$repo_root"
echo "Repo root: $repo_root"
echo "Job config: $job_config_for_maven"

if [[ "$action" == "prepare" || "$action" == "both" ]]; then
  run_maven --no-transfer-progress -Pxml-generation "-Detl.xml.generation.jobConfig=$job_config_for_maven" process-classes
fi

if [[ "$action" == "run" || "$action" == "both" ]]; then
  run_args=(--no-transfer-progress)
  if [[ "$skip_tests" == true ]]; then
    run_args+=(-DskipTests)
  fi
  run_args+=("-Dspring-boot.run.jvmArguments=-Detl.config.job=$job_config_for_maven" spring-boot:run)
  run_maven "${run_args[@]}"
fi


