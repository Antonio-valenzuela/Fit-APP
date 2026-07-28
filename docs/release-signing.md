# Firma de release

La configuracion canonica es fail-closed y usa un keystore protegido fuera del
repositorio. Consulta:

- `docs/security/secrets-setup.md` para migrar secretos conservando hash, identidad
  y ACL.
- `docs/security/release-signing.md` para generar y verificar el AAB firmado.

No uses keystores ni scripts de passwords ubicados en la raiz del proyecto.
