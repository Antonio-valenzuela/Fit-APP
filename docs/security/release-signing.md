# Firma fail-closed de release

Los builds debug y las pruebas no requieren credenciales de release. En cambio,
`assembleRelease`, `bundleRelease`, `packageRelease` y las tareas de publicacion
fallan de forma explicita cuando la configuracion esta incompleta, el keystore no
existe, la ruta es relativa o apunta dentro del proyecto. No existe fallback a una
debug key ni a un keystore local del repositorio.

## Generar y verificar el AAB

Importa las variables desde el archivo externo protegido y valida primero:

```powershell
. "$env:USERPROFILE\.fitplan\signing\release-secrets.local.ps1"
.\gradlew.bat :app:validateReleaseSigning --dependency-verification strict
.\gradlew.bat :app:bundleRelease --dependency-verification strict
```

El artefacto esperado es
`app\build\outputs\bundle\release\app-release.aab`. Verifica su firma con el JDK:

```powershell
jarsigner -verify -verbose -certs .\app\build\outputs\bundle\release\app-release.aab
keytool -printcert -jarfile .\app\build\outputs\bundle\release\app-release.aab
```

Consulta la entrada del keystore sin poner el password en la linea de comandos;
`keytool` lo solicitara de forma interactiva:

```powershell
keytool -list -v -keystore "$env:KEYSTORE_PATH" -alias "$env:KEY_ALIAS"
```

La huella SHA-256 del certificado del AAB debe coincidir con la entrada autorizada
para subir versiones. Conserva como evidencia el SHA-256 del AAB, el resultado de
`jarsigner -verify` y la huella del certificado. No guardes passwords en logs.

## Rotacion y recuperacion

Antes de rotar, confirma si Google Play App Signing administra la app signing key o
solo la upload key. Conserva al menos dos respaldos cifrados y prueba su restauracion.
Una perdida o rotacion equivocada puede impedir futuras actualizaciones; sigue el
flujo de recuperacion del proveedor antes de revocar el material anterior.
