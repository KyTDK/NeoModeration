#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <commit-message-file>" >&2
  exit 2
fi

message_file=$1

if grep -Eiq '(codex|claude|cursor|coding[[:space:]-]+agent|ai[[:space:]-]+agent|generated[[:space:]-]+by|made-with|co-authored-by:.*(agent|codex|claude|cursor))' "$message_file"; then
  echo "Commit message rejected: remove tool or coding-agent attribution markers." >&2
  exit 1
fi
