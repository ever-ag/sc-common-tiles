#!/usr/bin/env bash
# Prints the reactor modules whose ${VERSION} is not on GitHub Packages yet, as a
# comma-separated Maven -pl list (groupId:artifactId). Empty output means every module
# is published. Modules with maven.deploy.skip=true are ignored.
#
# Needs: VERSION, MAVEN_USER, MAVEN_TOKEN, GITHUB_REPOSITORY. Run from the repository root.
set -euo pipefail
: "${VERSION:?}" "${MAVEN_USER:?}" "${MAVEN_TOKEN:?}" "${GITHUB_REPOSITORY:?}"

# Capture first so a Maven failure stops the script instead of looking like "nothing missing".
modules=$(mvn -B -q org.codehaus.mojo:exec-maven-plugin:3.6.3:exec \
  -Dexec.executable=echo -Dexec.args='${project.groupId}:${project.artifactId} ${maven.deploy.skip}')
if [[ -z "$modules" ]]; then
  echo "::error::Could not list the reactor modules." >&2
  exit 1
fi

missing=()
while read -r ga skip; do
  [[ -z "$ga" || "$skip" == "true" ]] && continue
  group=${ga%%:*}
  artifact=${ga##*:}
  url="https://maven.pkg.github.com/${GITHUB_REPOSITORY}/${group//.//}/${artifact}/${VERSION}/${artifact}-${VERSION}.pom"
  status=$(curl -s -o /dev/null -w '%{http_code}' -u "${MAVEN_USER}:${MAVEN_TOKEN}" "$url")
  case "$status" in
    200|302) ;;
    404) missing+=("$ga") ;;
    *)
      echo "::error::Unexpected HTTP $status while checking $url" >&2
      exit 1 ;;
  esac
done <<< "$modules"

(IFS=,; echo "${missing[*]-}")
