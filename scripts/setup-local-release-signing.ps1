[CmdletBinding()]
param()

$credentialFile = Join-Path $env:LOCALAPPDATA 'HermesControl\release-signing-password.dpapi'
$credentialDirectory = Split-Path -Parent $credentialFile

$credential = Get-Credential -UserName 'hermes-mobile' -Message 'Enter the Hermes Mobile release keystore password once'
if ($null -eq $credential) {
    throw 'No password was supplied.'
}

New-Item -ItemType Directory -Force -Path $credentialDirectory | Out-Null
$credential.Password |
    ConvertFrom-SecureString |
    Set-Content -LiteralPath $credentialFile -Encoding Ascii -NoNewline
$credential = $null

Write-Output "Saved Windows-user-encrypted signing credential to $credentialFile"
