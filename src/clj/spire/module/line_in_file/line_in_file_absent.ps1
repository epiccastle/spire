# line_in_file_absent.ps1
# PowerShell version of the line-in-file absent script.

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $FILE -PathType Leaf)) {
    [Console]::Error.Write('File not found.')
    exit 1
}

$lines = @(Get-Content -LiteralPath $FILE)
$lineCount = $lines.Count

function Write-File {
    param([string[]]$content)
    [System.IO.File]::WriteAllLines($FILE, $content)
}

function Remove-ByIndex {
    param([int]$index)
    $result = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($i -ne $index) { $result += $lines[$i] }
    }
    return $result
}

# :absent by linenum
if ($LINENUM) {
    $linenumInt = [int]$LINENUM
    if ($linenumInt -lt 0) {
        $linenumInt = $lineCount + $linenumInt + 1
    }
    if ($linenumInt -lt 1 -or $linenumInt -gt $lineCount) {
        [Console]::Error.Write("No line number $LINENUM in file.")
        exit 2
    }

    $lines = Remove-ByIndex ($linenumInt - 1)
    Write-File $lines
    exit 255
}

# :absent by regexp or string-match or line-match
if ($REGEX -or $STRING_MATCH -or $LINE_MATCH) {
    # Find the first matching index (0-based)
    $matchIndex = -1
    for ($i = 0; $i -lt $lineCount; $i++) {
        $matched = $false
        if ($REGEX) {
            if ($lines[$i] -match $REGEX) { $matched = $true }
        } elseif ($STRING_MATCH) {
            if ($lines[$i].Contains($STRING_MATCH)) { $matched = $true }
        } else {
            if ($lines[$i] -eq $LINE_MATCH) { $matched = $true }
        }
        if ($matched) { $matchIndex = $i; break }
    }

    if ($matchIndex -ge 0) {
        $lines = Remove-ByIndex $matchIndex
        Write-File $lines
        exit 255
    } else {
        exit 0
    }
}
