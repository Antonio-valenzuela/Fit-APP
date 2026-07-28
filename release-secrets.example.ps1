# Template only. Copy this file to a protected directory outside the repository,
# replace every placeholder from a password manager, and never commit the copy.
$env:KEYSTORE_PATH = '<absolute-path-outside-repository>'
$env:STORE_PASSWORD = '<load-from-password-manager>'
$env:KEY_ALIAS = '<release-key-alias>'
$env:KEY_PASSWORD = '<load-from-password-manager>'

if ($env:KEYSTORE_PATH.StartsWith('<') -or
    $env:STORE_PASSWORD.StartsWith('<') -or
    $env:KEY_ALIAS.StartsWith('<') -or
    $env:KEY_PASSWORD.StartsWith('<')) {
  throw 'Replace every placeholder before using release signing.'
}
