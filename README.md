# 🌙 Lumora

Reproductor multimedia basado en extensiones, creado por **Miguel**.
Fork de [CloudStream](https://github.com/recloudstream/cloudstream) (GPL-3.0, ver [NOTICE.md](NOTICE.md)).

> ⚠️ Lumora no trae fuentes de video por defecto. Todo el contenido viene de las extensiones
> (repositorios) que añadas. Usa solo extensiones de contenido que tengas derecho a ver.

## ✨ Qué trae

- **Perfiles propios:** la primera vez que alguien abre Lumora aparece la pantalla de perfiles lista para
  crear o renombrar el suyo. Cada perfil tiene su propio historial, favoritos, "seguir viendo" y sus propias
  cuentas de AniList / MAL / Simkl. Se pueden añadir más en cualquier momento.
- **Auto‑actualización por GitHub:** subes versión → todos reciben el aviso para actualizar.
- **Repositorios de extensiones por defecto** (opcional) configurables en un solo archivo.

---

## 1) Subir el proyecto a GitHub (una sola vez)

1. Crea el repositorio **kaspercito/Lumora** como **público** (la app lee los *Releases* sin iniciar sesión;
   si es privado, las actualizaciones no funcionan).
2. Sube todo este proyecto a la rama **main**. Los datos del repo ya están en `gradle.properties`:

   ```properties
   lumora.githubUser=kaspercito
   lumora.githubRepo=Lumora
   lumora.defaultRepos=
   ```

## 2) Crea tu llave de firma (muy importante)

Android solo acepta una actualización si el APK está firmado con **la misma llave** que el instalado.
Créala **una vez** y guárdala en un lugar seguro (si la pierdes, todos tendrán que desinstalar y reinstalar):

```bash
keytool -genkeypair -v -keystore lumora.jks -alias lumora -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 lumora.jks > lumora.jks.base64     # en Mac: base64 -i lumora.jks
```

En GitHub → *Settings → Secrets and variables → Actions → New repository secret*, crea:

| Secret | Valor |
|---|---|
| `LUMORA_KEYSTORE_BASE64` | contenido de `lumora.jks.base64` |
| `LUMORA_KEYSTORE_PASSWORD` | la contraseña del keystore |
| `LUMORA_KEY_ALIAS` | `lumora` |
| `LUMORA_KEY_PASSWORD` | la contraseña de la llave |

## 3) Primera versión (1.0.0)

Con los secretos creados y el código en `main`, el workflow *Publicar nueva versión de Lumora* corre solo
(pestaña **Actions**), compila y crea el Release **v1.0.0** con el archivo `1.0.0.apk`.
Ese APK es el que instalas en cada celular (permite "instalar apps desconocidas").

## 4) Cómo actualizar (cada vez que cambies algo)

1. Haz tus cambios en el código.
2. En `gradle/libs.versions.toml` sube las dos versiones:
   ```toml
   versionCode = "2"        # +1 cada vez
   versionName = "1.0.1"    # la nueva versión
   ```
3. `git add . && git commit -m "Lumora 1.0.1" && git push`
4. Espera unos minutos: Actions compila y publica el Release `v1.0.1` con `1.0.1.apk`.
5. Cuando los demás abren Lumora, les sale **"Actualizar"** y se instala sobre la anterior sin perder sus perfiles.
   (También pueden buscarla en *Ajustes → Actualizaciones*.)

Reglas: si no subes `versionName`, no se publica nada nuevo; el APK siempre se llama como `versionName`
(el workflow lo hace solo); y la llave de firma nunca cambia.

## 5) Primer uso en cada celular

Al abrir, cada persona crea o renombra su perfil, sale del modo edición y lo selecciona.
Para que no pregunte cada vez: *Ajustes → Cuentas → Omitir la selección de cuenta al inicio*.
Los perfiles viven en cada celular; para pasar tu historial a otro teléfono usa *Ajustes → Copia de seguridad*.

## 6) Extensiones

- Manual: *Ajustes → Extensiones → Añadir repositorio* y pega la URL.
- Automático: en `gradle.properties` pon `lumora.defaultRepos=Nombre|https://url/repo.json;Otro|https://...`
  (se añaden solo en la primera instalación).

## 7) Cambiar nombre / icono

- Nombre: `app/src/main/res/values/strings.xml` → `app_name` (y los demás idiomas).
- Repositorios por defecto: `lumora.defaultRepos` en `gradle.properties`.
- Icono: edita y ejecuta `tools/make_icons.py` (genera vectores y PNG).

## Detalles técnicos

- `applicationId`: `com.miguel.lumora`. Las carpetas del código se llaman `com/miguel/lumora`, pero las
  líneas `package com.lagradost.cloudstream3` dentro de los archivos se dejaron a propósito: las extensiones
  se compilan contra ese nombre de paquete y dejarían de funcionar si se renombra.
- Compilar local: `./gradlew assembleStableDebug` (Android Studio recomendado, JDK 17).
