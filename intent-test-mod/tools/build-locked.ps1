param([string]$EvidenceDirectory = 'D:\GitHub\ARCE-Task-Evidence\final-campaign\c8fa16e16\fixtures\K18')
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$lock = 'D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm'
$token = "W432 pid=$PID $([guid]::NewGuid())"
if (Test-Path -LiteralPath (Join-Path $EvidenceDirectory 'SHA256SUMS.txt')) {
    throw 'Evidence directory is sealed; select a fresh EvidenceDirectory'
}
New-Item -ItemType Directory -Path $EvidenceDirectory -Force | Out-Null
$log = Join-Path $EvidenceDirectory 'build.log'
$result = Join-Path $EvidenceDirectory 'build-result.txt'
$code = 1
try {
    foreach ($drive in @('C', 'D')) {
        if ((Get-PSDrive $drive).Free -lt 10GB) { throw "$drive has less than 10 GB free" }
    }
    $deadline = (Get-Date).AddHours(3)
    # A fresh launcher also yields before trying, so relaunches cannot reacquire immediately.
    Start-Sleep -Seconds 90
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
    $mayRelease = $true
    try {
        Set-Content -LiteralPath "$lock\owner.txt" "$token $(Get-Date -Format o)"
        Set-Location -LiteralPath $workspace
        "LOCK_ACQUIRED $(Get-Date -Format o) $token" | Out-File $log -Append
        # Native stderr must not become a terminating PowerShell NativeCommandError.
        # Keep the wrapper process until its actual exit, including compiler warnings.
        $process = Start-Process -FilePath $env:ComSpec -WindowStyle Hidden -PassThru `
            -WorkingDirectory $workspace -ArgumentList '/d', '/c', 'gradlew.bat -p intent-test-mod --offline clean build --console=plain' `
            -RedirectStandardOutput (Join-Path $EvidenceDirectory 'build-stdout.log') `
            -RedirectStandardError (Join-Path $EvidenceDirectory 'build-stderr.log')
        if (-not $process.WaitForExit(1800000)) {
            $all = @(Get-CimInstance Win32_Process)
            $owned = @($process.Id)
            do {
                $new = @($all | Where-Object { $_.ParentProcessId -in $owned -and $_.ProcessId -notin $owned })
                $owned += @($new | ForEach-Object { $_.ProcessId })
            } while ($new.Count -gt 0)
            $java = @($all | Where-Object { $_.ProcessId -in $owned -and $_.Name -in @('java.exe', 'javaw.exe') })
            & taskkill.exe /PID $process.Id /T /F | Out-File $log -Append
            Start-Sleep -Seconds 2
            foreach ($child in $java) {
                $live = Get-CimInstance Win32_Process -Filter "ProcessId = $($child.ProcessId)"
                if ($live -and $live.CreationDate -eq $child.CreationDate) {
                    $mayRelease = $false
                    throw "Owned JVM $($child.ProcessId) survived timeout; retain lock for Root"
                }
            }
            throw 'Fixture Gradle build exceeded the 30-minute launcher limit; process tree stopped'
        }
        $process.Refresh()
        $code = $process.ExitCode
    } finally {
        $owner = Get-Content -LiteralPath "$lock\owner.txt" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($mayRelease -and $owner -and $owner.StartsWith($token)) {
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
