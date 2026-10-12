#!/usr/bin/env bash
# 统一测试入口：被 .claude/settings.json 的 PostToolUse 钩子与 CI（.github/workflows/ai-review.yml）共用。
# 用法：
#   scripts/run-tests.sh              # 全量测试（无测试模块的模块不报错）
#   scripts/run-tests.sh MODULE       # 仅测该模块及其依赖，例如 smart-parking-order
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1
MODULE="${1:-}"

if [[ -n "$MODULE" ]]; then
  echo ">> 运行模块测试: $MODULE (含依赖 -am)"
  mvn -B -pl "$MODULE" -am test -DfailIfNoTests=false
else
  echo ">> 运行全量测试 (允许无测试模块)"
  mvn -B test -DfailIfNoTests=false
fi
