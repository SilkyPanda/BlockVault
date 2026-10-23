#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/.."
fail=0

scan() {
  local pattern="$1" msg="$2" exclude="$3"
  shift 3
  local hits
  hits=$(grep -rEn --include='*.java' "$pattern" "$@" 2>/dev/null | grep -Ev "$exclude" || true)
  if [ -n "$hits" ]; then
    echo "$hits"
    echo "::error::$msg"
    fail=1
  fi
}

DIRS="core/src/main shared-client/src/main fabric/src neoforge/src"
scan 'import java\.net\.|import javax\.net\.|HttpClient|HttpURLConnection|URLConnection|java\.net\.Socket' 'Networking APIs are forbidden (the mod must make no network connections)' '^$' $DIRS
scan 'System\.(out|err)\.|printStackTrace\(' 'Use the logger; never System.out/err/printStackTrace' '^$' $DIRS
scan 'ProcessBuilder|Runtime\.getRuntime\(\)\.exec' 'External process execution is forbidden' '^$' $DIRS

if grep -rEn --include='*.java' '@(Inject|Redirect|ModifyArg|ModifyVariable|ModifyExpressionValue|WrapOperation)\(' shared-client/src 2>/dev/null | grep -v 'require *= *0' >/dev/null; then
  echo '::warning::Check that every mixin injector declares require = 0 (multi-line annotations may be false positives)'
fi

for f in LICENSE README.md STATUS.md DECISIONS.md .github/workflows/build.yml; do
  [ -f "$f" ] || { echo "::error::missing $f"; fail=1; }
done

if grep -Eiq 'softprops/action-gh-release|publish|modrinth|curseforge|secrets\.' .github/workflows/build.yml; then
  echo '::error::Release/publish/secrets are forbidden in the workflow (manual releases only)'
  fail=1
fi

exit "$fail"
