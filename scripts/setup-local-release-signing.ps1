[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
Add-Type -AssemblyName System.Windows.Forms
$credentialFile = Join-Path $env:LOCALAPPDATA 'HermesControl\release-signing-password.dpapi'
$credentialDirectory = Split-Path -Parent $credentialFile
$entropy = [Text.Encoding]::UTF8.GetBytes('HermesControl-release-signing-v1')

function Read-Password([string]$label) {
    $form = New-Object System.Windows.Forms.Form
    $form.Text = 'Hermes Mobile release signing'
    $form.Width = 420
    $form.Height = 170
    $form.StartPosition = 'CenterScreen'
    $form.FormBorderStyle = 'FixedDialog'
    $form.MaximizeBox = $false
    $form.MinimizeBox = $false

    $prompt = New-Object System.Windows.Forms.Label
    $prompt.Text = $label
    $prompt.AutoSize = $true
    $prompt.Left = 20
    $prompt.Top = 20
    $form.Controls.Add($prompt)

    $textBox = New-Object System.Windows.Forms.TextBox
    $textBox.Left = 20
    $textBox.Top = 50
    $textBox.Width = 360
    $textBox.UseSystemPasswordChar = $true
    $form.Controls.Add($textBox)

    $ok = New-Object System.Windows.Forms.Button
    $ok.Text = 'OK'
    $ok.Left = 220
    $ok.Top = 85
    $ok.Width = 75
    $ok.DialogResult = [System.Windows.Forms.DialogResult]::OK
    $form.AcceptButton = $ok
    $form.Controls.Add($ok)

    $cancel = New-Object System.Windows.Forms.Button
    $cancel.Text = 'Cancel'
    $cancel.Left = 305
    $cancel.Top = 85
    $cancel.Width = 75
    $cancel.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
    $form.CancelButton = $cancel
    $form.Controls.Add($cancel)

    if ($form.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) { return $null }
    return $textBox.Text
}

$password = Read-Password 'Enter the release keystore password (at least 6 characters)'
if ($null -eq $password -or $password.Length -lt 6) {
    throw 'No valid password was supplied.'
}
$confirmation = Read-Password 'Enter the same password again'
if ($password -cne $confirmation) {
    throw 'Passwords do not match.'
}

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
    $confirmation = $null
}

Write-Output "Saved Windows-user-encrypted signing credential to $credentialFile"
