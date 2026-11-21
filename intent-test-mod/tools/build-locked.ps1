param([string]$EvidenceDirectory = 'D:\GitHub\ARCE-Task-Evidence\final-campaign\c8fa16e16\fixtures\K18')
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$lock = 'D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm'
$token = "W432 pid=$PID $([guid]::NewGuid())"
New-Item -ItemType Directory -Path $EvidenceDirectory -Force | Out-Null
$log = Join-Path $EvidenceDirectory 'build.log'
$result = Join-Path $EvidenceDirectory 'build-result.txt'
$code = 1
try {
    foreach ($drive in @('C', 'D')) {
        if ((Get-PSDrive $drive).Free -lt 10GB) { throw "$drive has less than 10 GB free" }
    }
    $deadline = (Get-Date).AddHours(3)
    while ($true) {
        $ErrorActionPreference = 'Continue'
        cmd /c mkdir "$lock" 2>$null
        $mkdirCode = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($mkdirCode -eq 0) { break }
        if (Test-Path -LiteralPath $lock) {
            if (((Get-Date) - (Get-Item -LiteralPath $lock).LastWriteTime).TotalHours -gt 3) {
                throw 'Heavy-JVM lock is older than three hours; leave it for Root'
            }
        }
        if ((Get-Date) -gt $deadline) { throw 'Timed out waiting for the heavy-JVM lock' }
        Start-Sleep -Seconds 60
    }
    try {
        Set-Content -LiteralPath "$lock\owner.txt" "$token $(Get-Date -Format o)"
        Set-Location -LiteralPath $workspace
        "LOCK_ACQUIRED $(Get-Date -Format o) $token" | Out-File $log -Append
        & .\gradlew.bat -p intent-test-mod --offline clean build --console=plain *>> $log
        $code = $LASTEXITCODE
    } finally {
        $owner = Get-Content -LiteralPath "$lock\owner.txt" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($owner -and $owner.StartsWith($token)) {
            $resolvedLock = (Resolve-Path -LiteralPath $lock).Path
            if ($resolvedLock -ne 'D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm') { throw 'Unexpected lock location' }
            Remove-Item -LiteralPath $resolvedLock -Recurse -Force
            "LOCK_RELEASED $(Get-Date -Format o) $token" | Out-File $log -Append
        }
    }
} catch {
    $_ | Out-String | Out-File $log -Append
    $code = 1
}
Set-Content -LiteralPath $result "exit_code=$code completed=$(Get-Date -Format o)"
exit $code
