# line_in_file_present.nu
# Nushell version of the line-in-file present script.

if not ($FILE | path exists) {
    print --stderr "File not found."
    exit 1
}

mut lines = []
try {
    $lines = (open $FILE | lines)
} catch {
    print --stderr "File not readable."
    exit 1
}

let linecount = ($lines | length)

def write-file [file: string, content: list] {
    let joined = ($content | str join (char nl))
    ($joined + (char nl)) | save --raw $file
}

# :present by linenum
if $LINENUM != "" {
    mut linenum_int = ($LINENUM | into int)
    if $linenum_int < 0 {
        $linenum_int = $linecount + $linenum_int + 1
    }
    if $linenum_int < 1 or $linenum_int > $linecount {
        print --stderr $"No line number ($LINENUM) in file."
        exit 2
    }
    if $lines.($linenum_int - 1) == $LINE {
        exit 0
    } else {
        $lines.($linenum_int - 1) = $LINE
        write-file $FILE $lines
        exit 255
    }
}

# :present by regexp or string-match or line-match
if $REGEX != "" or $STRING_MATCH != "" or $LINE_MATCH != "" {
    # Find matching indices (0-based)
    mut match_indices = []
    for i in 0..<($lines | length) {
        let line = $lines | get $i
        let matched = if $REGEX != "" {
            ($line =~ $REGEX)
        } else if $STRING_MATCH != "" {
            ($line | str contains $STRING_MATCH)
        } else {
            ($line == $LINE_MATCH)
        }
        if $matched {
            $match_indices = ($match_indices | append $i)
        }
    }

    if ($match_indices | is-not-empty) {
        # Apply selector
        let selected = if $SELECTOR == "first" {
            [$match_indices | first]
        } else if $SELECTOR == "last" {
            [$match_indices | last]
        } else {
            $match_indices
        }

        mut exit_code = 0
        # Process in reverse order to keep indices valid
        for idx in ($selected | reverse) {
            if $lines | get $idx != $LINE {
                $lines | update $idx $LINE
                $lines = ($lines | update $idx $LINE)
                $exit_code = 255
            }
        }
        if $exit_code == 255 {
            write-file $FILE $lines
        }
        exit $exit_code
    } else {
        # No direct match — check AFTER / BEFORE / INSERTAT
        if $AFTER != "" {
            mut after_indices = []
            for i in 0..<($lines | length) {
                if ($lines | get $i) =~ $AFTER {
                    $after_indices = ($after_indices | append $i)
                }
            }
            let selected = if $SELECTOR == "first" {
                if ($after_indices | is-not-empty) { [$after_indices | first] } else { [] }
            } else if $SELECTOR == "last" {
                if ($after_indices | is-not-empty) { [$after_indices | last] } else { [] }
            } else {
                $after_indices
            }

            mut exit_code = 0
            for idx in ($selected | reverse) {
                let next_idx = $idx + 1
                let next_line = if $next_idx < ($lines | length) { $lines | get $next_idx } else { "" }
                if $next_line != $LINE {
                    $lines = ($lines | insert $next_idx $LINE)
                    $exit_code = 255
                }
            }
            if $exit_code == 255 {
                write-file $FILE $lines
            }
            exit $exit_code
        } else if $BEFORE != "" {
            mut before_indices = []
            for i in 0..<($lines | length) {
                if ($lines | get $i) =~ $BEFORE {
                    $before_indices = ($before_indices | append $i)
                }
            }
            let selected = if $SELECTOR == "first" {
                if ($before_indices | is-not-empty) { [$before_indices | first] } else { [] }
            } else if $SELECTOR == "last" {
                if ($before_indices | is-not-empty) { [$before_indices | last] } else { [] }
            } else {
                $before_indices
            }

            mut exit_code = 0
            for idx in ($selected | reverse) {
                let prev_line = if $idx > 0 { $lines | get ($idx - 1) } else { "" }
                if $prev_line != $LINE {
                    $lines = ($lines | insert $idx $LINE)
                    $exit_code = 255
                }
            }
            if $exit_code == 255 {
                write-file $FILE $lines
            }
            exit $exit_code
        } else {
            if $INSERTAT == "bof" {
                $lines = ($lines | prepend $LINE)
                write-file $FILE $lines
                exit 255
            } else {
                $lines = ($lines | append $LINE)
                write-file $FILE $lines
                exit 255
            }
        }
    }
}

print --stderr "script error"
exit 1
