#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO_NAME="${1:-AetherScape}"
TAG="${2:-v0.1.0-beta.1}"

command -v git >/dev/null || { echo "Instala git: pkg install git"; exit 1; }
command -v gh >/dev/null || { echo "Instala GitHub CLI: pkg install gh"; exit 1; }

gh auth status

git init
git branch -M main
git add .
if ! git diff --cached --quiet; then
  git commit -m "feat: publicar prototipo beta de AetherScape"
fi

gh repo create "$REPO_NAME" --public --source=. --remote=origin --push

git tag -a "$TAG" -m "AetherScape beta inicial"
git push origin "$TAG"

echo
echo "Repositorio publicado. El tag $TAG activó el workflow de Release."
echo "Revisa: gh run list"
