$ErrorActionPreference = "Continue"
$SDK = "D:\node_global\kafatool\android\sdk"
$JDK = "D:\node_global\kafatool\android\jdk"
$BT = "$SDK\build-tools\35.0.0"
$dir = $PSScriptRoot
$out = "$dir\build"

New-Item -ItemType Directory -Path "$out\classes" -Force | Out-Null

Write-Output "== javac =="
Get-ChildItem "$out\classes" -Recurse -Filter "*.class" -ErrorAction SilentlyContinue | Remove-Item -Force
$srcs = @(Get-ChildItem "$dir\src\com\wusun\speaker\*.java" | ForEach-Object { $_.FullName })
& "$JDK\bin\javac.exe" --release 8 -encoding UTF-8 -d "$out\classes" -classpath "$SDK\platforms\android-35\android.jar" $srcs 2>&1 | Where-Object { $_ -match "error" }
if (-not (Test-Path "$out\classes\com\wusun\speaker\MainActivity.class")) { Write-Output "JAVAC FAILED"; exit 1 }

Write-Output "== d8 =="
$classes = @(Get-ChildItem "$out\classes\com\wusun\speaker\*.class" | ForEach-Object { $_.FullName })
& "$BT\d8.bat" --lib "$SDK\platforms\android-35\android.jar" --output "$out" $classes
if (-not (Test-Path "$out\classes.dex")) { Write-Output "D8 FAILED"; exit 1 }

Write-Output "== aapt2 compile res =="
$resFiles = @(Get-ChildItem "$dir\res" -Recurse -File | ForEach-Object { $_.FullName })
& "$BT\aapt2.exe" compile -o "$out\res.zip" $resFiles
if (-not (Test-Path "$out\res.zip")) { Write-Output "AAPT2 COMPILE FAILED"; exit 1 }

Write-Output "== aapt2 link =="
& "$BT\aapt2.exe" link -o "$out\base.apk" -I "$SDK\platforms\android-35\android.jar" --manifest "$dir\AndroidManifest.xml" --min-sdk-version 26 --target-sdk-version 35 --version-code 1 --version-name 1.0 "$out\res.zip"
if (-not $?) { exit 1 }

Write-Output "== add dex =="
Push-Location $out
& "$BT\aapt.exe" add base.apk classes.dex
Pop-Location
if (-not $?) { exit 1 }

Write-Output "== zipalign =="
& "$BT\zipalign.exe" -f 4 "$out\base.apk" "$out\aligned.apk"
if (-not $?) { exit 1 }

Write-Output "== keytool =="
if (-not (Test-Path "$out\speaker.keystore")) {
    & "$JDK\bin\keytool.exe" -genkeypair -keystore "$out\speaker.keystore" -alias speaker -storepass speaker123 -keypass speaker123 -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=LosslessSpeaker"
}

Write-Output "== apksigner =="
& "$BT\apksigner.bat" sign --ks "$out\speaker.keystore" --ks-pass pass:speaker123 --key-pass pass:speaker123 --out "$out\speaker.apk" "$out\aligned.apk"
if (-not $?) { exit 1 }

& "$BT\apksigner.bat" verify "$out\speaker.apk"
Get-Item "$out\speaker.apk" | Select-Object FullName, Length
Write-Output "BUILD OK"
