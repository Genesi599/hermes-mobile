[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$credentialFile = Join-Path $env:LOCALAPPDATA 'HermesControl\release-signing-password.dpapi'
$credentialDirectory = Split-Path -Parent $credentialFile
$entropy = [Text.Encoding]::UTF8.GetBytes('HermesControl-release-signing-v1')

$credential = Get-Credential -UserName 'hermes-mobile' -Message 'Enter the Hermes Mobile release keystore password once'
if ($null -eq $credential) {
    throw 'No password was supplied.'
}

$password = $credential.GetNetworkCredential().Password
$passwordBytes = [Text.Encoding]::UTF8.GetBytes($password)
try {
    $protectedBytes = [Security.Cryptography.ProtectedData]::Protect(
        $passwordBytes,
        $entropy,
        [Security.Cryptography.DataProtectionScope]::CurrentUser
    )
    New-Item -ItemType Directory -Force -Path $credentialDirectory | Out-Null
    [Convert]::ToBase64String($protectedBytes) |
        Set-Content -LiteralPath $credentialFile -Encoding Ascii -NoNewline
} finally {
    [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
    $password = $null
    $credential = $null
}

Write-Output "Saved Windows-user-encrypted signing credential to $credentialFile"
