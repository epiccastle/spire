# line_in_file_get.nu
# Nushell version of the line-in-file get script.

if not ($FILE | path exists) {
    print --stderr "File not found."
    exit 1
}

let lines = try {
    open $FILE | lines
} catch {
    print --stderr "File not readable."
    exit 1
    []
}

let linecount = ($lines | length)

# :get by linenum
if $LINENUM != "" {
    mut linenum_int = ($LINENUM | into int)
    if $linenum_int < 0 {
        $linenum_int = $linecount + $linenum_int + 1
    }
    if $linenum_int < 1 or $linenum_int > $linecount {
        print --stderr $"No line number ($LINENUM) in file."
        exit 2
    }
    print $linenum_int
    print ($lines | get ($linenum_int - 1))
    exit 0
}

# :get by regexp or string-match or line-match
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

    # Apply selector
    let selected = if $SELECTOR == "first" {
        if ($match_indices | is-not-empty) { [$match_indices | first] } else { [] }
    } else if $SELECTOR == "last" {
        if ($match_indices | is-not-empty) { [$match_indices | last] } else { [] }
    } else {
        $match_indices
    }

    if ($selected | is-not-empty) {
        # Output line numbers (1-based) and matching lines
        let line_nums = ($selected | each { |idx| $idx + 1 } | str join " ")
        print $line_nums
        for idx in $selected {
            print ($lines | get $idx)
        }
        exit 0
    } else {
        print "no match"
        exit 0
    }
}

print --stderr "script error"
exit 1
