# Publicar o actualizar AetherScape desde Termux

El script actualizado funciona tanto si el repositorio todavía no existe como si `AetherScape` ya existe en tu cuenta. Para evitar los errores de permisos del almacenamiento compartido, trabaja dentro de `$HOME` de Termux.

## 1. Preparar Termux

```bash
pkg update
pkg install git gh unzip
gh auth login
```

## 2. Extraer el paquete en el almacenamiento privado de Termux

```bash
cd "$HOME"
rm -rf AetherScape-release
mkdir AetherScape-release
unzip "$HOME/storage/downloads/AetherScape-v0.2.0-beta.2-fixed-source.zip" -d AetherScape-release
cd "$HOME/AetherScape-release/AetherScape-beta"
```

Comprueba que estás en la carpeta correcta:

```bash
ls
```

Debes ver `app`, `scripts`, `settings.gradle`, `README.md` y otros archivos. No debe aparecer otra carpeta `AetherScape-beta` dentro.

## 3. Validar

```bash
bash scripts/validate.sh
```

## 4. Publicar la actualización

```bash
bash scripts/publish-termux.sh AetherScape v0.2.0-beta.2
```

El script:

- detecta tu usuario de GitHub;
- comprueba si el repositorio ya existe;
- si existe, lo clona y reemplaza su contenido por la actualización;
- si no existe, lo crea;
- hace commit y push;
- crea el tag de la beta;
- activa el workflow de GitHub Actions.

## 5. Revisar la compilación

```bash
OWNER="$(gh api user --jq .login)"
gh run list --repo "$OWNER/AetherScape"
gh run watch --repo "$OWNER/AetherScape"
```

Al terminar, abre la sección **Releases** del repositorio y descarga el APK generado.

## Clima

La beta permite seleccionar Open-Meteo sin clave, Google Weather API, OpenWeatherMap o WeatherAPI.com desde la pestaña **Clima**.
