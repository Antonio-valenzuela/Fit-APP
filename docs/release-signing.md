# FitPlan MX - Firma de release y secretos locales

Este proyecto usa variables de entorno para firmar release:

- `KEYSTORE_PATH`
- `STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## Archivos locales generados

- `my-upload-key.jks`: upload key local.
- `release-secrets.local.ps1`: variables de entorno locales para firmar.

Ambos estan ignorados por git. No los subas a ningun repositorio ni los compartas publicamente.

## Usar secretos locales

En PowerShell, desde la raiz del proyecto:

```powershell
. .\release-secrets.local.ps1
.\gradlew.bat :app:bundleRelease
```

El AAB firmado queda en:

```text
app\build\outputs\bundle\release\app-release.aab
```

## Verificar firma

```powershell
& 'C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot\bin\jarsigner.exe' -verify -verbose app\build\outputs\bundle\release\app-release.aab
```

Debe mostrar `jar verified`.

## Cuidado

Si pierdes el upload key o sus passwords, puedes quedar bloqueado para actualizar la app hasta pasar por recuperacion de Play App Signing. Guarda una copia segura fuera del proyecto.

