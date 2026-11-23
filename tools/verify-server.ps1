param([int]$WaitSeconds = 150)

$ErrorActionPreference = 'Stop'
$serverDir = 'G:\服务端'
$java = 'D:\mo-jdk\jdk-25.0.5+8\bin\java.exe'
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $java
$psi.WorkingDirectory = $serverDir
$psi.Arguments = '-Xms1G -Xmx2G -Dfile.encoding=UTF-8 -jar FHM-Core-26.3.jar --nogui'
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true

$global:seenDone = $false
$p = [System.Diagnostics.Process]::Start($psi)
$p.add_OutputDataReceived({ param($s, $e) if ($e.Data -and $e.Data -match 'Done \(') { $global:seenDone = $true } })
$p.add_ErrorDataReceived({ param($s, $e) })
$p.BeginOutputReadLine()
$p.BeginErrorReadLine()

$deadline = (Get-Date).AddSeconds($WaitSeconds)
while (-not $global:seenDone -and (Get-Date) -lt $deadline -and -not $p.HasExited) {
    Start-Sleep -Seconds 2
}
Write-Output "server-done=$($global:seenDone) exited=$($p.HasExited)"

Start-Sleep -Seconds 3
$logPath = Join-Path $serverDir 'logs\latest.log'
$packLines = Select-String -Path $logPath -Pattern '资源包已生成|Furniture|资源包' | Select-Object -Last 8
$packLines | ForEach-Object { Write-Output ("LOG: " + $_.Line) }

$sha = $null
try {
    $r = Invoke-WebRequest -Uri 'http://127.0.0.1:8123/pack.zip' -TimeoutSec 30 -UseBasicParsing
    $bytes = $r.Content
    if ($bytes -is [byte[]]) {
        $tmp = Join-Path $env:TEMP 'pack-verify.zip'
        [IO.File]::WriteAllBytes($tmp, $bytes)
        $sha = (Get-FileHash $tmp -Algorithm SHA1).Hash.ToLower()
        Write-Output "http=$($r.StatusCode) size=$($bytes.Length) sha1=$sha"
    }
} catch {
    Write-Output "http-fail: $($_.Exception.Message)"
}
if ($sha) {
    $local = (Get-FileHash 'G:\插件\家具\build\FurniturePack-0.4.1.zip' -Algorithm SHA1).Hash.ToLower()
    Write-Output "local-sha1=$local match=$($sha -eq $local)"
}

$p.StandardInput.WriteLine('stop')
$p.StandardInput.Flush()
if (-not $p.WaitForExit(45000)) {
    Write-Output 'stop-timeout, killing'
    $p.Kill()
}
Write-Output "exit-code=$($p.ExitCode)"
