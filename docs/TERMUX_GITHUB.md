# Publicar AetherScape desde Termux

```bash
pkg update
pkg install git gh unzip tar
termux-setup-storage
gh auth login
```

Extrae el proyecto dentro del almacenamiento privado de Termux:

```bash
cd "$HOME"
rm -rf "$HOME/AetherScape-release"
mkdir -p "$HOME/AetherScape-release"

unzip -o \
  "$HOME/storage/downloads/AetherScape-v0.7.0-beta.9-validation-fix-source.zip" \
  -d "$HOME/AetherScape-release"

cd "$HOME/AetherScape-release/AetherScape-beta"
bash scripts/validate.sh
bash scripts/publish-termux.sh AetherScape v0.7.0-beta.9
```

Comprobar la compilación:

```bash
OWNER="$(gh api user --jq .login)"
gh run list --repo "$OWNER/AetherScape" --limit 5
```
