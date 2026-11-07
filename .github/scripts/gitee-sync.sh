#!/usr/bin/env bash
# 把 GitHub 最新正式版同步到 Gitee 发行版（国内下载更新用）。
# 11-07 改：GitHub(美国)→Gitee 的跨境线路时好时坏，好时 13 秒传完，坏时连续卡死。
#   - 不再“3 次失败就放弃、等 2 小时定时器”（定时器实际常隔 5~9 小时才跑）：同一个任务里持续重试，最长 DEADLINE_MIN 分钟。
#   - 每次超时后先查 Gitee 上文件到底到了没有（超时可能只是回复没回来），没到才重传；不再删除发行版，避免误删已传上的包。
#   - 每轮重读 GitHub 最新版本，期间又发了新版就直接改传新版。
#   - 试验：接口上传失败 2 次后，顺带用 git 推送到 Gitee 仓库的 apk 分支，记录耗时，评估能否做备用通道（App 暂不使用）。
set -uo pipefail
: "${GITEE_TOKEN:?}" ; : "${GITEE_REPO:?}" ; : "${GITHUB_REPOSITORY:?}"
API="https://gitee.com/api/v5/repos/${GITEE_REPO}"
DEADLINE_MIN="${DEADLINE_MIN:-120}"
START=$(date +%s)
log() { echo "[$(TZ=Asia/Shanghai date +%H:%M:%S)] $*"; }
elapsed_min() { echo $(( ($(date +%s) - START) / 60 )); }
gq() { curl -sS --connect-timeout 20 --max-time 60 "$@"; }   # Gitee 小请求

has_apk() {  # $1=tag → 输出发行版 id（有安装包时返回 0）
  local j; j=$(gq "${API}/releases/tags/$1?access_token=${GITEE_TOKEN}" 2>/dev/null) || return 2
  echo "$j" | jq -r '.id // empty' 2>/dev/null
  [ "$(echo "$j" | jq -r '[.assets[]? | select(.name|endswith(".apk"))] | length' 2>/dev/null || echo 0)" -gt 0 ]
}

cleanup() {  # 只保留最近 10 个；删除其他版本的空发行版
  gq "${API}/releases?access_token=${GITEE_TOKEN}&per_page=100" > all.json || return 0
  jq -r --arg t "$1" '.[] | select(.tag_name != $t) | select([.assets[]? | select(.name|endswith(".apk"))] | length == 0) | "\(.id) \(.tag_name)"' all.json 2>/dev/null \
    | while read -r EID ETAG; do
        gq -X DELETE "${API}/releases/${EID}?access_token=${GITEE_TOKEN}" >/dev/null && log "删除空发行版 ${ETAG}"
        gq -X DELETE "${API}/tags/${ETAG}?access_token=${GITEE_TOKEN}" >/dev/null || true
      done
  jq -r 'sort_by(.id) | reverse | .[10:] | .[].id' all.json 2>/dev/null | while read -r OLD; do
    gq -X DELETE "${API}/releases/${OLD}?access_token=${GITEE_TOKEN}" >/dev/null && log "删除旧发行版 ${OLD}"
  done
}

git_push_trial() {  # 试验通道：记录 git 推送耗时，不影响结果
  local t0=$(date +%s) d; d=$(mktemp -d)
  ( cd "$d" && git init -q && git checkout -q -b apk && cp "$OLDPWD/$1" EricToolbox-latest.apk && printf '%s\n' "$2" > VERSION \
    && git add . && git -c user.name=ci -c user.email=ci@localhost commit -qm "$2" \
    && timeout 300 git push -q -f "https://oauth2:${GITEE_TOKEN}@gitee.com/${GITEE_REPO}.git" apk ) >/dev/null 2>&1
  local rc=$?; log "试验 git 推送 $2：$([ $rc = 0 ] && echo 成功 || echo "失败 rc=$rc")，用时 $(( $(date +%s) - t0 )) 秒"
  rm -rf "$d"; GIT_TRIED=1
}

TRY=0; CUR_TAG=""; GIT_TRIED=""
while :; do
  REL_JSON=$(gh api "repos/${GITHUB_REPOSITORY}/releases/latest" 2>/dev/null) || { sleep 30; continue; }
  TAG=$(echo "$REL_JSON" | jq -r '.tag_name')
  APK_NAME=$(echo "$REL_JSON" | jq -r '[.assets[] | select(.name|endswith(".apk"))][0].name')
  APK_ID=$(echo "$REL_JSON" | jq -r '[.assets[] | select(.name|endswith(".apk"))][0].id')
  if [ -z "$TAG" ] || [ "$APK_NAME" = "null" ]; then log "GitHub 上没有可同步的版本"; exit 0; fi
  if [ "$TAG" != "$CUR_TAG" ]; then
    [ -n "$CUR_TAG" ] && log "期间发布了新版，改传 ${TAG}"
    CUR_TAG="$TAG"; TRY=0; GIT_TRIED=""
    rm -f ./*.apk; gh api -H "Accept: application/octet-stream" "repos/${GITHUB_REPOSITORY}/releases/assets/${APK_ID}" > "$APK_NAME"
    log "GitHub 最新：${TAG} $(stat -c %s "$APK_NAME") 字节"
  fi

  RID=$(has_apk "$TAG"); rc=$?
  if [ $rc = 0 ]; then log "Gitee 已有 ${TAG} 的安装包"; cleanup "$TAG"; log "已同步 ${TAG}（总用时 $(elapsed_min) 分钟）"; exit 0; fi

  if [ "$(elapsed_min)" -ge "$DEADLINE_MIN" ]; then
    echo "::warning::${DEADLINE_MIN} 分钟内没能把 ${TAG} 传到 Gitee（跨境线路不通）。下次定时任务会继续；在此之前 App 会提示从 GitHub 下载。"
    exit 0
  fi
  TRY=$((TRY+1))

  if [ $rc = 2 ]; then log "第 ${TRY} 轮：连不上 Gitee"; else
    if [ -z "$RID" ]; then
      BRANCH=$(gq "${API}?access_token=${GITEE_TOKEN}" | jq -r '.default_branch // empty'); BRANCH=${BRANCH:-master}
      NOTES=$(echo "$REL_JSON" | jq -r '.body' | head -20)
      RID=$(gq -X POST "${API}/releases" -F "access_token=${GITEE_TOKEN}" -F "tag_name=${TAG}" -F "name=${TAG}" \
            -F "body=${NOTES}" -F "target_commitish=${BRANCH}" | jq -r '.id // empty' 2>/dev/null)
    fi
    if [ -z "$RID" ]; then log "第 ${TRY} 轮：创建发行版失败"; else
      T0=$(date +%s)
      # 每次最多 3 分钟；传输 45 秒低于 20KB/s 视为卡住
      RES=$(curl -sS --http1.1 -H "Expect:" --connect-timeout 20 --max-time 180 --speed-time 45 --speed-limit 20000 \
            -X POST "${API}/releases/${RID}/attach_files" -F "access_token=${GITEE_TOKEN}" -F "file=@${APK_NAME}" 2>&1)
      if echo "$RES" | jq -e '.name' >/dev/null 2>&1; then log "第 ${TRY} 轮：上传成功，用时 $(( $(date +%s) - T0 )) 秒"; continue; fi
      log "第 ${TRY} 轮：上传未确认（$(echo "$RES" | tail -c 120 | tr '\n' ' ')），用时 $(( $(date +%s) - T0 )) 秒，先核对 Gitee"
      sleep 20   # 下一轮开头用 has_apk 核对
    fi
  fi
  [ "$TRY" -ge 2 ] && [ -z "$GIT_TRIED" ] && git_push_trial "$APK_NAME" "$TAG"
  WAIT=$(( TRY < 4 ? 30 : 90 )); sleep "$WAIT"
done
