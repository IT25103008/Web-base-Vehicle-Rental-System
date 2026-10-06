# ============================================================
#  Axle mail inbox
#
#  The app has no mail server on a laptop, so every email it would send
#  (confirm your email, reset your password, booking updates) is written
#  to its log instead. This window watches that log and shows each email
#  as it arrives, like a tiny inbox.
#
#  Keys:  O = open the newest link in the browser
#         C = copy the newest link to the clipboard
#         L = list every message received
#         X = clear the screen
#         Q = quit
#
#  This file is plain ASCII on purpose: Windows PowerShell 5.1 reads
#  scripts without a byte-order mark as ANSI, so the box-drawing
#  characters are built from their code points at run time instead.
# ============================================================
param(
    [string]$LogFile = (Join-Path $PSScriptRoot 'logs\axle.log'),
    [int]$Port = 8080,     # the app's port, for the ONLINE / OFFLINE light
    [switch]$Once,         # print what is already in the log, then stop (testing)
    [switch]$Animate       # spinner on the status line (needs a console that honours carriage returns)
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
try { $Host.UI.RawUI.WindowTitle = 'Axle Mail Inbox' } catch { }

# ---------- drawing characters ----------
$TL = [string][char]0x256D; $TR = [string][char]0x256E     # rounded corners
$BL = [string][char]0x2570; $BR = [string][char]0x256F
$H  = [string][char]0x2500; $V  = [string][char]0x2502
$LT = [string][char]0x251C; $RT = [string][char]0x2524     # T joints
$DOT = [string][char]0x25CF; $MID = [string][char]0x00B7
$SPIN = @('|', '/', '-', '\')

# ---------- layout ----------
function Get-Width {
    try { $w = $Host.UI.RawUI.WindowSize.Width } catch { $w = 80 }
    if ($w -lt 50) { $w = 50 }
    return [Math]::Min($w - 2, 84)
}
$script:W = Get-Width            # card width including the two borders

$script:mails   = New-Object System.Collections.ArrayList
$script:current = $null
$script:stamp   = (Get-Date -Format 'HH:mm:ss')
$script:idleOn  = $false
$script:online  = $false

# ---------- small helpers ----------
function Out-Seg([string]$text, $fg = 'Gray', $bg = $null) {
    if ($bg) { Write-Host $text -NoNewline -ForegroundColor $fg -BackgroundColor $bg }
    else     { Write-Host $text -NoNewline -ForegroundColor $fg }
}

function Wrap-Text([string]$text, [int]$width) {
    $out = New-Object System.Collections.ArrayList
    if ([string]::IsNullOrWhiteSpace($text)) { [void]$out.Add(''); return ,$out }
    $line = ''
    foreach ($word in ($text -split '\s+')) {
        while ($word.Length -gt $width) {                  # a word longer than the card
            if ($line.Length -gt 0) { [void]$out.Add($line); $line = '' }
            [void]$out.Add($word.Substring(0, $width))
            $word = $word.Substring($width)
        }
        if ($line.Length -eq 0)                              { $line = $word }
        elseif (($line.Length + 1 + $word.Length) -le $width) { $line = $line + ' ' + $word }
        else                                                  { [void]$out.Add($line); $line = $word }
    }
    if ($line.Length -gt 0) { [void]$out.Add($line) }
    return ,$out
}

function Clear-Idle {
    if ($script:idleOn) {
        Write-Host ("`r" + (' ' * ($script:W + 1)) + "`r") -NoNewline
        $script:idleOn = $false
    }
}

# one row of a card: segments are @(@('text','Color'), ...)
function Card-Row($segments, $frame) {
    $inner = $script:W - 4
    $used = 0
    Out-Seg ($V + ' ') $frame
    foreach ($s in $segments) {
        $t = [string]$s[0]
        if ($used + $t.Length -gt $inner) { $t = $t.Substring(0, [Math]::Max(0, $inner - $used)) }
        Out-Seg $t $s[1] $s[2]
        $used += $t.Length
    }
    if ($used -lt $inner) { Out-Seg (' ' * ($inner - $used)) 'Gray' }
    Out-Seg (' ' + $V) $frame
    Write-Host ''
}
function Card-Top($frame)    { Write-Host ($TL + ($H * ($script:W - 2)) + $TR) -ForegroundColor $frame }
function Card-Bottom($frame) { Write-Host ($BL + ($H * ($script:W - 2)) + $BR) -ForegroundColor $frame }
function Card-Divider($frame){ Write-Host ($LT + ($H * ($script:W - 2)) + $RT) -ForegroundColor $frame }

# what kind of mail is it? (label, colour)
function Get-Kind([string]$subject) {
    $s = $subject.ToLower()
    if ($s -match 'confirm.*email|verify')               { return @('CONFIRM EMAIL', 'Green') }
    if ($s -match 'password|reset')                      { return @('PASSWORD', 'Magenta') }
    if ($s -match 'payment|refund|balance|charge')       { return @('PAYMENT', 'Yellow') }
    if ($s -match 'booking|vehicle|trip|return|pickup')  { return @('BOOKING', 'Cyan') }
    if ($s -match 'insurance|damage|maintenance|service'){ return @('FLEET', 'Blue') }
    return @('MAIL', 'Gray')
}

# ---------- banner ----------
function Write-Banner {
    $frame = 'DarkCyan'
    $inner = $script:W - 4
    Write-Host ''
    Card-Top $frame
    Card-Row @(,@('', 'Gray')) $frame
    $title = 'A X L E   M A I L   I N B O X'
    $pad = [Math]::Max(0, [int](($inner - $title.Length) / 2))
    Card-Row @(,@(((' ' * $pad) + $title), 'Cyan')) $frame
    $sub = 'every email the system sends lands here'
    $pad = [Math]::Max(0, [int](($inner - $sub.Length) / 2))
    Card-Row @(,@(((' ' * $pad) + $sub), 'DarkGray')) $frame
    Card-Row @(,@('', 'Gray')) $frame
    Card-Divider $frame
    Card-Row @(@('watching  ', 'DarkGray'), @(((Split-Path -Leaf (Split-Path $LogFile)) + [string][char]92 + (Split-Path -Leaf $LogFile)), 'Gray')) $frame
    Card-Row @(@('keys      ', 'DarkGray'), @('[O]', 'Black', 'Gray'), @(' ', 'Gray'), @('open link', 'Gray'),
               @('   ', 'Gray'), @('[C]', 'Black', 'Gray'), @(' ', 'Gray'), @('copy link', 'Gray'),
               @('   ', 'Gray'), @('[L]', 'Black', 'Gray'), @(' ', 'Gray'), @('list', 'Gray'),
               @('   ', 'Gray'), @('[Q]', 'Black', 'Gray'), @(' ', 'Gray'), @('quit', 'Gray')) $frame
    Card-Bottom $frame
}

# ---------- one mail ----------
function Show-Mail($m, [int]$number, [bool]$fresh) {
    Clear-Idle
    $kind  = Get-Kind $m.Subject
    $frame = if ($fresh) { $kind[1] } else { 'DarkGray' }
    $inner = $script:W - 4

    Write-Host ''
    Card-Top $frame

    # header row: status dot, kind badge, time and number on the right
    $left  = if ($fresh) { "$DOT NEW  " } else { "$DOT      " }
    $badge = ' ' + $kind[0] + ' '
    $right = "{0}  #{1}" -f $m.Time, $number
    $gap   = [Math]::Max(1, $inner - $left.Length - $badge.Length - $right.Length)
    Out-Seg ($V + ' ') $frame
    Out-Seg $left $(if ($fresh) { 'Yellow' } else { 'DarkGray' })
    Out-Seg $badge 'Black' $kind[1]
    Out-Seg (' ' * $gap) 'Gray'
    Out-Seg $right 'DarkGray'
    Out-Seg (' ' + $V) $frame
    Write-Host ''

    Card-Divider $frame
    Card-Row @(@('To       ', 'DarkGray'), @($m.To, 'White')) $frame
    Card-Row @(@('Subject  ', 'DarkGray'), @($m.Subject, 'White')) $frame
    Card-Divider $frame

    # body, word-wrapped; the link is shown separately below
    $blankRun = $false
    foreach ($line in $m.Body) {
        if ($m.Link -and $line.Contains($m.Link)) { continue }
        if ([string]::IsNullOrWhiteSpace($line)) {
            if (-not $blankRun) { Card-Row @(,@('', 'Gray')) $frame }
            $blankRun = $true
            continue
        }
        $blankRun = $false
        foreach ($w in (Wrap-Text $line $inner)) { Card-Row @(,@($w, 'Gray')) $frame }
    }

    if ($m.Link) {
        Card-Divider $frame
        Card-Row @(,@('LINK', 'Green')) $frame
        foreach ($chunk in (Wrap-Text $m.Link $inner)) { Card-Row @(,@($chunk, 'Green')) $frame }
        Card-Row @(,@('', 'Gray')) $frame
        Card-Row @(@(' O ', 'Black', 'Green'), @(' Open in browser', 'Gray'),
                   @('     ', 'Gray'), @(' C ', 'Black', 'Cyan'), @(' Copy link', 'Gray')) $frame
    }
    Card-Bottom $frame
    if ($fresh) { try { [Console]::Beep(880, 140) } catch { } }
}

# ---------- reading the log ----------
# An email in the log looks like:
#   ===== EMAIL (no SMTP server configured - shown here instead) =====
#   To:      someone@example.com
#   Subject: Confirm your email for Axle
#   <blank>
#   <body lines, one of them a link>
#   ==================================================================
function Read-LogLine([string]$line, [bool]$fresh) {
    if ($line -match '^\d{4}-\d{2}-\d{2}T(\d{2}:\d{2}:\d{2})') { $script:stamp = $Matches[1] }
    if ($null -eq $script:current) {
        if ($line -match '^=+ EMAIL') {
            $script:current = [pscustomobject]@{
                Time = $script:stamp; To = ''; Subject = ''
                Body = (New-Object System.Collections.ArrayList); Link = $null
            }
        }
        return
    }
    if ($line -match '^=+\s*$') {                       # closing line: the mail is complete
        $m = $script:current
        $script:current = $null
        while ($m.Body.Count -gt 0 -and [string]::IsNullOrWhiteSpace($m.Body[0])) { $m.Body.RemoveAt(0) }
        while ($m.Body.Count -gt 0 -and [string]::IsNullOrWhiteSpace($m.Body[$m.Body.Count - 1])) { $m.Body.RemoveAt($m.Body.Count - 1) }
        $found = ($m.Body | Where-Object { $_ -match 'https?://\S+' } | Select-Object -Last 1)
        if ($found -and ($found -match '(https?://\S+)')) { $m.Link = $Matches[1] }
        [void]$script:mails.Add($m)
        if ($fresh) { Show-Mail $m $script:mails.Count $true }
        return
    }
    if ($line -match '^To:\s*(.*)$')      { $script:current.To = $Matches[1].Trim(); return }
    if ($line -match '^Subject:\s*(.*)$') { $script:current.Subject = $Matches[1].Trim(); return }
    [void]$script:current.Body.Add($line.TrimEnd())
}

function Newest-Link {
    for ($i = $script:mails.Count - 1; $i -ge 0; $i--) {
        if ($script:mails[$i].Link) { return $script:mails[$i].Link }
    }
    return $null
}

# ---------- the list view (L) ----------
function Show-List {
    Clear-Idle
    $frame = 'DarkCyan'
    Write-Host ''
    Card-Top $frame
    Card-Row @(,@(('INBOX  ' + $MID + '  ' + $script:mails.Count + ' message(s)'), 'Cyan')) $frame
    Card-Divider $frame
    if ($script:mails.Count -eq 0) {
        Card-Row @(,@('nothing yet', 'DarkGray')) $frame
    }
    for ($i = 0; $i -lt $script:mails.Count; $i++) {
        $m = $script:mails[$i]
        $k = Get-Kind $m.Subject
        Card-Row @(@(('#{0,-3}' -f ($i + 1)), 'DarkGray'), @($m.Time + '  ', 'DarkGray'),
                   @(('{0,-14}' -f $k[0]), $k[1]), @($m.To, 'White')) $frame
    }
    Card-Bottom $frame
}

# ---------- the live status line at the bottom ----------
function Test-Online {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $iar = $c.BeginConnect('127.0.0.1', $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(250)
        if ($ok) { try { $c.EndConnect($iar) } catch { $ok = $false } }
        $c.Close()
        return $ok
    } catch { return $false }
}

$script:lastStatus = ''

function Show-Idle([int]$frame) {
    $state = if ($script:online) { 'ONLINE' } else { 'OFFLINE' }
    $scol  = if ($script:online) { 'Green' } else { 'Red' }
    $key   = "$state|$($script:mails.Count)"
    if (-not $Animate) {
        # plain mode: print the status line again only when something changed
        if ($key -eq $script:lastStatus) { return }
        $script:lastStatus = $key
        Out-Seg '  ' 'Gray'
        Out-Seg ($DOT + ' ') $scol
        Out-Seg ("app $state") $scol
        Out-Seg ('   ' + $MID + '   ') 'DarkGray'
        Out-Seg ("{0} received" -f $script:mails.Count) 'White'
        Out-Seg ('   ' + $MID + '   ') 'DarkGray'
        Out-Seg 'listening for new mail...' 'Gray'
        Write-Host ''
        return
    }
    $spin = $SPIN[$frame % $SPIN.Count]
    Write-Host ("`r") -NoNewline
    Out-Seg ('  ' + $spin + ' ') 'Cyan'
    Out-Seg 'listening for new mail' 'Gray'
    Out-Seg ('   ' + $MID + '   ') 'DarkGray'
    Out-Seg ("{0} received" -f $script:mails.Count) 'White'
    Out-Seg ('   ' + $MID + '   ') 'DarkGray'
    Out-Seg ($DOT + ' ') $scol
    Out-Seg ("app $state") $scol
    Out-Seg ('   ' + $MID + '   ') 'DarkGray'
    Out-Seg (Get-Date -Format 'HH:mm:ss') 'DarkGray'
    Out-Seg '      ' 'Gray'
    $script:idleOn = $true
}

# ============================================================
#  start
# ============================================================
Write-Banner

if (-not (Test-Path -LiteralPath $LogFile)) {
    if ($Once) { Write-Host '  (no log file yet)' -ForegroundColor DarkGray; exit 0 }
    Write-Host ''
    Write-Host '  Waiting for the app to start and create its log file...' -ForegroundColor DarkYellow
    Write-Host '  Start it with run-frontend.bat, restart-app.bat or mvn spring-boot:run.' -ForegroundColor DarkGray
    $f = 0
    while (-not (Test-Path -LiteralPath $LogFile)) {
        Write-Host ("`r  " + $SPIN[$f % 4] + ' ') -NoNewline -ForegroundColor Cyan
        $f++; Start-Sleep -Milliseconds 300
    }
    Write-Host ("`r" + (' ' * 6) + "`r") -NoNewline
}

$fs = New-Object System.IO.FileStream($LogFile, [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
$sr = New-Object System.IO.StreamReader($fs, [System.Text.Encoding]::UTF8)

# mail that is already in the log: show the latest three, dimmed
while ($null -ne ($line = $sr.ReadLine())) { Read-LogLine $line $false }
$script:current = $null
$total = $script:mails.Count
if ($total -gt 0) {
    $skip = [Math]::Max(0, $total - 3)
    if ($skip -gt 0) { Write-Host ("`n  ... {0} older message(s) hidden - press L to list all" -f $skip) -ForegroundColor DarkGray }
    for ($i = $skip; $i -lt $total; $i++) { Show-Mail $script:mails[$i] ($i + 1) $false }
}
if ($Once) { $sr.Dispose(); exit 0 }
Write-Host ''

# ---------- watch for new mail ----------
$tick = 0
while ($true) {
    $got = $false
    while ($null -ne ($line = $sr.ReadLine())) { Read-LogLine $line $true; $got = $true }
    if ($got -and $Animate) { Write-Host '' }

    if ($fs.Length -lt $fs.Position) {                 # the log was rotated or cleared
        $fs.Position = 0
        $sr.DiscardBufferedData()
    }

    $keyReady = $false
    try { $keyReady = [Console]::KeyAvailable } catch { }     # no key input when run without a console
    while ($keyReady) {
        $key = [Console]::ReadKey($true)
        switch ($key.Key) {
            'Q' { Clear-Idle; $sr.Dispose(); Write-Host '  Inbox closed.' -ForegroundColor DarkGray; exit 0 }
            'O' {
                Clear-Idle
                $l = Newest-Link
                if ($l) { Write-Host ('  Opening in your browser...') -ForegroundColor Green; Start-Process $l }
                else    { Write-Host '  No link in the mail so far.' -ForegroundColor DarkYellow }
            }
            'C' {
                Clear-Idle
                $l = Newest-Link
                if ($l) { Set-Clipboard -Value $l; Write-Host '  Link copied. Paste it into your browser''s address bar.' -ForegroundColor Green }
                else    { Write-Host '  No link in the mail so far.' -ForegroundColor DarkYellow }
            }
            'L' { Show-List }
            'X' { Clear-Host; Write-Banner; Write-Host '' }
        }
        try { $keyReady = [Console]::KeyAvailable } catch { $keyReady = $false }
    }

    if (($tick % 8) -eq 0) { $script:online = Test-Online }
    Show-Idle $tick
    $tick++
    Start-Sleep -Milliseconds 250
}
