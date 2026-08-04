# line_in_file_absent.nu
# Nushell version of the line-in-file absent script.

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

# :absent by linenum
if $LINENUM != "" {
    mut linenum_int = ($LINENUM | into int)
    if $linenum_int < 0 {
        $linenum_int = $linecount + $linenum_int + 1
    }
    if $linenum_int < 1 or $linenum_int > $linecount {
        print --stderr $"No line number ($LINENUM) in file."
        exit 2
    }
    $lines = ($lines | reject ($linenum_int - 1))
    write-file $FILE $lines
    exit 255
}

# :absent by regexp or string-match or line-match
if $REGEX != "" or $STRING_MATCH != "" or $LINE_MATCH != "" {
    # Find the first matching index (0-based)
    mut match_index = -1
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
            $match_index = $i
            break
        }
    }

    if $match_index >= 0 {
        $lines = ($lines | reject $match_index)
        write-file $FILE $lines
        exit 255
    } else {
        exit 0
    }
}
