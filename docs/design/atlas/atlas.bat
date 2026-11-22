@echo off
rem Opens the Glyph Pet atlas in the browser through a little local server (keep this window open while you look).
chcp 65001 >nul
cd /d "%~dp0"
set PORT=8765
start "" "http://127.0.0.1:%PORT%/index.html"
where python >nul 2>nul && (python -m http.server %PORT% --bind 127.0.0.1 & goto :eof)
where py >nul 2>nul && (py -m http.server %PORT% --bind 127.0.0.1 & goto :eof)
echo Python not found: install it from python.org, then run this again.
pause
