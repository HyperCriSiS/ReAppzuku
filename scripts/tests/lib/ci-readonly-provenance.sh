#!/usr/bin/env bash
# Read-only ADB CI diagnostics. No action here grants recovery authorization.
readonly_mode() {
  case "$1" in allow|ignore|default|foreground|errored|deny|ask) return 0;; *) return 1;; esac
}
validate_readonly_appops() {
  local raw="$1" op="$2" line detail mode suffix rows=0 uid=0 pkg=0
  local suffix_regex='^[[:space:]]*(time|rejectTime|duration)=[^;]+$'
  [[ -n "$raw" && "$op" =~ ^[A-Z][A-Z0-9_]{0,63}$ ]] || return 1
  [[ "$raw" == 'No operations.' ]] && return 0
  if [[ "$raw" == $'No operations.\nDefault mode: '* ]]; then
    readonly_mode "${raw#*Default mode: }"
    return $?
  fi
  while IFS= read -r line || [[ -n "$line" ]]; do
    ((rows+=1))
    ((rows<=2)) || return 1
    if [[ "$line" == "Uid mode: $op: "* ]]; then
      ((uid==0)) || return 1
      uid=1
      detail="${line#Uid mode: $op: }"
    elif [[ "$line" == "$op: "* ]]; then
      ((pkg==0)) || return 1
      pkg=1
      detail="${line#$op: }"
    else
      return 1
    fi
    mode="${detail%%;*}"
    readonly_mode "$mode" || return 1
    if [[ "$detail" == *';'* ]]; then
      suffix="${detail#*;}"
      [[ "$suffix" =~ $suffix_regex ]] || return 1
    fi
  done <<< "$raw"
  ((pkg+uid>=1))
}
validate_readonly_deviceidle() {
  local raw="$1" line cat pkg numeric id key
  [[ -n "$raw" ]] || return 1
  local -A seen=() ids=()
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^(system|user|system-excidle),([A-Za-z0-9_.]+),([0-9]+)$ ]] || return 1
    cat="${BASH_REMATCH[1]}" pkg="${BASH_REMATCH[2]}" numeric="${BASH_REMATCH[3]}"
    ((${#numeric}<=10)) || return 1
    id=$((10#$numeric))
    ((id>0 && id<=2147483647)) || return 1
    key="$cat,$pkg"
    [[ ! -v seen["$key"] ]] || return 1
    seen["$key"]=1
    if [[ -v ids["$pkg"] ]]; then
      [[ "${ids[$pkg]}" == "$id" ]] || return 1
    fi
    ids["$pkg"]="$id"
  done <<< "$raw"
  ((${#seen[@]}>0))
}
# Double-read only; matching text is diagnostic, never proof of original state.
readonly_capture_pair() {
  local label="$1" max="$2" shape="$3" op="$4" first='' second='' valid=0
  shift 4
  [[ "$label" =~ ^READONLY_[A-Z0-9_]+$ && "$max" =~ ^[0-9]+$ ]] || return 2
  if ! first="$("$@" 2>&1)"; then
    printf '%s=UNAVAILABLE_UNKNOWN\n' "$label"; return 0
  fi
  if ! second="$("$@" 2>&1)"; then
    printf '%s=UNAVAILABLE_UNKNOWN\n' "$label"; return 0
  fi
  first="${first//$'\r'/}"
  second="${second//$'\r'/}"
  if [[ -z "$first" || -z "$second" ]]; then
    printf '%s=EMPTY_UNKNOWN\n' "$label"; return 0
  fi
  if ((${#first}>max || ${#second}>max)); then
    printf '%s=OUTPUT_TOO_LARGE_UNKNOWN\n' "$label"; return 0
  fi
  if [[ "$first" != "$second" ]]; then
    printf '%s=CHANGED_DURING_READ_UNKNOWN\n' "$label"; return 0
  fi
  case "$shape" in
    appops) validate_readonly_appops "$first" "$op" && valid=1 ;;
    deviceidle) validate_readonly_deviceidle "$first" && valid=1 ;;
    *) return 2 ;;
  esac
  if ((valid==0)); then
    printf '%s=UNRECOGNIZED_OUTPUT_UNKNOWN\n' "$label"; return 0
  fi
  printf '%s_BEGIN\n%s\n%s_END\n' "$label" "$first" "$label"
  printf '%s=RAW_DOUBLE_READ_MATCH_DIAGNOSTIC_ONLY\n' "$label"
}
