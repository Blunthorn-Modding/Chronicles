param(
    [Parameter(Position=0)]
    [ValidateSet('setup','build','deploy','start','smoke','content-smoke','menu-smoke','stage-smoke','ecology-smoke','legacy-smoke','backup','restore','pack','doctor','client','verify','pilot-prepare','pilot-start','pilot-smoke','pilot-promote','pilot-backup')]
    [string]$Action = 'doctor',
    [switch]$AcceptEula,
    [string]$Archive
)
$ErrorActionPreference = 'Stop'
$arguments = @((Join-Path $PSScriptRoot 'workspace.py'), $Action)
if ($AcceptEula) { $arguments += '--accept-eula' }
if ($Archive) { $arguments += @('--archive', $Archive) }
& python @arguments
exit $LASTEXITCODE
