param(
    [string]$Phase = 'all'
)

$ErrorActionPreference = 'Stop'
$serverDir = 'E:\繁花梦\01-服务端\测试服务端'
$java = 'C:\Program Files\Java\jdk-25.0.5.1-hotspot\bin\java.exe'
$log = Join-Path $serverDir 'logs\latest.log'

function Start-TestServer {
    param([string[]]$Commands, [string]$Tag)
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $java
    $psi.WorkingDirectory = $serverDir
    $psi.Arguments = '-Xms1G -Xmx2G -Dfile.encoding=UTF-8 -jar FHM-Core-26.3.jar --nogui'
    $psi.RedirectStandardInput = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $started = Get-Date
    $p = [System.Diagnostics.Process]::Start($psi)
    $ok = $false
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt 120) {
        Start-Sleep -Seconds 2
        if (-not (Test-Path $log)) { continue }
        $info = Get-Item $log
        if ($info.LastWriteTime -lt $started) { continue }
        $tail = Get-Content $log -Tail 40 -ErrorAction SilentlyContinue
        if ($tail -match 'Done \(') { $ok = $true; break }
    }
    Write-Output "[$Tag] server-ready=$ok"
    Start-Sleep -Seconds 2
    foreach ($c in $Commands) {
        $p.StandardInput.WriteLine($c)
        $p.StandardInput.Flush()
        Start-Sleep -Seconds 2
    }
    Start-Sleep -Seconds 3
    $p.StandardInput.WriteLine('stop')
    $p.StandardInput.Flush()
    if (-not $p.WaitForExit(45000)) { $p.Kill(); Write-Output "[$Tag] killed" }
    Write-Output "[$Tag] exit=$($p.ExitCode)"
}

if ($Phase -in @('all', 'first')) {
    Start-TestServer -Tag 'first' -Commands @(
        'furniture place table 2 100 2 0',
        'furniture place chair 4 100 2 0',
        'furniture place cabinet 6 100 2 0',
        'furniture place lamp 8 100 2 0',
        'furniture place bench 10 100 2 90',
        'furniture list'
    )
}

if ($Phase -in @('all', 'second')) {
    Start-TestServer -Tag 'second' -Commands @('furniture list')
}

if ($Phase -eq 'kill') {
    Start-TestServer -Tag 'kill' -Commands @('kill @e[type=minecraft:interaction]')
}

if ($Phase -eq 'third') {
    Start-TestServer -Tag 'third' -Commands @('furniture list')
}

if ($Phase -eq 'info') {
    Start-TestServer -Tag 'info' -Commands @(
        'furniture info 2 100 2',
        'furniture info 4 100 2',
        'furniture info 6 100 2',
        'furniture info 8 100 2',
        'furniture info 10 100 2'
    )
}

if ($Phase -eq 'fresh') {
    Start-TestServer -Tag 'fresh' -Commands @(
        'furniture place table 20 100 2 0',
        'furniture debug'
    )
}

if ($Phase -eq 'debug') {
    Start-TestServer -Tag 'debug' -Commands @('furniture debug')
}

if ($Phase -eq 'cleanup') {
    Start-TestServer -Tag 'cleanup' -Commands @(
        'furniture remove 2 100 2',
        'furniture remove 4 100 2',
        'furniture remove 6 100 2',
        'furniture remove 8 100 2',
        'furniture remove 10 100 2',
        'furniture remove 20 100 2',
        'furniture list'
    )
}

Write-Output '--- 关键日志 ---'
Select-String -Path $log -Pattern '已恢复已放置家具|交互箱|显示实体|家具系统加载完成|已放置' |
    Select-Object -Last 20 | ForEach-Object { Write-Output ($_.Line -replace '^\[[^]]+\] \[[^]]+\]: ', '') }
