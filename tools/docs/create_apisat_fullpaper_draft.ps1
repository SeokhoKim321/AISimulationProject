param(
    [string]$SourcePath = "2026 APISAT\APISAT-2026_FullPaper_Draft_260828.md",
    [string]$TemplatePath = "2026 APISAT\FullPaperTemplate_APISAT-2026.docx",
    [string]$OutputPath = "2026 APISAT\APISAT-2026_FullPaper_Draft_260828.docx",
    [string]$PdfPath = "2026 APISAT\APISAT-2026_FullPaper_Draft_260828.pdf"
)

$ErrorActionPreference = "Stop"

function Resolve-ProjectPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location).Path $Path))
}

$sourceFull = Resolve-ProjectPath $SourcePath
$templateFull = Resolve-ProjectPath $TemplatePath
$outputFull = Resolve-ProjectPath $OutputPath
$pdfFull = Resolve-ProjectPath $PdfPath

foreach ($required in @($sourceFull, $templateFull)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required file not found: $required"
    }
}

$sourceDir = Split-Path -Parent $sourceFull
$lines = Get-Content -LiteralPath $sourceFull -Encoding UTF8
$meta = @{}
foreach ($line in $lines) {
    if ($line -match '^(TITLE|AUTHORS|AFFILIATION|ABSTRACT|KEYWORDS):\s*(.*)$') {
        $meta[$matches[1]] = $matches[2]
    }
}
foreach ($key in @("TITLE", "AUTHORS", "AFFILIATION", "ABSTRACT", "KEYWORDS")) {
    if (-not $meta.ContainsKey($key)) {
        throw "Missing manuscript metadata: $key"
    }
}

$word = $null
$doc = $null
$selection = $null

try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0

    $doc = $word.Documents.Open($templateFull, $false, $false)
    if (Test-Path -LiteralPath $outputFull) {
        [System.IO.File]::Delete($outputFull)
    }
    if (Test-Path -LiteralPath $pdfFull) {
        [System.IO.File]::Delete($pdfFull)
    }
    $doc.SaveAs2($outputFull, 16)
    $doc.Content.Delete()
    $selection = $word.Selection
    $selection.SetRange(0, 0)

    function Set-SelectionFormat {
        param(
            [double]$FontSize = 10,
            [bool]$Bold = $false,
            [bool]$Italic = $false,
            [int]$Alignment = 3,
            [double]$LeftIndent = 0,
            [double]$RightIndent = 0,
            [double]$FirstLineIndent = 10,
            [double]$SpaceBefore = 0,
            [double]$SpaceAfter = 0,
            [double]$LineSpacing = 12,
            [bool]$KeepWithNext = $false
        )
        $selection.Font.Name = "Times New Roman"
        $selection.Font.Size = $FontSize
        $selection.Font.Bold = $(if ($Bold) { -1 } else { 0 })
        $selection.Font.Italic = $(if ($Italic) { -1 } else { 0 })
        $selection.Font.Color = 0
        $selection.Range.HighlightColorIndex = 0
        $selection.ParagraphFormat.Alignment = $Alignment
        $selection.ParagraphFormat.LeftIndent = $LeftIndent
        $selection.ParagraphFormat.RightIndent = $RightIndent
        $selection.ParagraphFormat.FirstLineIndent = $FirstLineIndent
        $selection.ParagraphFormat.SpaceBefore = $SpaceBefore
        $selection.ParagraphFormat.SpaceAfter = $SpaceAfter
        $selection.ParagraphFormat.LineSpacingRule = 4
        $selection.ParagraphFormat.LineSpacing = $LineSpacing
        $selection.ParagraphFormat.KeepWithNext = $(if ($KeepWithNext) { -1 } else { 0 })
        $selection.ParagraphFormat.KeepTogether = 0
        $selection.ParagraphFormat.WidowControl = -1
    }

    function Add-Paragraph {
        param(
            [string]$Text,
            [double]$FontSize = 10,
            [bool]$Bold = $false,
            [bool]$Italic = $false,
            [int]$Alignment = 3,
            [double]$LeftIndent = 0,
            [double]$RightIndent = 0,
            [double]$FirstLineIndent = 10,
            [double]$SpaceBefore = 0,
            [double]$SpaceAfter = 0,
            [double]$LineSpacing = 12,
            [bool]$KeepWithNext = $false
        )
        Set-SelectionFormat -FontSize $FontSize -Bold $Bold -Italic $Italic `
            -Alignment $Alignment -LeftIndent $LeftIndent -RightIndent $RightIndent `
            -FirstLineIndent $FirstLineIndent -SpaceBefore $SpaceBefore `
            -SpaceAfter $SpaceAfter -LineSpacing $LineSpacing -KeepWithNext $KeepWithNext
        if ($Text) {
            $selection.TypeText($Text)
        }
        $selection.TypeParagraph()
    }

    function Configure-Section {
        param(
            [object]$Section,
            [int]$ColumnCount
        )
        $Section.PageSetup.PageWidth = $word.CentimetersToPoints(21.0)
        $Section.PageSetup.PageHeight = $word.CentimetersToPoints(29.7)
        $Section.PageSetup.TopMargin = $word.CentimetersToPoints(2.5)
        $Section.PageSetup.BottomMargin = $word.CentimetersToPoints(2.5)
        $Section.PageSetup.LeftMargin = $word.CentimetersToPoints(1.7)
        $Section.PageSetup.RightMargin = $word.CentimetersToPoints(1.7)
        $Section.PageSetup.DifferentFirstPageHeaderFooter = 0
        $Section.PageSetup.OddAndEvenPagesHeaderFooter = 0
        $Section.PageSetup.TextColumns.SetCount($ColumnCount)
        if ($ColumnCount -eq 2) {
            $Section.PageSetup.TextColumns.Spacing = $word.CentimetersToPoints(0.74)
        }

        foreach ($headerType in @(1, 2, 3)) {
            $header = $Section.Headers.Item($headerType)
            $header.LinkToPrevious = $false
            $header.Range.Text = "The 17th Asia-Pacific International Symposium on Aerospace Technology"
            $header.Range.Font.Name = "Times New Roman"
            $header.Range.Font.Size = 8
            $header.Range.ParagraphFormat.Alignment = 1

            $footer = $Section.Footers.Item($headerType)
            $footer.LinkToPrevious = $false
            $footer.Range.Text = ""
            $footer.Range.ParagraphFormat.Alignment = 1
            $fieldRange = $footer.Range.Duplicate
            $fieldRange.Collapse(1)
            [void]$footer.Range.Fields.Add($fieldRange, 33)
            $footer.Range.Font.Name = "Times New Roman"
            $footer.Range.Font.Size = 8
        }
    }

    function Add-MainHeading {
        param([string]$Text)
        $center = $Text -eq "References"
        Add-Paragraph -Text $Text -FontSize 10 -Bold $true -Alignment $(if ($center) { 1 } else { 0 }) `
            -FirstLineIndent 0 -SpaceBefore 10 -SpaceAfter 6 -LineSpacing 12 -KeepWithNext $true
    }

    function Add-SubHeading {
        param([string]$Text)
        Add-Paragraph -Text $Text -FontSize 10 -Bold $true -Alignment 0 -FirstLineIndent 0 `
            -SpaceBefore 3 -SpaceAfter 0 -LineSpacing 12 -KeepWithNext $true
    }

    function Add-Caption {
        param([string]$Text)
        Add-Paragraph -Text $Text -FontSize 8 -Alignment 1 -FirstLineIndent 0 `
            -SpaceBefore 1 -SpaceAfter 5 -LineSpacing 10
    }

    function Add-Figure {
        param(
            [string]$RelativePath,
            [string]$Caption,
            [double]$WidthInches
        )
        $figurePath = [System.IO.Path]::GetFullPath((Join-Path $sourceDir $RelativePath))
        if (-not (Test-Path -LiteralPath $figurePath)) {
            throw "Figure not found: $figurePath"
        }
        Set-SelectionFormat -FontSize 8 -Alignment 1 -FirstLineIndent 0 -LineSpacing 10
        # Inline graphics must not inherit the exact-height body line spacing,
        # otherwise Word clips the picture to a single text line in PDF export.
        $selection.ParagraphFormat.LineSpacingRule = 0
        $inline = $doc.InlineShapes.AddPicture($figurePath, $false, $true, $selection.Range)
        $inline.LockAspectRatio = -1
        $inline.Width = $word.InchesToPoints($WidthInches)
        $inline.Range.ParagraphFormat.Alignment = 1
        $inline.Range.ParagraphFormat.LineSpacingRule = 0
        $selection.SetRange($inline.Range.End, $inline.Range.End)
        $selection.TypeParagraph()
        Add-Caption -Text $Caption
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($inline)
    }

    function Add-Equation {
        param(
            [string]$EquationText,
            [string]$Number
        )
        $table = $doc.Tables.Add($selection.Range, 1, 2)
        $table.AllowAutoFit = $false
        $table.Columns.Item(1).Width = $word.CentimetersToPoints(6.2)
        $table.Columns.Item(2).Width = $word.CentimetersToPoints(1.3)
        $table.Cell(1, 1).Range.Text = $EquationText
        $table.Cell(1, 2).Range.Text = $Number
        $table.Range.Font.Name = "Times New Roman"
        $table.Range.Font.Size = 10
        $table.Cell(1, 1).Range.ParagraphFormat.Alignment = 1
        $table.Cell(1, 2).Range.ParagraphFormat.Alignment = 2
        $table.Range.ParagraphFormat.SpaceBefore = 1
        $table.Range.ParagraphFormat.SpaceAfter = 1
        $table.Borders.Enable = 0
        $selection.SetRange($table.Range.End, $table.Range.End)
        $selection.TypeParagraph()
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($table)
    }

    function Add-DataTable {
        param(
            [string]$Caption,
            [object[]]$Rows,
            [bool]$Wide
        )
        Add-Caption -Text $Caption
        $columnCount = $Rows[0].Count
        $table = $doc.Tables.Add($selection.Range, $Rows.Count, $columnCount)
        for ($rowIndex = 0; $rowIndex -lt $Rows.Count; $rowIndex++) {
            for ($columnIndex = 0; $columnIndex -lt $columnCount; $columnIndex++) {
                $table.Cell($rowIndex + 1, $columnIndex + 1).Range.Text = $Rows[$rowIndex][$columnIndex]
            }
        }
        $table.Range.Font.Name = "Times New Roman"
        $table.Range.Font.Size = 8
        $table.Range.ParagraphFormat.Alignment = 0
        $table.Range.ParagraphFormat.SpaceBefore = 0
        $table.Range.ParagraphFormat.SpaceAfter = 0
        $table.Range.ParagraphFormat.LineSpacingRule = 4
        $table.Range.ParagraphFormat.LineSpacing = 9.5
        $table.Rows.Item(1).Range.Font.Bold = -1
        $table.Rows.Item(1).Shading.BackgroundPatternColor = 15132390
        $table.AllowAutoFit = $true
        $table.AutoFitBehavior(2)
        $table.Borders.Enable = 1
        $table.Borders.Item(-2).LineStyle = 0
        $table.Borders.Item(-4).LineStyle = 0
        $table.Borders.Item(-6).LineStyle = 0
        $table.Borders.Item(-1).LineWidth = 12
        $table.Borders.Item(-3).LineWidth = 12
        if ($Wide) {
            $table.PreferredWidthType = 3
            $table.PreferredWidth = $word.CentimetersToPoints(17.4)
        }
        $selection.SetRange($table.Range.End, $table.Range.End)
        $selection.TypeParagraph()
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($table)
    }

    function Enter-ColumnMode {
        param([int]$ColumnCount)
        $selection.InsertBreak(3)
        $newSection = $doc.Sections.Item($doc.Sections.Count)
        Configure-Section -Section $newSection -ColumnCount $ColumnCount
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($newSection)
    }

    $firstSection = $doc.Sections.Item(1)
    Configure-Section -Section $firstSection -ColumnCount 1
    [void][Runtime.InteropServices.Marshal]::ReleaseComObject($firstSection)

    Add-Paragraph -Text $meta["TITLE"] -FontSize 14 -Bold $true -Alignment 1 `
        -FirstLineIndent 0 -SpaceBefore 0 -SpaceAfter 9 -LineSpacing 18 -KeepWithNext $true
    Add-Paragraph -Text $meta["AUTHORS"] -FontSize 10 -Alignment 1 -FirstLineIndent 0 `
        -SpaceAfter 3 -LineSpacing 13 -KeepWithNext $true
    Add-Paragraph -Text $meta["AFFILIATION"] -FontSize 8 -Italic $true -Alignment 1 `
        -FirstLineIndent 0 -SpaceAfter 10 -LineSpacing 10 -KeepWithNext $true
    Add-Paragraph -Text $meta["ABSTRACT"] -FontSize 9 -Alignment 3 `
        -LeftIndent $word.CentimetersToPoints(1.3) -RightIndent $word.CentimetersToPoints(1.3) `
        -FirstLineIndent 0 -SpaceAfter 6 -LineSpacing 11.5
    Add-Paragraph -Text ("Key Words:  " + $meta["KEYWORDS"]) -FontSize 9 -Bold $true `
        -Alignment 1 -FirstLineIndent 0 -SpaceAfter 4 -LineSpacing 13 -KeepWithNext $true

    Enter-ColumnMode -ColumnCount 2

    $startIndex = 0
    for ($idx = 0; $idx -lt $lines.Count; $idx++) {
        if ($lines[$idx] -match '^##\s+') {
            $startIndex = $idx
            break
        }
    }

    $inReferences = $false
    for ($i = $startIndex; $i -lt $lines.Count; $i++) {
        $line = $lines[$i].Trim()
        if (-not $line) {
            continue
        }
        if ($line -match '^##\s+(.+)$') {
            Add-MainHeading -Text $matches[1]
            $inReferences = $matches[1] -eq "References"
            continue
        }
        if ($line -match '^###\s+(.+)$') {
            Add-SubHeading -Text $matches[1]
            continue
        }
        if ($line -match '^\[\[FIGURE_WIDE\|([^|]+)\|([^|]+)\|([^\]]+)\]\]$') {
            Enter-ColumnMode -ColumnCount 1
            Add-Figure -RelativePath $matches[1] -Caption $matches[2] -WidthInches ([double]$matches[3])
            Enter-ColumnMode -ColumnCount 2
            continue
        }
        if ($line -match '^\[\[FIGURE\|([^|]+)\|([^|]+)\|([^\]]+)\]\]$') {
            Add-Figure -RelativePath $matches[1] -Caption $matches[2] -WidthInches ([double]$matches[3])
            continue
        }
        if ($line -match '^\[\[EQUATION\|([^|]+)\|([^\]]+)\]\]$') {
            Add-Equation -EquationText $matches[1] -Number $matches[2]
            continue
        }
        if ($line -match '^\[\[TABLE(_WIDE)?_START\|(.+)\]\]$') {
            $wide = [bool]$matches[1]
            $caption = $matches[2]
            $tableRows = New-Object System.Collections.Generic.List[object]
            $i++
            while ($i -lt $lines.Count -and $lines[$i].Trim() -ne '[[TABLE_END]]') {
                $rowText = $lines[$i].Trim()
                if ($rowText) {
                    $cells = @($rowText -split '\|', -1)
                    $tableRows.Add($cells)
                }
                $i++
            }
            if ($tableRows.Count -eq 0) {
                throw "Empty table: $caption"
            }
            if ($wide) {
                Enter-ColumnMode -ColumnCount 1
            }
            Add-DataTable -Caption $caption -Rows $tableRows.ToArray() -Wide $wide
            if ($wide) {
                Enter-ColumnMode -ColumnCount 2
            }
            continue
        }

        $isDraftNote = $line.StartsWith("[Draft note:")
        if ($inReferences) {
            Add-Paragraph -Text $line -FontSize 8 -Alignment 3 `
                -LeftIndent 10 -FirstLineIndent -10 -LineSpacing 10
        }
        else {
            Add-Paragraph -Text $line -FontSize 10 -Italic $isDraftNote -Alignment 3 `
                -FirstLineIndent $(if ($isDraftNote) { 0 } else { 10 }) -LineSpacing 12
        }
        if ($isDraftNote) {
            $previous = $doc.Paragraphs.Item($doc.Paragraphs.Count - 1)
            $previous.Range.Font.Color = 192
            $previous.Range.HighlightColorIndex = 7
            [void][Runtime.InteropServices.Marshal]::ReleaseComObject($previous)
        }
    }

    $doc.Fields.Update() | Out-Null
    $doc.Repaginate()
    $pageCount = $doc.ComputeStatistics(2)
    $wordCount = $doc.ComputeStatistics(0)
    $doc.Save()
    $doc.ExportAsFixedFormat($pdfFull, 17)
    Write-Output "DOCX: $outputFull"
    Write-Output "PDF : $pdfFull"
    Write-Output "Pages: $pageCount"
    Write-Output "Words: $wordCount"
}
finally {
    if ($doc -ne $null) {
        try { $doc.Close($true) } catch {}
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($doc)
    }
    if ($word -ne $null) {
        try { $word.Quit() } catch {}
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($word)
    }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}
