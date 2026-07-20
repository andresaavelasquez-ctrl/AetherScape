# Publicar AetherScape desde Termux

La compilación pesada se realiza en GitHub Actions. Termux se usa para crear el repositorio, hacer commit, push y crear el tag que dispara la Release.

## 1. Preparar Termux

```bash
pkg update
pkg install git gh unzip
termux-setup-storage
gh auth login
```

En `gh auth login`, selecciona GitHub.com, HTTPS y acceso mediante navegador.

## 2. Extraer el paquete

Copia `AetherScape-v0.1.0-beta.1-source.zip` a Descargas y ejecuta:

```bash
cd ~/storage/downloads
unzip AetherScape-v0.1.0-beta.1-source.zip
cd AetherScape-beta
```

## 3. Configurar la identidad de Git

```bash
git config --global user.name "Andres Acevedo"
git config --global user.email "TU_CORREO_DE_GITHUB"
```

## 4. Validar el paquete

```bash
bash scripts/validate.sh
```

## 5. Crear repositorio, commit y push

Método automatizado:

```bash
bash scripts/publish-termux.sh AetherScape v0.1.0-beta.1
```

Método manual equivalente:

```bash
git init
git branch -M main
git add .
git commit -m "feat: publicar prototipo beta de AetherScape"
gh repo create AetherScape --public --source=. --remote=origin --push
```

## 6. Crear la primera Release

```bash
git tag -a v0.1.0-beta.1 -m "AetherScape beta inicial"
git push origin v0.1.0-beta.1
```

El workflow `.github/workflows/release.yml` compila el APK de depuración instalable y lo adjunta automáticamente a una Release marcada como prerelease.

## 7. Revisar la compilación

```bash
gh run list
gh run watch
```

Al terminar, abre la sección **Releases** del repositorio y descarga el APK generado.

## 8. Clave meteorológica

La clave no se escribe en el repositorio. Instala el APK, abre **Clima**, pega la clave de Google Weather API y pulsa **Guardar clave**. Para una versión pública se debe sustituir este mecanismo por un backend/proxy.
