#!/usr/bin/env bash
set -e

ROOT="$(git rev-parse --show-toplevel)"
git -C "$ROOT" config --local core.excludesFile "$ROOT/.afinityignore"
git -C "$ROOT" config --local core.hooksPath "$ROOT/.githooks"

echo "AFinity ignore rules and git hooks configured."