[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$VersionName,

    [Parameter(Mandatory)]
    [int]$VersionCode,

    [switch]$CopyToNutstore
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$repo = Split-Path -Parent $PSScriptRoot
$keystore = Join-Path $env:USERPROFILE 'hermes-mobile-release.jks'
$credentialFile = Join-Path $env:LOCALAPPDATA 'HermesControl\release-signing-password.dpapi'
$entropy = [Text.Encoding]::UTF8.GetBytes('HermesControl-release-signing-v1')
$apk = Join-Path $repo 'app\build\outputs\apk\release\app-release.apk'

if (-not (Test-Path -LiteralPath $keystore)) {
    throw "Keystore was not found: $keystore"
}
if (-not (Test-Path -LiteralPath $credentialFile)) {
    throw "Encrypted signing credential was not found. Run scripts\setup-local-release-signing.ps1 first."
}

$protectedBytes = [Convert]::FromBase64String((Get-Content -LiteralPath $credentialFile -Raw).Trim())
$passwordBytes = [Security.Cryptography.ProtectedData]::Unprotect(
    $protectedBytes,
    $entropy,
    [Security.Cryptography.DataProtectionScope]::CurrentUser
)

try {
    $password = [Text.Encoding]::UTF8.GetString($passwordBytes)
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'C:\Program Files\Git\bin\bash.exe'
    $psi.Arguments = "-lc `"./gradlew assembleRelease -PversionName=$VersionName -PversionCode=$VersionCode`""
    $psi.WorkingDirectory = $repo
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.EnvironmentVariables['KEYSTORE_PATH'] = $keystore
    $psi.EnvironmentVariables['KEYSTORE_PASSWORD'] = $password
    $psi.EnvironmentVariables['KEY_ALIAS'] = 'hermes-mobile'
    $psi.EnvironmentVariables['KEY_PASSWORD'] = $password

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi
    [void]$process.Start()
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $apk)) {
        throw "Release build failed with exit code $($process.ExitCode).`n$stdout`n$stderr"
    }

    if ($CopyToNutstore) {
        $destinationDirectory = Join-Path $env:USERPROFILE 'Nutstore\1\FileTransfer'
        New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null
        $destination = Join-Path $destinationDirectory "HermesControl-v$VersionName-release.apk"
        Copy-Item -LiteralPath $apk -Destination $destination -Force
        Write-Output "Release APK copied to $destination"
    } else {
        Write-Output "Release APK built at $apk"
    }
} finally {
    [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
    [Array]::Clear($protectedBytes, 0, $protectedBytes.Length)
    $password = $null
}
