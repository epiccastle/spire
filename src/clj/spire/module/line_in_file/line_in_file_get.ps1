# line_in_file_get.ps1
# PowerShell version of the line-in-file get script.

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $FILE -PathType Leaf)) {
    [Console]::Error.Write('File not found.')
    exit 1
}

$lines = @(Get-Content -LiteralPath $FILE)
$lineCount = $lines.Count

# :get by linenum
if ($LINENUM) {
    $linenumInt = [int]$LINENUM
    if ($linenumInt -lt 0) {
        $linenumInt = $lineCount + $linenumInt + 1
    }
    if ($linenumInt -lt 1 -or $linenumInt -gt $lineCount) {
        [Console]::Error.Write("No line number $LINENUM in file.")
        exit 2
    }
    Write-Output $linenumInt
    Write-Output $lines[$linenumInt - 1]
    exit 0
}

# :get by regexp or string-match or line-match
if ($REGEX -or $STRING_MATCH -or $LINE_MATCH) {
    $matchIndices = @()
    for ($i = 0; $i -lt $lineCount; $i++) {
        $matched = $false
        if ($REGEX) {
            if ($lines[$i] -match $REGEX) { $matched = $true }
        } elseif ($STRING_MATCH) {
            if ($lines[$i].Contains($STRING_MATCH)) { $matched = $true }
        } else {
            if ($lines[$i] -eq $LINE_MATCH) { $matched = $true }
        }
        if ($matched) { $matchIndices += $i }
    }

    # Apply selector
    switch ($SELECTOR) {
        'First' { $matchIndices = @($matchIndices[0]) }
        'Last'  { $matchIndices = @($matchIndices[-1]) }
        'All'   { }
    }

    if ($matchIndices.Count -gt 0) {
        # Output line numbers (1-based) and matching lines
        $lineNums = @()
        $lineContents = @()
        foreach ($idx in $matchIndices) {
            $lineNums += ($idx + 1)
            $lineContents += $lines[$idx]
        }
        Write-Output ($lineNums -join ' ')
        $lineContents | ForEach-Object { Write-Output $_ }
        exit 0
    } else {
        Write-Output 'no match'
        exit 0
    }
}

[Console]::Error.Write('script error')
exit 1
