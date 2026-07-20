#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO_NAME="${1:-AetherScape}"
TAG="${2:-v0.3.0-beta.4}"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
WORK_DIR="$HOME/.cache/aetherscape-publish-$REPO_NAME"

command -v git >/dev/null || { echo "Instala git: pkg install git"; exit 1; }
command -v gh >/dev/null || { echo "Instala GitHub CLI: pkg install gh"; exit 1; }
command -v tar >/dev/null || { echo "Falta tar en Termux"; exit 1; }

[ -d "$PROJECT_DIR/app" ] || {
  echo "Error: ejecuta este script desde el proyecto que contiene app/ y settings.gradle."
  exit 1
}
[ -f "$PROJECT_DIR/settings.gradle" ] || {
  echo "Error: no se encontró settings.gradle en $PROJECT_DIR"
  exit 1
}

gh auth status
OWNER="$(gh api user --jq .login)"
USER_ID="$(gh api user --jq .id)"
FULL_REPO="$OWNER/$REPO_NAME"

rm -rf "$WORK_DIR"
mkdir -p "$(dirname "$WORK_DIR")"

copy_project() {
  local destination="$1"
  find "$destination" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
  tar \
    --exclude='.git' \
    --exclude='.gradle' \
    --exclude='build' \
    --exclude='app/build' \
    -C "$PROJECT_DIR" -cf - . | tar -C "$destination" -xf -
}

if gh repo view "$FULL_REPO" >/dev/null 2>&1; then
  echo "El repositorio $FULL_REPO ya existe. Se actualizará sin intentar crearlo otra vez."
  gh repo clone "$FULL_REPO" "$WORK_DIR"
  copy_project "$WORK_DIR"
else
  echo "Creando el nuevo repositorio $FULL_REPO"
  mkdir -p "$WORK_DIR"
  copy_project "$WORK_DIR"
  cd "$WORK_DIR"
  git init -b main
fi

cd "$WORK_DIR"
git config user.name "$(gh api user --jq '.name // .login')"
git config user.email "${USER_ID}+${OWNER}@users.noreply.github.com"
git branch -M main

git add -A
if git diff --cached --quiet; then
  echo "No hay cambios nuevos para publicar."
else
  git commit -m "feat: actualizar AetherScape a $TAG"
fi

if git remote get-url origin >/dev/null 2>&1; then
  git push -u origin main
else
  gh repo create "$REPO_NAME" --public --source=. --remote=origin --push
fi

# Publica una etiqueta ligera. Si git push falla por un error temporal de refs,
# usa la API de GitHub como respaldo.
git tag -d "$TAG" >/dev/null 2>&1 || true
git tag "$TAG"
if ! git push origin "refs/tags/$TAG"; then
  echo "El push de la etiqueta falló; usando la API de GitHub como respaldo…"
  COMMIT_SHA="$(git rev-parse HEAD)"
  gh api -X DELETE "repos/$FULL_REPO/git/refs/tags/$TAG" >/dev/null 2>&1 || true
  gh api -X POST "repos/$FULL_REPO/git/refs" \
    -f ref="refs/tags/$TAG" \
    -f sha="$COMMIT_SHA"
fi

echo
echo "Actualización publicada en: https://github.com/$FULL_REPO"
echo "El tag $TAG activó el workflow de Release."
echo "Revisa la compilación con:"
echo "gh run list --repo $FULL_REPO"
echo "gh run watch --repo $FULL_REPO"
