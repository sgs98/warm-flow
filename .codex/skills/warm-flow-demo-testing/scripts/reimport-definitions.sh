#!/usr/bin/env bash
# warm-flow-demo-testing：把 demo 的流程定义整理成「每个 flowCode 一条已发布定义」
#
# 背景：demo 的流程定义不由 SQL 灌入（见 warm-flow-demo/warm-flow-demo/src/main/resources/sql/data.sql
#       头部注释），统一放在 resources/json 下，文件名 = 定义里的 flowCode；导入接口永远生成新版本
#       且为未发布状态，必须再发布才可用于发起 / 定时启动 / 消息启动。
#
# 用途：定义被改乱（同编码多版本、未发布、结构残缺）时恢复成干净状态；换库后一次性灌入复测数据。
#
# 用法：
#   CONFIRM=YES bash scripts/reimport-definitions.sh
# 可覆盖变量：
#   BASE      demo 后端地址，默认 http://localhost:8080
#   REPO      仓库根，默认从脚本位置推断
#   JSONDIR   定义 json 目录，默认 $REPO/warm-flow-demo/warm-flow-demo/src/main/resources/json
#   DUMPDIR   删除前导出备份目录，默认 ${TMPDIR:-/tmp}/wf-defs-backup-<时间戳>
#
# 行为：导出备份当前全部定义 -> 逐个删除 -> 按 json 目录逐个导入 -> 逐个发布 -> 打印最终列表。
# 拒绝执行：存在流程实例（避免删掉仍被引用的定义）、后端不可达、备份或导入失败。
set -euo pipefail

BASE=${BASE:-http://localhost:8080}
REPO=${REPO:-$(cd "$(dirname "$0")/../../../.." && pwd)}
JSONDIR=${JSONDIR:-$REPO/warm-flow-demo/warm-flow-demo/src/main/resources/json}
TMPBASE=${TMPDIR:-/tmp}
DUMPDIR=${DUMPDIR:-${TMPBASE%/}/wf-defs-backup-$(date +%Y%m%d-%H%M%S)}

if [ "${CONFIRM:-}" != "YES" ]; then
  cat >&2 <<EOF
本操作会删除 ${BASE} 上全部流程定义（含各版本），再按 ${JSONDIR} 逐个导入并发布。
删除前会导出备份到 ${DUMPDIR}；存在流程实例时拒绝执行。
确认后重跑：CONFIRM=YES bash $0
EOF
  exit 1
fi

api() { curl -sS --max-time 30 "$@"; }
# JSON 取值：不依赖 jq，只用 python3
jq1() { python3 -c "import json,sys;d=json.load(sys.stdin);print($1)"; }

# 0) 后端可达
api "$BASE/api/definitions?pageNum=1&pageSize=1" >/dev/null

# 1) 无流程实例（引擎也会逐个拒绝，这里提前给出整体提示，避免删一半）
inst=$(api "$BASE/api/instances?pageNum=1&pageSize=1" | jq1 'd["data"]["total"]')
if [ "$inst" != "0" ]; then
  echo "存在 $inst 个流程实例，拒绝删除定义。先按业务清理实例，或换一个库复测。" >&2
  exit 1
fi

# 2) 导出备份 + 逐个删除
mkdir -p "$DUMPDIR"
api "$BASE/api/definitions?pageNum=1&pageSize=200" | python3 -c '
import json,sys
d=json.load(sys.stdin)["data"]
print("total=%s" % d["total"])
for r in d["list"]:
    print("\t".join([r["id"], r["flowCode"], r["version"], r["flowName"]]))
' | tee "$DUMPDIR/definitions.tsv"

tail -n +2 "$DUMPDIR/definitions.tsv" | while IFS=$'\t' read -r id code version name; do
  f="$DUMPDIR/${code}_v${version}_${id}.json"
  api -o "$f" "$BASE/api/definitions/$id/export"
  api -X DELETE "$BASE/api/definitions/$id" >/dev/null
  echo "deleted ${code} v${version} (${id})  备份: $(basename "$f")"
done

# 3) 逐个导入 + 发布
find "$JSONDIR" -maxdepth 1 -name '*.json' | sort | while read -r f; do
  n=$(basename "$f")
  id=$(api -X POST "$BASE/api/definitions/import" -F "file=@$f" | jq1 'd.get("data") or ""')
  if [ -z "$id" ]; then
    echo "导入失败：$n" >&2
    exit 1
  fi
  api -X PUT "$BASE/api/definitions/$id/publish" >/dev/null
  echo "imported + published ${n} -> id=${id}"
done

# 4) 最终列表
echo "--- 最终定义列表 ---"
api "$BASE/api/definitions?pageNum=1&pageSize=200" | python3 -c '
import json,sys
d=json.load(sys.stdin)["data"]
print("total=%s" % d["total"])
for r in d["list"]:
    print("  %-26s v%-3s isPublish=%s  %s" % (r["flowCode"], r["version"], r["isPublish"], r["flowName"]))
'
echo "备份目录：$DUMPDIR"
