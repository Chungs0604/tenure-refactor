#!/bin/bash
# 이미 커밋된 Flyway 마이그레이션을 고치면 체크섬이 깨져 다른 환경의 빌드가 실패한다
set -euo pipefail

input=$(cat)
file_path=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty')
[ -z "$file_path" ] && exit 0

migration_dir="src/main/resources/db/migration"
case "$file_path" in
  *"$migration_dir"/V*.sql) ;;
  *) exit 0 ;;
esac

root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
case "$file_path" in
  /*) abs="$file_path" ;;
  *) abs="$root/$file_path" ;;
esac
rel="${abs#"$root"/}"

if git -C "$root" cat-file -e "HEAD:$rel" 2>/dev/null; then
  next=$(( $(ls "$root/$migration_dir" | grep -oE '^V[0-9]+' | tr -d V | sort -n | tail -1) + 1 ))
  reason="${rel} 은 이미 커밋된 Flyway 마이그레이션이다. 수정하면 체크섬이 깨져 다른 환경의 빌드가 실패한다. 내용을 고치지 말고 V${next}__<설명>.sql 로 새 마이그레이션을 추가해라."
  jq -n --arg reason "$reason" '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $reason}}'
fi

exit 0
