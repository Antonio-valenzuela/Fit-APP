# Configuracion segura de secretos de release

La firma de release debe vivir fuera del repositorio. El build acepta exclusivamente
estas variables de entorno: `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS` y
`KEY_PASSWORD`. `KEYSTORE_PATH` debe ser absoluto, apuntar a un archivo existente y
estar fuera del directorio del proyecto.

No pongas estas variables en `.env`: el build Android procesa ese archivo. Usa el
administrador de secretos del CI o un script PowerShell local protegido fuera del
repositorio. `release-secrets.example.ps1` contiene solamente placeholders.

Estado local verificado el 19 de julio de 2026: los tres keystores y el script local
se retiraron del checkout y quedaron en `C:\Users\yahir\.fitplan\signing`. Sus
copias SHA-256 coincidieron antes del movimiento. La carpeta no hereda permisos y
concede acceso solamente a la identidad de Windows actual. También existe el
respaldo recuperable `repo-original-backup-2026-07-19` dentro de esa misma carpeta;
no se elimino ningun keystore.

## Migracion local sin perder la identidad

No borres ni muevas el material original hasta completar todos los pasos:

1. Haz una copia de respaldo cifrada y desconectada.
2. Registra el SHA-256 de cada archivo original sin abrirlo:

   ```powershell
   Get-FileHash -Algorithm SHA256 -LiteralPath .\my-upload-key.jks
   Get-FileHash -Algorithm SHA256 -LiteralPath .\fitplan-upload-key.p12
   Get-FileHash -Algorithm SHA256 -LiteralPath .\fitplan-upload-key-v2.p12
   Get-FileHash -Algorithm SHA256 -LiteralPath .\release-secrets.local.ps1
   ```

3. Crea una ubicacion privada y copia, no muevas, el archivo elegido:

   ```powershell
   $secureDir = Join-Path $env:USERPROFILE '.fitplan\signing'
   New-Item -ItemType Directory -Force -Path $secureDir | Out-Null
   Copy-Item -LiteralPath .\my-upload-key.jks -Destination $secureDir
   ```

4. Compara los hashes original/copia. Deben ser identicos. Si no coinciden, detente.
5. Restringe la carpeta a la identidad de Windows actual y verifica el ACL:

   ```powershell
   $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
   icacls $secureDir /inheritance:r /grant:r "${identity}:(OI)(CI)F" /T
   Get-Acl -LiteralPath $secureDir | Format-List Owner,AccessToString
   ```

6. Copia `release-secrets.example.ps1` a esa carpeta, sustituye los placeholders
   desde un administrador de contrasenas y establece `KEYSTORE_PATH` a la copia.
7. Importa el script por su ruta externa y valida sin mostrar valores:

   ```powershell
   . "$env:USERPROFILE\.fitplan\signing\release-secrets.local.ps1"
   .\gradlew.bat :app:validateReleaseSigning --dependency-verification strict
   ```

8. Genera y verifica un AAB firmado siguiendo `docs/security/release-signing.md`.
   Solo despues de confirmar hash, alias, huella del certificado, respaldo y release
   firmado puede retirarse la copia del repositorio.

No pases passwords como argumentos de `keytool`, Gradle o scripts: pueden quedar en
el historial o en la lista de procesos. Permite que `keytool` solicite el password o
inyectalo mediante el almacen de secretos del CI.

## CI

Guarda passwords y alias como secretos enmascarados. Materializa el keystore en un
archivo temporal fuera del checkout, limita sus permisos, establece las cuatro
variables solo durante el job y destruye el archivo temporal al terminar. El job de
release debe ejecutar verificacion estricta de dependencias y conservar el hash y la
huella del firmante como evidencia, nunca los secretos.
