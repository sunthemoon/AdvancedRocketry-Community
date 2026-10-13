param(
    [Parameter(Mandatory=$true)] [ValidateSet('Data','GameTest','Build','Native')] [string]$Target,
    [Parameter(Mandatory=$true)] [string]$Evidence,
    [string]$TestedCommit
)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if ((Get-Location).Path -ne $workspace) { throw 'Run only from the W21C worktree.' }
$lock = 'D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm'
$token = "W21C-$Target-$([guid]::NewGuid())"
$deadline = (Get-Date).AddHours(3)
$lastNotice = [datetime]::MinValue
while (-not (New-Item -ItemType Directory -Path $lock -ErrorAction SilentlyContinue)) {
    if ((Get-Date) -ge $deadline -or ((Get-Date) - (Get-Item -LiteralPath $lock).CreationTime).TotalHours -gt 3) {
        throw 'Heavy JVM lock timed out or is older than three hours. It has not been removed.'
    }
    if (((Get-Date) - $lastNotice).TotalSeconds -ge 30) {
        Write-Output "Waiting for heavy JVM lock: $(Get-Content -LiteralPath "$lock\owner.txt" -ErrorAction SilentlyContinue)"
        $lastNotice = Get-Date
    }
    Start-Sleep -Seconds 5
}
try {
    Set-Content -LiteralPath "$lock\owner.txt" -Value $token
    $disks = Get-PSDrive C,D
    $disks | Select-Object Name,Free | ConvertTo-Json | Set-Content (Join-Path $Evidence "$Target-disk.json")
    if ($disks | Where-Object { $_.Free -lt 10GB }) { throw 'C: and D: must each have 10 GiB free.' }
    $log = Join-Path $Evidence "$Target-console.log"
    switch ($Target) {
        'Data' { & .\gradlew.bat --offline runData --console=plain *> $log }
        'GameTest' { & .\gradlew.bat --offline runGameTestServer --console=plain *> $log }
        'Build' { & .\gradlew.bat --offline clean build --console=plain *> $log }
        'Native' {
            # The harness records HEAD, refuses an unclean tree and builds the jar it tests (W21c review L4).
            $native = @('-B', 'scripts/run_w21c_research_restart.py',
                '--template', 'D:/GitHub/ARCE-Task-Evidence/v1.8.0/guard-forced-setup-05/native-05/server',
                '--candidate-jar', 'build/libs/advancedrocketry-community-1.20.1-1.8.0-dev.jar',
                '--work-dir', 'build/w21c-native', '--evidence-dir', (Join-Path $Evidence 'native'), '--build-candidate')
            if ($TestedCommit) { $native += @('--tested-commit', $TestedCommit) }
            & python @native *> $log
        }
    }
    $code = $LASTEXITCODE
    Get-Content -LiteralPath $log -Tail 20
    if ($code -ne 0) { throw "$Target failed with exit code $code. See $log" }
} finally {
    if ([IO.Path]::GetFullPath($lock) -ne 'D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm') { throw 'Unexpected lock cleanup target.' }
    if ((Test-Path -LiteralPath "$lock\owner.txt") -and (Get-Content -LiteralPath "$lock\owner.txt" -Raw).Trim() -eq $token) {
        Remove-Item -LiteralPath 'D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm' -Recurse -Force
    }
}
