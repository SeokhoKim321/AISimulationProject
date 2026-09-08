param(
    [string]$OutputPath = "2026 해병항공 전투발전 워크숍\제3회_해병항공_전투발전_워크숍_AI데이터_조종능력향상_김석호_260922.pptx",
    [string]$PdfPath = "2026 해병항공 전투발전 워크숍\제3회_해병항공_전투발전_워크숍_AI데이터_조종능력향상_김석호_260922.pdf"
)

$ErrorActionPreference = "Stop"

function Resolve-ProjectPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location).Path $Path))
}

function Convert-HexColor {
    param([string]$Hex)
    $value = $Hex.TrimStart('#')
    $r = [Convert]::ToInt32($value.Substring(0, 2), 16)
    $g = [Convert]::ToInt32($value.Substring(2, 2), 16)
    $b = [Convert]::ToInt32($value.Substring(4, 2), 16)
    return $r + (256 * $g) + (65536 * $b)
}

$C = @{
    Navy = Convert-HexColor "071B33"
    Navy2 = Convert-HexColor "0B294D"
    Blue = Convert-HexColor "1769AA"
    Cyan = Convert-HexColor "19B7C9"
    Mint = Convert-HexColor "54C7A1"
    Green = Convert-HexColor "2E9E6F"
    Coral = Convert-HexColor "EF6555"
    Amber = Convert-HexColor "E5A93D"
    Sand = Convert-HexColor "F2CC72"
    Ink = Convert-HexColor "172536"
    Slate = Convert-HexColor "526171"
    Light = Convert-HexColor "F4F7FA"
    PaleBlue = Convert-HexColor "E8F1FA"
    PaleMint = Convert-HexColor "E6F5F0"
    PaleCoral = Convert-HexColor "FDEDEA"
    PaleAmber = Convert-HexColor "FFF6DF"
    Line = Convert-HexColor "D6DEE7"
    White = Convert-HexColor "FFFFFF"
    Black = Convert-HexColor "000000"
}

$outputFull = Resolve-ProjectPath $OutputPath
$pdfFull = Resolve-ProjectPath $PdfPath
$outputDir = Split-Path -Parent $outputFull
if (-not (Test-Path -LiteralPath $outputDir)) {
    [void](New-Item -ItemType Directory -Path $outputDir -Force)
}

$cockpitOverlay = Resolve-ProjectPath "2026 해병항공 전투발전 워크숍\assets\cockpit_aoi_overlay.png"
$matrixFigure = Resolve-ProjectPath "2026 해병항공 전투발전 워크숍\assets\aoi_dtmc_complete6.png"
foreach ($asset in @($cockpitOverlay, $matrixFigure)) {
    if (-not (Test-Path -LiteralPath $asset)) {
        throw "Required presentation asset not found: $asset"
    }
}

$ppt = $null
$presentation = $null
$slideWidth = 960.0
$slideHeight = 540.0
$fontName = "맑은 고딕"

function Set-Background {
    param($Slide, [int]$Color)
    $Slide.FollowMasterBackground = 0
    $Slide.Background.Fill.Solid()
    $Slide.Background.Fill.ForeColor.RGB = $Color
}

function Add-Text {
    param(
        $Slide,
        [string]$Text,
        [double]$X,
        [double]$Y,
        [double]$W,
        [double]$H,
        [double]$Size = 18,
        [int]$Color = $C.Ink,
        [bool]$Bold = $false,
        [int]$Align = 1,
        [int]$VAlign = 1,
        [string]$Font = $fontName,
        [double]$Margin = 0,
        [bool]$Italic = $false
    )
    $shape = $Slide.Shapes.AddTextbox(1, $X, $Y, $W, $H)
    $shape.TextFrame2.MarginLeft = $Margin
    $shape.TextFrame2.MarginRight = $Margin
    $shape.TextFrame2.MarginTop = $Margin
    $shape.TextFrame2.MarginBottom = $Margin
    $shape.TextFrame2.WordWrap = -1
    $shape.TextFrame2.AutoSize = 0
    $shape.TextFrame2.VerticalAnchor = $VAlign
    $range = $shape.TextFrame2.TextRange
    $range.Text = $Text
    $range.Font.Name = $Font
    $range.Font.Size = $Size
    $range.Font.Fill.ForeColor.RGB = $Color
    $range.Font.Bold = $(if ($Bold) { -1 } else { 0 })
    $range.Font.Italic = $(if ($Italic) { -1 } else { 0 })
    $range.ParagraphFormat.Alignment = $Align
    return $shape
}

function Add-Rect {
    param(
        $Slide,
        [double]$X,
        [double]$Y,
        [double]$W,
        [double]$H,
        [int]$Fill,
        [int]$Line = $C.Line,
        [double]$RadiusType = 5,
        [double]$LineWeight = 1
    )
    $shape = $Slide.Shapes.AddShape([int]$RadiusType, $X, $Y, $W, $H)
    $shape.Fill.Solid()
    $shape.Fill.ForeColor.RGB = $Fill
    $shape.Line.ForeColor.RGB = $Line
    $shape.Line.Weight = $LineWeight
    return $shape
}

function Add-Line {
    param(
        $Slide,
        [double]$X1,
        [double]$Y1,
        [double]$X2,
        [double]$Y2,
        [int]$Color = $C.Line,
        [double]$Weight = 2,
        [bool]$Arrow = $false,
        [bool]$Dash = $false
    )
    $shape = $Slide.Shapes.AddLine($X1, $Y1, $X2, $Y2)
    $shape.Line.ForeColor.RGB = $Color
    $shape.Line.Weight = $Weight
    if ($Arrow) { $shape.Line.EndArrowheadStyle = 3 }
    if ($Dash) { $shape.Line.DashStyle = 4 }
    return $shape
}

function Add-CircleLabel {
    param($Slide, [string]$Text, [double]$X, [double]$Y, [double]$D, [int]$Fill, [int]$TextColor = $C.White, [double]$Size = 16)
    $shape = $Slide.Shapes.AddShape(9, $X, $Y, $D, $D)
    $shape.Fill.Solid()
    $shape.Fill.ForeColor.RGB = $Fill
    $shape.Line.Visible = 0
    [void](Add-Text $Slide $Text $X $Y $D $D $Size $TextColor $true 2 3)
    return $shape
}

function Add-Tag {
    param($Slide, [string]$Text, [double]$X, [double]$Y, [double]$W, [int]$Fill, [int]$TextColor = $C.White)
    [void](Add-Rect $Slide $X $Y $W 24 $Fill $Fill 5 0)
    [void](Add-Text $Slide $Text ($X + 8) ($Y + 1) ($W - 16) 20 11 $TextColor $true 2 3)
}

function Add-PictureFit {
    param($Slide, [string]$Path, [double]$X, [double]$Y, [double]$W, [double]$H)
    Add-Type -AssemblyName System.Drawing
    $image = [System.Drawing.Image]::FromFile($Path)
    try {
        $imageRatio = $image.Width / $image.Height
    }
    finally {
        $image.Dispose()
    }
    $boxRatio = $W / $H
    if ($imageRatio -gt $boxRatio) {
        $drawW = $W
        $drawH = $W / $imageRatio
        $drawX = $X
        $drawY = $Y + (($H - $drawH) / 2)
    }
    else {
        $drawH = $H
        $drawW = $H * $imageRatio
        $drawX = $X + (($W - $drawW) / 2)
        $drawY = $Y
    }
    return $Slide.Shapes.AddPicture($Path, 0, -1, $drawX, $drawY, $drawW, $drawH)
}

function Add-Notes {
    param($Slide, [string]$Text)
    $notesPage = $null
    $placeholders = $null
    try {
        $notesPage = $Slide.NotesPage
        $placeholders = $notesPage.Shapes.Placeholders
        for ($i = 1; $i -le $placeholders.Count; $i++) {
            $shape = $placeholders.Item($i)
            try {
                if ($shape.PlaceholderFormat.Type -eq 2) {
                    $shape.TextFrame.TextRange.Text = $Text
                    break
                }
            }
            finally {
                [void][Runtime.InteropServices.Marshal]::ReleaseComObject($shape)
            }
        }
    }
    catch {
        Write-Warning "Could not add notes to slide $($Slide.SlideIndex): $($_.Exception.Message)"
    }
    finally {
        if ($placeholders -ne $null) {
            [void][Runtime.InteropServices.Marshal]::ReleaseComObject($placeholders)
        }
        if ($notesPage -ne $null) {
            [void][Runtime.InteropServices.Marshal]::ReleaseComObject($notesPage)
        }
    }
    Write-Output ("Prepared slide {0:00}: {1}" -f $Slide.SlideIndex, (($Text -split "`n")[0]))
}

function Add-Footer {
    param($Slide, [int]$Number, [bool]$Dark = $false)
    $lineColor = $(if ($Dark) { Convert-HexColor "33516E" } else { $C.Line })
    $textColor = $(if ($Dark) { Convert-HexColor "AFC3D7" } else { $C.Slate })
    [void](Add-Line $Slide 36 510 924 510 $lineColor 0.8)
    [void](Add-Text $Slide "제3회 해병항공 전투발전 워크숍  |  공개자료·연구실 기술검증 기반 제안" 38 514 700 14 9 $textColor $false 1 3)
    [void](Add-Text $Slide ("{0:00}" -f $Number) 884 514 38 14 9 $textColor $true 3 3)
}

function Add-Title {
    param($Slide, [string]$Section, [string]$Title, [int]$Number, [string]$Subtitle = "")
    [void](Add-Text $Slide $Section 40 24 250 18 11 $C.Blue $true 1 3)
    [void](Add-Text $Slide $Title 40 48 870 42 27 $C.Navy $true 1 3)
    if ($Subtitle) {
        [void](Add-Text $Slide $Subtitle 42 88 850 24 13 $C.Slate $false 1 3)
    }
    Add-Footer $Slide $Number
}

function New-BlankSlide {
    param([int]$Index, [int]$Background = $C.Light)
    $slide = $presentation.Slides.Add($Index, 12)
    Set-Background $slide $Background
    return $slide
}

function Add-CardTitleBody {
    param($Slide, [string]$Title, [string]$Body, [double]$X, [double]$Y, [double]$W, [double]$H, [int]$Accent, [int]$Fill = $C.White, [double]$TitleSize = 17, [double]$BodySize = 14)
    [void](Add-Rect $Slide $X $Y $W $H $Fill $C.Line 5 1)
    [void](Add-Rect $Slide $X $Y 7 $H $Accent $Accent 1 0)
    [void](Add-Text $Slide $Title ($X + 20) ($Y + 14) ($W - 35) 28 $TitleSize $C.Navy $true 1 3)
    [void](Add-Text $Slide $Body ($X + 20) ($Y + 50) ($W - 35) ($H - 60) $BodySize $C.Ink $false 1 1)
}

function Add-KpiCard {
    param($Slide, [string]$Value, [string]$Label, [double]$X, [double]$Y, [double]$W, [double]$H, [int]$Accent)
    [void](Add-Rect $Slide $X $Y $W $H $C.White $C.Line 5 1)
    [void](Add-Text $Slide $Value ($X + 10) ($Y + 12) ($W - 20) 42 28 $Accent $true 2 3)
    [void](Add-Text $Slide $Label ($X + 12) ($Y + 58) ($W - 24) ($H - 65) 12 $C.Slate $true 2 1)
}

function Add-HelicopterOutline {
    param($Slide)
    [void](Add-Line $Slide 622 235 900 235 $C.Cyan 5)
    [void](Add-Line $Slide 760 200 760 275 $C.Cyan 3)
    [void](Add-Rect $Slide 676 265 174 55 (Convert-HexColor "123A5C") $C.Cyan 5 2)
    [void](Add-Line $Slide 676 294 608 277 $C.Cyan 5)
    [void](Add-Line $Slide 608 277 580 259 $C.Cyan 4)
    [void](Add-Line $Slide 612 267 586 289 $C.Cyan 2)
    [void](Add-Line $Slide 612 267 586 245 $C.Cyan 2)
    [void](Add-Line $Slide 704 320 686 350 $C.Cyan 3)
    [void](Add-Line $Slide 824 320 844 350 $C.Cyan 3)
    [void](Add-Line $Slide 676 350 856 350 $C.Cyan 3)
    [void](Add-Line $Slide 706 275 752 275 $C.White 1.5)
    [void](Add-Line $Slide 756 275 804 275 $C.White 1.5)
    [void](Add-Line $Slide 807 275 838 294 $C.White 1.5)
}

try {
    $ppt = New-Object -ComObject PowerPoint.Application
    $ppt.Visible = -1
    $presentation = $ppt.Presentations.Add()
    $presentation.PageSetup.SlideWidth = $slideWidth
    $presentation.PageSetup.SlideHeight = $slideHeight

    # 01. Title
    $slide = New-BlankSlide 1 $C.Navy
    [void](Add-Rect $slide 0 0 960 14 $C.Cyan $C.Cyan 1 0)
    [void](Add-Text $slide "제3회 해병항공 전투발전 워크숍" 48 46 520 24 15 $C.Cyan $true 1 3)
    [void](Add-Text $slide "AI 데이터를 활용한\n조종능력 향상 방안" 48 112 540 118 38 $C.White $true 1 1)
    [void](Add-Text $slide "시선·조종·상황 데이터를 결합한\n데이터 기반 디브리핑 프레임워크" 52 258 500 62 20 (Convert-HexColor "C5D8E9") $false 1 1)
    [void](Add-Tag $slide "APISAT 연구 기반" 50 354 150 $C.Blue)
    [void](Add-Tag $slide "군 적용 개념 제안" 210 354 160 $C.Green)
    [void](Add-Text $slide "김석호  |  인하대학교 항공우주공학과\n2026. 09. 22." 50 418 430 52 15 $C.White $false 1 1)
    Add-HelicopterOutline $slide
    [void](Add-Text $slide "TRAIN  ·  MEASURE  ·  DEBRIEF  ·  IMPROVE" 595 404 310 22 12 $C.Cyan $true 2 3)
    Add-Notes $slide @"
[00:40]
안녕하십니까. 오늘은 AI 데이터를 활용해 조종능력을 향상시키는 방법을 말씀드리겠습니다. 여기서 AI 데이터란 단순 비행점수나 영상이 아니라, 조종사가 어디를 보았고, 항공기가 어떤 상태였고, 어떤 조작을 언제 했는지를 같은 시간축으로 정렬한 데이터입니다. 연구실에서 구현한 X-Plane·Java·Tobii 프레임워크의 검증 결과를 먼저 보여드리고, 이 수집·분석 구조를 해병항공의 인가된 시뮬레이터와 교관 디브리핑 체계에 어떻게 단계적으로 이식할 수 있는지 제안드리겠습니다.
"@

    # 02. Executive summary
    $slide = New-BlankSlide 2
    Add-Title $slide "01  핵심 제안" "오늘 말씀드릴 결론은 세 가지입니다" 2 "AI는 결론을 대신하는 채점기가 아니라, 교관이 근거를 더 잘 보게 하는 도구입니다."
    Add-CardTitleBody $slide "구현됨" "비행상태·조종입력·시선·상황 이벤트를 동일 시간축으로 수집하고 trial 단위로 자동 분석" 42 136 276 238 $C.Blue $C.PaleBlue 18 15
    Add-CardTitleBody $slide "확인됨" "36개 균형 trial에서 데이터 완전성, gaze 품질, 반응 event, AOI 전이 Matrix 산출 가능성 검증" 342 136 276 238 $C.Green $C.PaleMint 18 15
    Add-CardTitleBody $slide "제안함" "마린온급 시뮬레이터의 data adapter와 교관용 디브리핑 화면으로 이식 후, 검증된 기준모델을 단계적으로 구축" 642 136 276 238 $C.Coral $C.PaleCoral 18 15
    [void](Add-Rect $slide 92 406 776 54 $C.Navy $C.Navy 5 0)
    [void](Add-Text $slide "목표: ‘시간 이수형 훈련’에 행동 데이터 기반 설명과 반복학습을 더한다" 112 418 736 30 19 $C.White $true 2 3)
    Add-Notes $slide @"
[00:55 | 누적 01:35]
결론은 세 가지입니다. 첫째, 수집과 분석 파이프라인은 이미 구현했습니다. 둘째, 소규모 기술검증이지만 36개 trial에서 실제로 시선과 조종 데이터를 동기화하고 Matrix까지 만들 수 있음을 확인했습니다. 셋째, 군 활용은 X-Plane을 그대로 가져가는 것이 아니라, 인가된 마린온급 시뮬레이터에 data adapter를 붙이고 교관이 해석하는 디브리핑 도구로 이식하는 방향입니다. 사람을 한 점수로 서열화하는 시스템은 제안하지 않습니다. 훈련 중 행동을 설명하고 다음 훈련 목표를 정하는 보조도구를 제안합니다.
"@

    # 03. Why now
    $slide = New-BlankSlide 3
    Add-Title $slide "01  운용 맥락" "해병항공은 고난도 임무를 안전하게 반복 숙달해야 합니다" 3 "공식 자료가 제시하는 임무·훈련 방향과 데이터 기반 디브리핑의 접점"
    Add-CardTitleBody $slide "상륙·공중기동" "함정–육상 간 병력·장비 수송\n공중강습 및 입체적 상륙작전" 42 132 270 160 $C.Blue $C.White 17 14
    Add-CardTitleBody $slide "신속 대응" "도서지역 국지도발 등\n시간 압박이 큰 상황 대응" 345 132 270 160 $C.Coral $C.White 17 14
    Add-CardTitleBody $slide "고난도 모의훈련" "악천후·비상상황·전술상황을\n실기체 위험 없이 반복" 648 132 270 160 $C.Green $C.White 17 14
    [void](Add-Line $slide 130 330 830 330 $C.Cyan 4 $true)
    [void](Add-CircleLabel $slide "훈련" 112 309 42 $C.Navy $C.White 13)
    [void](Add-CircleLabel $slide "데이터" 319 309 42 $C.Blue $C.White 12)
    [void](Add-CircleLabel $slide "설명" 526 309 42 $C.Green $C.White 13)
    [void](Add-CircleLabel $slide "재훈련" 733 309 42 $C.Coral $C.White 11)
    [void](Add-Rect $slide 95 390 770 72 $C.PaleAmber $C.Amber 5 1)
    [void](Add-Text $slide "시뮬레이터는 이미 ‘상황을 재현’합니다.\n다음 단계는 그 안에서 나타난 조종행동을 정량적으로 설명하는 것입니다." 120 402 720 48 18 $C.Navy $true 2 3)
    [void](Add-Text $slide "출처: 방위사업청(2023.06.29, 마린온 전력화), KAI 훈련체계, 방위사업청(2026.07.29, KUH-1 비행훈련시뮬레이터)" 44 483 860 15 9 $C.Slate $false 1 3)
    Add-Notes $slide @"
[01:10 | 누적 02:45]
공식 자료에서 마린온은 병력과 장비 수송, 공중강습, 도서지역 신속대응 등 다양한 임무를 수행한다고 설명합니다. 또한 KAI는 MUH 시뮬레이터를 훈련체계로 제시하고 있고, 방위사업청도 고난도 상황과 비상상황을 반복 훈련하는 시뮬레이터의 가치를 강조하고 있습니다. 즉 상황을 재현하는 기반은 이미 존재합니다. 제가 제안하는 발전 방향은 그 훈련을 끝낸 뒤 ‘잘했다, 부족했다’만 남기는 것이 아니라, 무엇을 보고 어떤 조작을 언제 했는지를 근거로 설명하고 다음 훈련에 반영하는 폐쇄루프를 만드는 것입니다.
"@

    # 04. Problem definition
    $slide = New-BlankSlide 4
    Add-Title $slide "02  문제 정의" "훈련 종료 후, 조종행동을 설명할 근거가 충분한가?" 4
    [void](Add-Rect $slide 45 128 390 310 $C.PaleCoral $C.Coral 5 1)
    [void](Add-Text $slide "기존에 쉽게 남는 것" 68 148 320 28 19 $C.Coral $true 1 3)
    [void](Add-Text $slide "• 비행경로·고도·속도\n• 임무 성공/실패\n• 교관의 관찰과 기억\n• 일부 이벤트·통신 기록" 72 198 310 150 17 $C.Ink $false 1 1)
    [void](Add-Text $slide "결과는 보이지만, 판단 과정은 부분적으로만 남음" 70 366 330 44 15 $C.Coral $true 1 3)
    [void](Add-Line $slide 448 282 510 282 $C.Navy 5 $true)
    [void](Add-Rect $slide 525 128 390 310 $C.PaleMint $C.Green 5 1)
    [void](Add-Text $slide "추가로 남겨야 할 것" 548 148 320 28 19 $C.Green $true 1 3)
    [void](Add-Text $slide "• 어디를 보았는가\n• 위협이 언제 관측 가능했는가\n• 조종입력이 언제 변했는가\n• SOP·숙련 기준과 무엇이 다른가" 552 198 330 150 17 $C.Ink $false 1 1)
    [void](Add-Text $slide "결과 + 과정 → 설명 가능한 디브리핑" 550 366 330 44 15 $C.Green $true 1 3)
    Add-Notes $slide @"
[00:55 | 누적 03:40]
비행 시뮬레이터는 경로, 고도, 속도와 임무 성공 여부를 잘 기록합니다. 교관의 전문적인 관찰도 핵심입니다. 다만 훈련이 복잡해질수록 조종사가 왜 늦게 반응했는지, 계기를 지나치게 오래 보았는지, 외부 탐색이 부족했는지 같은 판단 과정을 사후에 완전히 재구성하기 어렵습니다. 그래서 기존 기록을 대체하는 것이 아니라 시선, 상황 이벤트, 조종입력의 시간 관계를 추가로 남겨야 합니다. 이 데이터가 있어야 교관의 평가를 반박하는 것이 아니라, 평가의 근거를 더 구체적으로 보여줄 수 있습니다.
"@

    # 05. Framework
    $slide = New-BlankSlide 5
    Add-Title $slide "03  구현 프레임워크" "서로 다른 데이터 네 종류를 하나의 사건 시간축으로 정렬했습니다" 5
    Add-CardTitleBody $slide "시뮬레이터" "항공기 상태\n위치·자세·속도" 42 134 176 125 $C.Blue $C.PaleBlue 16 14
    Add-CardTitleBody $slide "조종 입력" "pitch·roll·yaw\nthrottle" 242 134 176 125 $C.Cyan $C.PaleBlue 16 14
    Add-CardTitleBody $slide "시선" "화면 좌표\n좌·우 눈 validity" 442 134 176 125 $C.Amber $C.PaleAmber 16 14
    Add-CardTitleBody $slide "상황 이벤트" "spawn·20px 기회\nresponse·trial 종료" 642 134 176 125 $C.Coral $C.PaleCoral 16 14
    [void](Add-Line $slide 131 278 759 278 $C.Navy 3)
    foreach ($x in @(130, 330, 530, 730)) { [void](Add-Line $slide $x 258 $x 303 $C.Navy 2 $true) }
    [void](Add-Rect $slide 210 305 540 72 $C.Navy $C.Navy 5 0)
    [void](Add-Text $slide "PC 시간 병합 + event alignment" 240 318 480 26 22 $C.White $true 2 3)
    [void](Add-Text $slide "원본 스트림은 분리 보존 → 모든 결과를 row 단위로 역추적" 245 347 470 18 12 (Convert-HexColor "BFD4E5") $false 2 3)
    [void](Add-Line $slide 480 377 480 408 $C.Navy 3 $true)
    [void](Add-Rect $slide 115 410 730 60 $C.White $C.Green 5 1.5)
    [void](Add-Text $slide "trial 요약  |  반응시간  |  AOI 분포  |  전이 Matrix  |  감사 CSV/SVG" 140 424 680 28 18 $C.Navy $true 2 3)
    Add-Notes $slide @"
[01:20 | 누적 05:00]
현재 프레임워크는 네 종류의 데이터를 모읍니다. 첫째 항공기 상태, 둘째 조종 입력, 셋째 시선 좌표, 넷째 시나리오 이벤트입니다. 중요한 점은 이들을 단순히 한 파일에 섞지 않고 원본을 각각 보존한 뒤 PC 시간으로 병합한다는 것입니다. 그래서 반응시간 하나를 제시하더라도 어떤 event와 어떤 control row에서 나왔는지 다시 확인할 수 있습니다. 군 시뮬레이터로 옮길 때도 분석 알고리즘보다 먼저 이 데이터 계약과 시간동기 구조를 확보해야 합니다. 데이터가 감사 가능해야 교관이 신뢰하고 수정할 수 있습니다.
"@

    # 06. Automatic scenario
    $slide = New-BlankSlide 6
    Add-Title $slide "03  구현 프레임워크" "한 번 시작하면 6개 trial이 자동으로 진행됩니다" 6 "조종사의 버튼 조작을 줄이고, 방향 균형과 washout을 자동 보장"
    $timelineY = 262
    [void](Add-Line $slide 80 $timelineY 880 $timelineY $C.Navy 4 $true)
    $events = @(
        @{X=96; C=$C.Slate; T="SESSION\nSTART"; S="1회 시작"},
        @{X=235; C=$C.Blue; T="음성 과제"; S="6~10 s"},
        @{X=370; C=$C.Amber; T="속도 gate"; S="1.5 s 유지"},
        @{X=505; C=$C.Coral; T="INTRUDER\nSPAWN"; S="7~13 px"},
        @{X=640; C=$C.Coral; T="시각적 기회"; S="20 px"},
        @{X=775; C=$C.Green; T="반응 검출"; S="지속 입력변화"},
        @{X=868; C=$C.Navy; T="WASHOUT"; S="10~15 s"}
    )
    foreach ($e in $events) {
        [void](Add-CircleLabel $slide "" ($e.X - 10) ($timelineY - 10) 20 $e.C $C.White 10)
        [void](Add-Text $slide $e.T ($e.X - 58) 162 116 48 14 $C.Navy $true 2 3)
        [void](Add-Text $slide $e.S ($e.X - 55) 294 110 24 11 $C.Slate $false 2 3)
    }
    [void](Add-Rect $slide 96 348 768 70 $C.PaleBlue $C.Blue 5 1)
    [void](Add-Text $slide "FRONT 2회  ·  LEFT 2회  ·  RIGHT 2회    /    trial 사이 자세 안정 3 s 확인" 125 362 710 24 17 $C.Navy $true 2 3)
    [void](Add-Text $slide "초기상황 reload 없이 최종접근을 계속하며 반복 → 실제 운용 흐름을 덜 끊는 실험" 130 391 700 18 12 $C.Slate $false 2 3)
    [void](Add-Tag $slide "현재 구현" 790 112 120 $C.Green)
    Add-Notes $slide @"
[01:20 | 누적 06:20]
참가자가 세션을 한 번 시작하면 여섯 trial이 자동으로 진행됩니다. 매 trial마다 음성 속도 과제를 주고, 속도가 일정 범위에서 유지되면 침입기를 생성합니다. 이후 화면에서 침입기가 20 pixel 크기에 도달한 시점을 시각적 관측 기회로 기록하고, 지속적인 조종 입력변화를 반응으로 검출합니다. 전·좌·우 방향은 각각 두 번씩 나오며 순서는 바뀝니다. trial 사이에는 10~15초 washout과 3초 자세 안정 조건을 둡니다. 이 자동화는 참가자의 키 입력 오차를 줄이고, 연속 운용 속에서도 반복 가능한 데이터를 만드는 역할을 합니다.
"@

    # 07. Event anchors
    $slide = New-BlankSlide 7
    Add-Title $slide "03  구현 프레임워크" "‘보였을 때’와 ‘인지했을 때’를 구분했습니다" 7
    $laneX = 130
    $laneW = 720
    foreach ($y in @(160, 235, 310, 385)) { [void](Add-Line $slide $laneX $y ($laneX + $laneW) $y $C.Line 2) }
    [void](Add-Text $slide "상황" 50 145 65 30 15 $C.Slate $true 2 3)
    [void](Add-Text $slide "화면" 50 220 65 30 15 $C.Slate $true 2 3)
    [void](Add-Text $slide "조작" 50 295 65 30 15 $C.Slate $true 2 3)
    [void](Add-Text $slide "분석" 50 370 65 30 15 $C.Slate $true 2 3)
    [void](Add-CircleLabel $slide "1" 244 144 32 $C.Coral $C.White 14)
    [void](Add-Text $slide "SPAWN\n상황 노출" 212 178 100 44 13 $C.Coral $true 2 1)
    [void](Add-CircleLabel $slide "2" 454 219 32 $C.Amber $C.White 14)
    [void](Add-Text $slide "20 px\n시각적 기회" 418 253 110 44 13 $C.Amber $true 2 1)
    [void](Add-CircleLabel $slide "?" 585 219 32 $C.Slate $C.White 14)
    [void](Add-Text $slide "실제 인지\n현재 미측정" 548 253 110 44 13 $C.Slate $true 2 1)
    [void](Add-CircleLabel $slide "3" 720 294 32 $C.Green $C.White 14)
    [void](Add-Text $slide "지속 입력변화\n반응 event" 675 328 120 44 13 $C.Green $true 2 1)
    [void](Add-Line $slide 470 405 736 405 $C.Blue 4 $true)
    [void](Add-Text $slide "운용 반응시간 L = t_response − t_opportunity" 420 427 370 25 17 $C.Blue $true 2 3)
    [void](Add-Rect $slide 120 458 730 34 $C.PaleCoral $C.Coral 5 1)
    [void](Add-Text $slide "20 px는 반복 가능한 분석 기준이며, 조종사가 실제로 위협을 인지한 시점이라는 뜻은 아닙니다." 145 465 680 20 13 $C.Coral $true 2 3)
    Add-Notes $slide @"
[01:10 | 누적 07:30]
분석에서 가장 중요한 구분입니다. 침입기 생성은 상황 노출 시점입니다. 화면상 크기가 20 pixel에 도달한 시점은 이 장비 구성에서 비교 가능한 시각적 기회입니다. 그러나 그 순간 조종사가 실제로 인지했다고 말할 수는 없습니다. 인지는 현재 직접 측정하지 않았습니다. 마지막으로 조종 입력이 기준선에서 일정 시간 이상 변하면 반응 event를 기록합니다. 따라서 발표에서 사용하는 반응시간은 20 pixel 기회 이후 운용상 반응시간입니다. 이 용어 구분을 지켜야 데이터가 할 수 있는 말과 할 수 없는 말을 혼동하지 않습니다.
"@

    # 08. Gaze classification
    $slide = New-BlankSlide 8
    Add-Title $slide "04  시선 데이터" "고정 계기 AOI와 움직이는 외부 AOI를 함께 사용합니다" 8
    [void](Add-Rect $slide 38 126 576 326 $C.Black $C.Navy 5 1)
    [void](Add-PictureFit $slide $cockpitOverlay 42 130 568 318)
    [void](Add-Tag $slide "고정 계기 AOI" 58 141 128 $C.Blue)
    Add-CardTitleBody $slide "계기·패널" "Airspeed / Attitude / Altitude\nHeading / Vertical speed / NAV" 640 128 274 92 $C.Blue $C.PaleBlue 15 12
    Add-CardTitleBody $slide "침입기" "매 순간 화면 좌표와 크기를 계산하는 동적 AOI" 640 230 274 92 $C.Coral $C.PaleCoral 15 12
    Add-CardTitleBody $slide "활주로·외부" "Runway polygon + monitor grid + 기타 외부환경" 640 332 274 92 $C.Green $C.PaleMint 15 12
    [void](Add-Rect $slide 638 438 278 42 $C.PaleAmber $C.Amber 5 1)
    [void](Add-Text $slide "45 px 여유폭은 잠정값 → 표적시선 검증 필요" 650 448 254 20 11 $C.Amber $true 2 3)
    Add-Notes $slide @"
[01:15 | 누적 08:45]
시선은 화면 좌표만으로는 의미가 없습니다. 그래서 계기판의 airspeed, attitude, altitude 같은 영역은 고정 AOI로 정의합니다. 반면 침입기와 활주로는 항공기 자세와 위치에 따라 화면에서 움직이므로 매 순간 좌표와 크기를 다시 계산해 동적 AOI로 만듭니다. 직접 겹치지 않는 외부 시선은 모니터 격자와 방향 정보를 보조 근거로 남깁니다. 중요한 제한은 45 pixel 여유폭이 아직 engineering candidate라는 점입니다. 현재 결과는 분류 파이프라인 검증에는 쓸 수 있지만, 군 적용 전에는 목표를 실제로 보게 하는 검증 trial로 여유폭을 확정해야 합니다.
"@

    # 09. Matrix
    $slide = New-BlankSlide 9
    Add-Title $slide "04  시선 데이터" "Matrix는 ‘다음에 어디를 볼 확률’을 요약합니다" 9 "행은 현재 AOI, 열은 다음 200 ms AOI"
    [void](Add-Rect $slide 42 125 500 348 $C.White $C.Line 5 1)
    [void](Add-PictureFit $slide $matrixFigure 48 131 488 336)
    Add-CardTitleBody $slide "대각선" "같은 영역을 계속 본 확률\n예: INTRUDER→INTRUDER 0.79" 575 132 338 95 $C.Blue $C.PaleBlue 16 13
    Add-CardTitleBody $slide "비대각선" "다른 영역으로 시선을 옮긴 확률\n예: AIRSPEED→PANEL_OTHER 0.36" 575 240 338 95 $C.Green $C.PaleMint 16 13
    Add-CardTitleBody $slide "군 적용 시" "숙련 조종사·SOP 기준과 비교해\n과도한 고착 또는 누락 탐색을 질문" 575 348 338 95 $C.Coral $C.PaleCoral 16 13
    [void](Add-Text $slide "현재 Matrix는 기술검증 표본이며, 숙련 조종사 기준값이 아닙니다." 574 458 340 26 11 $C.Coral $true 2 3)
    Add-Notes $slide @"
[01:20 | 누적 10:05]
시선 Matrix는 어렵게 보이지만 읽는 방법은 단순합니다. 행이 지금 보고 있는 곳, 열이 다음 200 millisecond에 본 곳입니다. 대각선은 같은 영역을 유지한 확률이고, 비대각선은 다른 영역으로 이동한 확률입니다. 예를 들어 intruder 행의 대각선 0.79는 침입기 영역을 본 뒤 다음 구간에도 그 영역에 머문 비율입니다. 미래에는 숙련 조종사의 Matrix나 SOP가 요구하는 scan pattern과 비교해 ‘무조건 틀렸다’가 아니라, 왜 특정 계기나 외부 시야에 오래 고착했는지를 교관이 질문할 수 있습니다. 현재 Matrix는 기술검증 표본이므로 기준모델로 사용하면 안 됩니다.
"@

    # 10. Feasibility results
    $slide = New-BlankSlide 10
    Add-Title $slide "05  기술검증 결과" "소규모 표본에서 전체 파이프라인의 작동 가능성을 확인했습니다" 10
    Add-KpiCard $slide "6명 / 36" "완료 세션 / 균형 trial\n전·좌·우 각 12회" 44 132 164 126 $C.Blue
    Add-KpiCard $slide "96.3%" "participant-equal\ntrial gaze validity" 224 132 164 126 $C.Green
    Add-KpiCard $slide "31 / 36" "지속 조종반응\nautomatic detection" 404 132 164 126 $C.Coral
    Add-KpiCard $slide "5,942" "200 ms AOI\n인접 전이 수" 584 132 164 126 $C.Amber
    Add-KpiCard $slide "79.9%" "동일 AOI 유지\nself-transition" 764 132 164 126 $C.Blue
    [void](Add-Rect $slide 60 292 840 110 $C.White $C.Line 5 1)
    [void](Add-Text $slide "검출된 visual-opportunity → response" 84 310 350 24 15 $C.Slate $true 1 3)
    [void](Add-Text $slide "평균 3.135 s" 84 343 240 34 25 $C.Navy $true 1 3)
    [void](Add-Text $slide "중앙값 2.138 s" 340 343 240 34 25 $C.Blue $true 1 3)
    [void](Add-Rect $slide 612 309 250 70 $C.PaleCoral $C.Coral 5 1)
    [void](Add-Text $slide "속도 gate\n정상 16 / timeout 20" 632 319 210 50 16 $C.Coral $true 2 3)
    [void](Add-Text $slide "해석: 수집·병합·분류·Matrix 산출의 기술적 실현 가능성 확인" 78 426 804 28 18 $C.Green $true 2 3)
    [void](Add-Text $slide "미확인: 숙련도 차이, 실제 인지시점, 회피 의도, 군 조종사 모집단 효과" 78 458 804 22 13 $C.Coral $true 2 3)
    Add-Notes $slide @"
[01:20 | 누적 11:25]
연구실 기술검증에서는 6명이 자동 세션을 끝내 36개 균형 trial을 확보했습니다. trial 구간 gaze validity는 참가자 동일가중 약 96.3퍼센트였고, 36개 중 31개에서 지속적인 조종 입력변화를 검출했습니다. 200 millisecond 시선 상태 전이는 5,942개였고, 같은 영역을 유지한 비율은 79.9퍼센트였습니다. 반응이 검출된 trial의 20 pixel 기회 이후 반응시간은 평균 3.135초, 중앙값 2.138초였습니다. 다만 속도 gate의 절반 이상이 timeout이었기 때문에 confirmatory experiment 전에 수정해야 합니다. 이 수치는 파이프라인 작동을 보여주지만 숙련도 우열을 보여주는 결과는 아닙니다.
"@

    # 11. Phase dynamics
    $slide = New-BlankSlide 11
    Add-Title $slide "05  기술검증 결과" "침입기가 관측 가능해진 뒤 시선 전환은 증가했습니다" 11 "반복측정 기술표본의 descriptive pattern — 인과효과로 해석하지 않음"
    [void](Add-Text $slide "Self-transition rate (%)" 70 132 350 24 16 $C.Navy $true 2 3)
    [void](Add-Text $slide "AOI switches / min" 540 132 350 24 16 $C.Navy $true 2 3)
    $phaseNames = @("생성 전", "생성→20px", "20px 이후")
    $selfValues = @(79.9, 82.0, 78.2)
    $switchValues = @(58.9, 51.8, 62.7)
    $barColors = @($C.Blue, (Convert-HexColor "9476E8"), $C.Green)
    for ($i = 0; $i -lt 3; $i++) {
        $x1 = 85 + ($i * 120)
        $h1 = ($selfValues[$i] - 70) * 10
        [void](Add-Rect $slide $x1 (360 - $h1) 70 $h1 $barColors[$i] $barColors[$i] 1 0)
        [void](Add-Text $slide ([string]$selfValues[$i]) ($x1 - 5) (330 - $h1) 80 22 14 $C.Navy $true 2 3)
        [void](Add-Text $slide $phaseNames[$i] ($x1 - 20) 370 110 38 12 $C.Slate $false 2 1)

        $x2 = 555 + ($i * 120)
        $h2 = ($switchValues[$i] - 45) * 6
        [void](Add-Rect $slide $x2 (360 - $h2) 70 $h2 $barColors[$i] $barColors[$i] 1 0)
        [void](Add-Text $slide ([string]$switchValues[$i]) ($x2 - 5) (330 - $h2) 80 22 14 $C.Navy $true 2 3)
        [void](Add-Text $slide $phaseNames[$i] ($x2 - 20) 370 110 38 12 $C.Slate $false 2 1)
    }
    [void](Add-Line $slide 70 360 430 360 $C.Slate 1)
    [void](Add-Line $slide 540 360 900 360 $C.Slate 1)
    [void](Add-Rect $slide 168 430 624 50 $C.PaleMint $C.Green 5 1)
    [void](Add-Text $slide "20 px 이후: 동일 영역 고착 ↓  |  다른 AOI로의 전환 ↑" 190 442 580 24 18 $C.Green $true 2 3)
    Add-Notes $slide @"
[01:10 | 누적 12:35]
상황 단계별로 보면 생성 전 self-transition은 79.9퍼센트, 생성부터 20 pixel까지는 82퍼센트로 약간 높아지고, 20 pixel 이후에는 78.2퍼센트로 낮아졌습니다. 반대로 분당 AOI 전환은 51.8에서 62.7로 증가했습니다. 즉 관측 기회 이후 시선을 더 자주 재배분하는 패턴이 보였습니다. 그러나 같은 참가자의 반복 trial이고 구간 길이도 다르기 때문에 인과효과라고 말하지 않습니다. 군 적용에서 중요한 것은 이런 패턴 자체를 정답으로 쓰는 것이 아니라, 특정 훈련구간에서 조종사의 탐색전략이 어떻게 바뀌었는지를 교관에게 보여주는 것입니다.
"@

    # 12. Interpretation limits
    $slide = New-BlankSlide 12
    Add-Title $slide "05  기술검증 결과" "현재 데이터가 말할 수 있는 범위를 지켜야 합니다" 12
    [void](Add-Rect $slide 48 130 400 318 $C.PaleMint $C.Green 5 1)
    [void](Add-Text $slide "알 수 있는 것" 75 151 330 30 21 $C.Green $true 1 3)
    [void](Add-Text $slide "✓ 데이터가 빠짐없이 기록됐는가\n✓ 어느 화면·AOI를 보았는가\n✓ 20 px 이후 언제 입력이 변했는가\n✓ 시선 전환과 고착이 어떻게 달랐는가\n✓ 결과를 원본 row로 추적할 수 있는가" 77 205 340 190 16 $C.Ink $false 1 1)
    [void](Add-Rect $slide 512 130 400 318 $C.PaleCoral $C.Coral 5 1)
    [void](Add-Text $slide "아직 알 수 없는 것" 539 151 330 30 21 $C.Coral $true 1 3)
    [void](Add-Text $slide "× 실제로 위협을 인지한 정확한 순간\n× 조작이 회피 목적이었는지 여부\n× 한 명의 점수로 표현되는 조종능력\n× 숙련·비숙련 집단의 우열\n× 마린온 시뮬레이터에서의 즉시 적용성" 541 205 340 190 16 $C.Ink $false 1 1)
    [void](Add-Text $slide "원칙: 측정한 것과 추론한 것을 분리하고, 교관 판단을 최종 단계에 둔다." 118 466 724 25 17 $C.Navy $true 2 3)
    Add-Notes $slide @"
[01:15 | 누적 13:50]
군 활용일수록 데이터의 한계를 명확히 해야 합니다. 현재 시스템은 기록 완전성, 시선 영역, 20 pixel 이후 입력변화, scan pattern과 원본 추적성을 보여줄 수 있습니다. 반면 실제 인지 순간, 조작 의도, 사람의 전체 조종능력, 숙련 집단 차이는 직접 측정하지 못했습니다. 그리고 X-Plane에서 작동했다고 마린온 시뮬레이터에 바로 적용되는 것도 아닙니다. 따라서 자동 산출값은 교관에게 질문과 근거를 제공하고, 최종 평가는 교관이 수행해야 합니다. 이 원칙이 있어야 데이터가 처벌이나 잘못된 서열화에 쓰이는 것을 막을 수 있습니다.
"@

    # 13. Military transfer architecture
    $slide = New-BlankSlide 13
    Add-Title $slide "06  군 적용 방안" "X-Plane이 아니라 ‘데이터 계약과 분석 구조’를 이식합니다" 13
    [void](Add-Tag $slide "현재 연구실" 45 122 118 $C.Blue)
    [void](Add-Tag $slide "군 적용 제안" 793 122 122 $C.Green)
    Add-CardTitleBody $slide "인가 시뮬레이터" "마린온·KUH 계열\n상태/입력/event export" 44 168 180 120 $C.Navy $C.PaleBlue 16 13
    [void](Add-Line $slide 224 228 274 228 $C.Navy 3 $true)
    Add-CardTitleBody $slide "Adapter" "기종별 ICD를\n공통 data contract로 변환" 274 168 180 120 $C.Blue $C.PaleBlue 16 13
    [void](Add-Line $slide 454 228 504 228 $C.Navy 3 $true)
    Add-CardTitleBody $slide "보안 분석 노드" "폐쇄망·on-premise\n동기화/AOI/event 분석" 504 168 180 120 $C.Green $C.PaleMint 16 13
    [void](Add-Line $slide 684 228 734 228 $C.Navy 3 $true)
    Add-CardTitleBody $slide "교관 화면" "timeline·scan·반응\nSOP 비교·annotation" 734 168 180 120 $C.Coral $C.PaleCoral 16 13
    [void](Add-Rect $slide 95 329 770 110 $C.Navy $C.Navy 5 0)
    [void](Add-Text $slide "유지되는 핵심" 123 347 150 24 17 $C.Cyan $true 1 3)
    [void](Add-Text $slide "event timeline  ·  원본 감사성  ·  AOI/반응 분석  ·  trial 품질 flag  ·  기준모델 비교" 122 381 710 26 17 $C.White $true 2 3)
    [void](Add-Text $slide "교체되는 부분" 123 414 118 18 12 (Convert-HexColor "BFD4E5") $true 1 3)
    [void](Add-Text $slide "X-Plane/FlyWithLua → 군 시뮬레이터 interface / Cessna AOI·geometry → 해당 기종·임무 기준" 248 414 590 18 12 (Convert-HexColor "BFD4E5") $false 1 3)
    Add-Notes $slide @"
[01:25 | 누적 15:15]
군 적용에서 X-Plane과 FlyWithLua를 부대에 반입하는 것이 목적이 아닙니다. 인가된 시뮬레이터가 항공기 상태, 조종 입력, 상황 event를 내보낼 수 있다면 기종별 adapter가 이를 공통 data contract로 변환합니다. 이후 보안구역 내부 분석 노드에서 시간동기, AOI와 반응 분석을 수행하고 교관 화면에 제공합니다. 유지되는 것은 event timeline, 원본 감사성, 품질 flag와 기준모델 비교 구조입니다. 바뀌어야 하는 것은 Cessna 계기 AOI, 고정익 조종 threshold, 침입기 geometry와 임무 시나리오입니다. 따라서 첫 단계는 코드를 설치하는 것이 아니라 시뮬레이터 ICD와 로그 export 범위를 확인하는 것입니다.
"@

    # 14. Use cases
    $slide = New-BlankSlide 14
    Add-Title $slide "06  군 적용 방안" "해병항공 임무에 맞춰 측정 질문을 다시 설계할 수 있습니다" 14 "아래는 현재 구현 완료가 아닌, 군 시뮬레이터 연계 후 검증할 적용 후보입니다."
    Add-CardTitleBody $slide "01  함상 이·착함 / 저시정" "외부 기준점–계기 scan 전환\n접근 안정성·go-around 판단" 46 137 410 140 $C.Blue $C.PaleBlue 17 14
    Add-CardTitleBody $slide "02  해상·저고도 공중기동" "지형·장애물·교통 탐색\n고도·속도 유지와 외부감시 배분" 504 137 410 140 $C.Cyan $C.PaleBlue 17 14
    Add-CardTitleBody $slide "03  야간·악천후·비상절차" "계기 scan 순서와 고착\n경고–체크리스트–조작 timeline" 46 304 410 140 $C.Amber $C.PaleAmber 17 14
    Add-CardTitleBody $slide "04  공중강습·신속대응" "임무정보·외부위협·비행경로 간\n주의배분과 crew coordination" 504 304 410 140 $C.Coral $C.PaleCoral 17 14
    [void](Add-Text $slide "공통 질문: ‘성공했는가?’ + ‘무엇을 보고, 어떤 순서로, 얼마나 일관되게 수행했는가?’" 73 463 814 26 16 $C.Navy $true 2 3)
    Add-Notes $slide @"
[01:25 | 누적 16:40]
적용 후보는 네 가지입니다. 함상 이착함이나 저시정 접근에서는 외부 기준점과 계기 scan 전환을 볼 수 있습니다. 해상·저고도 공중기동에서는 고도와 속도를 유지하면서 장애물과 교통을 얼마나 적절히 탐색했는지 분석할 수 있습니다. 야간·악천후·비상절차에서는 경고, 체크리스트 확인, 조작의 순서와 특정 계기 고착을 볼 수 있습니다. 공중강습과 신속대응에서는 임무정보, 외부위협, 비행경로 사이의 주의배분과 crew coordination을 후보로 둘 수 있습니다. 단, 이 네 가지는 현재 구현 완료가 아니라 기종과 SOP에 맞게 새로 검증해야 할 적용 시나리오입니다.
"@

    # 15. Debrief mockup
    $slide = New-BlankSlide 15
    Add-Title $slide "06  군 적용 방안" "교관에게 필요한 것은 ‘한 점수’가 아니라 설명 가능한 화면입니다" 15
    Add-KpiCard $slide "2.4 s" "관측기회→입력변화" 44 124 180 90 $C.Blue
    Add-KpiCard $slide "68%" "외부환경 attention" 238 124 180 90 $C.Green
    Add-KpiCard $slide "14" "AOI switches / min" 432 124 180 90 $C.Amber
    Add-KpiCard $slide "FLAG" "active control at anchor" 626 124 180 90 $C.Coral
    [void](Add-Rect $slide 44 237 762 95 $C.White $C.Line 5 1)
    [void](Add-Text $slide "EVENT TIMELINE" 62 248 160 18 11 $C.Slate $true 1 3)
    [void](Add-Line $slide 83 292 762 292 $C.Navy 3 $true)
    foreach ($item in @(@{X=150;T="경고";C=$C.Coral},@{X=300;T="계기확인";C=$C.Blue},@{X=455;T="외부탐색";C=$C.Green},@{X=620;T="조작";C=$C.Amber},@{X=735;T="안정";C=$C.Navy})) {
        [void](Add-CircleLabel $slide "" ($item.X - 8) 284 16 $item.C $C.White 8)
        [void](Add-Text $slide $item.T ($item.X - 36) 302 72 18 10 $C.Slate $true 2 3)
    }
    [void](Add-Rect $slide 44 350 490 116 $C.PaleBlue $C.Blue 5 1)
    [void](Add-Text $slide "교관 확인 질문" 64 365 160 22 16 $C.Blue $true 1 3)
    [void](Add-Text $slide "• 왜 20 px 이후에도 특정 계기에 머물렀는가?\n• SOP상 먼저 확인해야 할 정보가 누락됐는가?\n• 같은 상황을 다시 수행하면 scan이 개선되는가?" 66 397 442 58 13 $C.Ink $false 1 1)
    [void](Add-Rect $slide 554 350 352 116 $C.PaleMint $C.Green 5 1)
    [void](Add-Text $slide "개인 발전 추적" 575 365 150 22 16 $C.Green $true 1 3)
    [void](Add-Text $slide "동일 조종사 반복훈련에서\n반응·scan·절차 편차의 변화 확인" 575 401 300 48 14 $C.Ink $false 1 1)
    [void](Add-Tag $slide "개념 화면" 808 124 98 $C.Slate)
    Add-Notes $slide @"
[01:15 | 누적 17:55]
최종 산출물은 한 개의 AI 점수가 아니라 설명 가능한 디브리핑 화면이어야 합니다. 상단에는 반응시간, 외부 attention, 전환빈도와 품질 flag를 보여주고, 가운데에는 경고부터 계기확인, 외부탐색, 조작, 안정화까지 event timeline을 보여줍니다. 하단에는 교관이 확인할 질문과 반복훈련의 변화를 제시합니다. 특히 active control 같은 flag를 함께 보여야 짧은 반응시간을 무조건 좋은 값으로 오해하지 않습니다. 데이터는 교관의 질문을 더 정밀하게 하고, 동일 조종사의 재훈련 전후 변화를 추적하는 데 우선 활용하는 것이 안전합니다.
"@

    # 16. AI roadmap
    $slide = New-BlankSlide 16
    Add-Title $slide "07  발전 로드맵" "AI는 데이터 표준화 이후에 단계적으로 도입해야 합니다" 16
    $stages = @(
        @{X=46; N="0"; T="동기화 데이터"; B="상태·입력·시선·event\n품질 flag와 원본 감사"; C=$C.Slate},
        @{X=270; N="1"; T="SOP 규칙모델"; B="필수 scan·조작 순서\n교관 rubric 디지털화"; C=$C.Blue},
        @{X=494; N="2"; T="숙련 기준모델"; B="교관·숙련자 Matrix\n임무·조건별 baseline"; C=$C.Green},
        @{X=718; N="3"; T="개인화 AI"; B="반복 패턴·이상 편차\n다음 훈련 추천"; C=$C.Coral}
    )
    foreach ($s in $stages) {
        [void](Add-Rect $slide $s.X 155 196 220 $C.White $s.C 5 2)
        [void](Add-CircleLabel $slide $s.N ($s.X + 70) 125 56 $s.C $C.White 20)
        [void](Add-Text $slide $s.T ($s.X + 14) 203 168 30 18 $C.Navy $true 2 3)
        [void](Add-Text $slide $s.B ($s.X + 18) 252 160 76 14 $C.Ink $false 2 1)
        if ($s.X -lt 700) { [void](Add-Line $slide ($s.X + 196) 265 ($s.X + 220) 265 $C.Navy 2 $true) }
    }
    [void](Add-Rect $slide 112 414 736 54 $C.PaleAmber $C.Amber 5 1)
    [void](Add-Text $slide "단계별 검증 gate를 통과하기 전에는 다음 단계의 자동 판단을 운용하지 않음" 132 428 696 25 17 $C.Amber $true 2 3)
    Add-Notes $slide @"
[01:05 | 누적 19:00]
AI를 먼저 만들고 데이터를 끼워 맞추면 안 됩니다. 0단계는 동기화 데이터와 품질관리입니다. 1단계는 SOP와 교관 rubric을 규칙모델로 표현하는 것입니다. 2단계에서 충분한 숙련 조종사 데이터를 조건별 기준모델로 만들 수 있습니다. 그 다음에야 개인별 반복 패턴과 이상 편차를 찾아 다음 훈련을 추천하는 AI를 검토할 수 있습니다. 각 단계마다 교관 검증과 보안·윤리 gate를 통과해야 하며, 현재 연구는 0단계를 구현하고 1단계로 넘어가기 위한 기술 근거를 마련한 수준입니다.
"@

    # 17. Pilot proposal and close
    $slide = New-BlankSlide 17
    Add-Title $slide "08  제안" "12주 기술실증으로 ‘연결 가능성’부터 확인할 수 있습니다" 17
    $weeks = @(
        @{X=44; W="1–2주"; T="Interface 확인"; B="상태·입력·event export\n시간기준·ICD·보안경계"; C=$C.Blue},
        @{X=264; W="3–6주"; T="Adapter 제작"; B="공통 data contract\n폐쇄망 logger·audit"; C=$C.Cyan},
        @{X=484; W="7–9주"; T="시나리오·rubric"; B="교관과 AOI·event·SOP\n판정 기준 공동 설계"; C=$C.Green},
        @{X=704; W="10–12주"; T="소규모 검증"; B="기술 완전성·재현성\n교관 usefulness review"; C=$C.Coral}
    )
    foreach ($w in $weeks) {
        [void](Add-Rect $slide $w.X 144 196 160 $C.White $w.C 5 2)
        [void](Add-Tag $slide $w.W ($w.X + 18) 126 82 $w.C)
        [void](Add-Text $slide $w.T ($w.X + 14) 170 168 28 18 $C.Navy $true 2 3)
        [void](Add-Text $slide $w.B ($w.X + 15) 216 166 58 13 $C.Ink $false 2 1)
        if ($w.X -lt 700) { [void](Add-Line $slide ($w.X + 196) 224 ($w.X + 217) 224 $C.Navy 2 $true) }
    }
    [void](Add-Rect $slide 44 332 872 112 $C.Navy $C.Navy 5 0)
    [void](Add-Text $slide "워크숍 이후 필요한 세 가지" 70 350 260 24 17 $C.Cyan $true 1 3)
    [void](Add-Text $slide "① 시뮬레이터 data interface 확인   ② 교관·운용자 공동설계 인원 지정   ③ 비식별·비징계·연구활용 기준 합의" 72 390 816 28 16 $C.White $true 2 3)
    [void](Add-Text $slide "결론  |  조종능력 향상은 AI의 점수보다, 반복 가능한 데이터와 교관의 설명에서 시작됩니다." 80 462 800 28 18 $C.Green $true 2 3)
    Add-Notes $slide @"
[00:55 | 본 발표 약 19:55]
처음부터 대규모 AI 사업으로 시작할 필요는 없습니다. 12주 기술실증으로 충분합니다. 먼저 시뮬레이터가 어떤 데이터를 어떤 시간기준으로 내보낼 수 있는지 확인하고, 공통 adapter와 폐쇄망 logger를 만듭니다. 다음으로 교관과 함께 임무별 AOI, event와 SOP rubric을 설계한 뒤 소규모로 기록 완전성과 재현성, 디브리핑 유용성을 검증합니다. 워크숍 이후 필요한 것은 interface 확인, 공동설계 인원 지정, 그리고 비식별·비징계·연구활용 기준 합의입니다. 결론적으로 조종능력 향상은 AI가 매긴 점수에서 시작되는 것이 아니라, 반복 가능한 데이터와 교관의 설명에서 시작됩니다. 감사합니다.
"@

    # 18. Appendix: definitions
    $slide = New-BlankSlide 18
    Add-Title $slide "APPENDIX" "핵심 용어와 분석 단위" 18
    Add-CardTitleBody $slide "Scenario exposure" "INTRUDER_SPAWNED\n침입기가 실제 생성된 객관적 시점" 52 138 260 120 $C.Coral $C.PaleCoral 16 13
    Add-CardTitleBody $slide "Visual opportunity" "화면상 침입기 크기 20 px\n비교 가능한 geometry anchor" 350 138 260 120 $C.Amber $C.PaleAmber 16 13
    Add-CardTitleBody $slide "Operational response" "pre-anchor baseline 대비\n지속적인 control input 변화" 648 138 260 120 $C.Green $C.PaleMint 16 13
    Add-CardTitleBody $slide "Observation bin" "고정 200 ms 동안의 dominant AOI\n검증된 fixation은 아님" 52 300 260 120 $C.Blue $C.PaleBlue 16 13
    Add-CardTitleBody $slide "DTMC" "현재 AOI 행 → 다음 AOI 열\n전이확률; row sum = 1" 350 300 260 120 $C.Blue $C.PaleBlue 16 13
    Add-CardTitleBody $slide "Quality flags" "speed timeout·active control·invalid gaze\n삭제 대신 명시적으로 보존" 648 300 260 120 $C.Slate $C.White 16 13
    Add-Notes $slide "[부록] 질의응답 시 용어 정의를 설명하는 슬라이드입니다. 핵심은 visual opportunity가 recognition이 아니고, 200 ms bin이 fixation이 아니라는 점입니다."

    # 19. Appendix: governance
    $slide = New-BlankSlide 19
    Add-Title $slide "APPENDIX" "군 적용 시 데이터 거버넌스 원칙" 19
    $gov = @(
        @{Y=135; N="01"; T="폐쇄망·on-premise"; B="원본 비행·시선 데이터는 인가된 구역 밖으로 반출하지 않음"; C=$C.Navy},
        @{Y=205; N="02"; T="비식별·최소수집"; B="발표·연구 export는 participant code와 필요한 지표만 사용"; C=$C.Blue},
        @{Y=275; N="03"; T="교관 승인"; B="AI 또는 규칙 flag는 설명자료이며 최종 평가는 교관이 확인"; C=$C.Green},
        @{Y=345; N="04"; T="비징계 목적"; B="초기 적용은 개인 발전·훈련개선에 한정하고 인사평가와 분리"; C=$C.Coral},
        @{Y=415; N="05"; T="연구·출판 별도 심의"; B="IRB/면제, 보안성 검토, 자료보존·폐기 규칙을 사전에 확정"; C=$C.Amber}
    )
    foreach ($g in $gov) {
        [void](Add-CircleLabel $slide $g.N 62 ($g.Y - 2) 42 $g.C $C.White 12)
        [void](Add-Text $slide $g.T 125 $g.Y 190 30 17 $C.Navy $true 1 3)
        [void](Add-Text $slide $g.B 320 $g.Y 570 30 14 $C.Ink $false 1 3)
        [void](Add-Line $slide 124 ($g.Y + 39) 890 ($g.Y + 39) $C.Line 1)
    }
    Add-Notes $slide "[부록] 기술보다 먼저 합의해야 할 운용원칙입니다. 초기 도입은 개인 발전과 교관 보조 목적에 한정하고, 연구·출판은 별도의 IRB와 보안심의를 거쳐야 합니다."

    # 20. Appendix: references
    $slide = New-BlankSlide 20
    Add-Title $slide "APPENDIX" "주요 근거자료" 20
    [void](Add-Text $slide "연구 근거" 54 126 180 28 18 $C.Blue $true 1 3)
    [void](Add-Text $slide "• Kim, S. and Lee, H., APISAT-2026 Full Paper merged manuscript, 2026.\n• AISimulationProject v39 technical-feasibility dataset and AOI/DTMC audit outputs." 66 166 820 62 14 $C.Ink $false 1 1)
    [void](Add-Text $slide "공식 공개자료" 54 250 180 28 18 $C.Green $true 1 3)
    [void](Add-Text $slide "• 방위사업청, ‘해병대 하늘로 비상하다’, 2023.06.29.\n  https://www.dapa.go.kr/dapa/doc/selectDoc.do?bbsSeq=326&docSeq=17266&menuSeq=3069\n• KAI, MUH-1 Marine Utility Helicopter.\n  https://www.koreaaero.com/EN/Business/MUH1.aspx\n• KAI, 훈련체계 — MUH 시뮬레이터.\n  https://m.koreaaero.com/KO/Business/TrainingSystem.aspx\n• 방위사업청, ‘수리온 비행훈련시뮬레이터 실전 배치’, 2026.07.29.\n  https://www.dapa.go.kr/dapa/doc/selectDoc.do?bbsSeq=326&docSeq=58786&menuSeq=3069" 66 288 840 168 12 $C.Ink $false 1 1)
    [void](Add-Rect $slide 54 466 852 28 $C.PaleAmber $C.Amber 5 1)
    [void](Add-Text $slide "군 적용 시나리오와 12주 실증안은 위 공개자료와 현재 프레임워크를 바탕으로 한 발표자 제안입니다." 68 472 824 16 10 $C.Amber $true 2 3)
    Add-Notes $slide "[부록] 발표에서 사용한 연구·공식 공개자료입니다. 군 적용 시나리오와 일정은 공개자료에 적힌 완료 사실이 아니라 발표자의 후속 실증 제안임을 구분합니다."

    # PowerPoint COM is substantially faster when text boxes are created first
    # and paragraph breaks are normalized in one pass immediately before save.
    $normalizedTextShapes = 0
    foreach ($builtSlide in @($presentation.Slides)) {
        foreach ($builtShape in @($builtSlide.Shapes)) {
            try {
                if ($builtShape.HasTextFrame -and $builtShape.TextFrame2.HasText) {
                    $builtText = $builtShape.TextFrame2.TextRange.Text
                    if ($builtText.Contains('\n')) {
                        $builtShape.TextFrame2.TextRange.Text = $builtText.Replace('\n', "`r")
                        $normalizedTextShapes++
                    }
                }
            }
            finally {
                [void][Runtime.InteropServices.Marshal]::ReleaseComObject($builtShape)
            }
        }
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($builtSlide)
    }
    Write-Output "Normalized text shapes: $normalizedTextShapes"

    if (Test-Path -LiteralPath $outputFull) { [System.IO.File]::Delete($outputFull) }
    if (Test-Path -LiteralPath $pdfFull) { [System.IO.File]::Delete($pdfFull) }
    $presentation.SaveAs($outputFull, 24)
    $presentation.SaveAs($pdfFull, 32)

    Write-Output "PPTX: $outputFull"
    Write-Output "PDF : $pdfFull"
    Write-Output "Slides: $($presentation.Slides.Count)"
}
finally {
    if ($presentation -ne $null) {
        try { $presentation.Close() } catch {}
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($presentation)
    }
    if ($ppt -ne $null) {
        try { $ppt.Quit() } catch {}
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($ppt)
    }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}
