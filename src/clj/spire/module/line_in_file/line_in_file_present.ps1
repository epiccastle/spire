# line_in_file_present.ps1
# PowerShell version of the line-in-file present script.

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $FILE -PathType Leaf)) {
    [Console]::Error.Write('File not found.')
    exit 1
}

$lines = @(Get-Content -LiteralPath $FILE)
$lineCount = $lines.Count

# Helper to write the array back to the file
function Write-File {
    param([string[]]$content)
    [System.IO.File]::WriteAllLines($FILE, $content)
}

# :present by linenum
if ($LINENUM) {
    $linenumInt = [int]$LINENUM
    if ($linenumInt -lt 0) {
        $linenumInt = $lineCount + $linenumInt + 1
    }
    if ($linenumInt -lt 1 -or $linenumInt -gt $lineCount) {
        [Console]::Error.Write("No line number $LINENUM in file.")
        exit 2
    }

    if ($lines[$linenumInt - 1] -eq $LINE) {
        exit 0
    } else {
        $lines[$linenumInt - 1] = $LINE
        Write-File $lines
        exit 255
    }
}

# :present by regexp or string-match or line-match
if ($REGEX -or $STRING_MATCH -or $LINE_MATCH) {
    # Find matching indices (0-based)
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

    if ($matchIndices.Count -gt 0) {
        # Apply selector
        switch ($SELECTOR) {
            'First' { $matchIndices = @($matchIndices[0]) }
            'Last'  { $matchIndices = @($matchIndices[-1]) }
            'All'   { }
        }

        $exit = 0
        # Process in reverse order to keep indices valid
        for ($j = $matchIndices.Count - 1; $j -ge 0; $j--) {
            $idx = $matchIndices[$j]
            if ($lines[$idx] -ne $LINE) {
                $lines[$idx] = $LINE
                $exit = 255
            }
        }
        if ($exit -eq 255) { Write-File $lines }
        exit $exit
    } else {
        # No direct match — check AFTER / BEFORE / INSERTAT
        if ($AFTER) {
            $afterIndices = @()
            for ($i = 0; $i -lt $lineCount; $i++) {
                if ($lines[$i] -match $AFTER) { $afterIndices += $i }
            }
            switch ($SELECTOR) {
                'First' { $afterIndices = @($afterIndices[0]) }
                'Last'  { $afterIndices = @($afterIndices[-1]) }
                'All'   { }
            }

            $exit = 0
            for ($j = $afterIndices.Count - 1; $j -ge 0; $j--) {
                $idx = $afterIndices[$j]
                $nextIdx = $idx + 1
                $nextLine = if ($nextIdx -lt $lineCount) { $lines[$nextIdx] } else { '' }
                if ($nextLine -ne $LINE) {
                    if ($nextIdx -lt $lineCount) {
                        $lines = $lines[0..$idx] + @($LINE) + $lines[$nextIdx..($lineCount - 1)]
                    } else {
                        $lines = $lines[0..$idx] + @($LINE)
                    }
                    $lineCount = $lines.Count
                    $exit = 255
                }
            }
            if ($exit -eq 255) { Write-File $lines }
            exit $exit
        } elseif ($BEFORE) {
            $beforeIndices = @()
            for ($i = 0; $i -lt $lineCount; $i++) {
                if ($lines[$i] -match $BEFORE) { $beforeIndices += $i }
            }
            switch ($SELECTOR) {
                'First' { $beforeIndices = @($beforeIndices[0]) }
                'Last'  { $beforeIndices = @($beforeIndices[-1]) }
                'All'   { }
            }

            $exit = 0
            for ($j = $beforeIndices.Count - 1; $j -ge 0; $j--) {
                $idx = $beforeIndices[$j]
                $prevIdx = $idx - 1
                $prevLine = if ($prevIdx -ge 0) { $lines[$prevIdx] } else { '' }
                if ($prevLine -ne $LINE) {
                    if ($prevIdx -ge 0) {
                        $lines = $lines[0..$prevIdx] + @($LINE) + $lines[$idx..($lineCount - 1)]
                    } else {
                        $lines = @($LINE) + $lines
                    }
                    $lineCount = $lines.Count
                    $exit = 255
                }
            }
            if ($exit -eq 255) { Write-File $lines }
            exit $exit
        } else {
            if ($INSERTAT -eq 'bof') {
                $lines = @($LINE) + $lines
                Write-File $lines
                exit 255
            } else {
                $lines = $lines + @($LINE)
                Write-File $lines
                exit 255
            }
        }
    }
}

[Console]::Error.Write('script error')
exit 1
