@echo off
chcp 936 >nul
title 词岛源码 - 一键上传
cd /d "%~dp0"
echo ========================================
echo    词岛源码  一键上传到 GitHub
echo ========================================
echo.

git add -A
git -c user.name=liuxiansheng12369 -c user.email=877065045@qq.com commit -m "更新 %date% %time%"
if errorlevel 1 (echo [1/2] 没有新改动，直接推送) else (echo [1/2] 已生成本地提交)

set tries=0
:push
echo [2/2] 正在推送到 GitHub ...
git push
if not errorlevel 1 goto ok
set /a tries=%tries%+1
if %tries% geq 3 goto fail
echo     网络抖动，3 秒后重试 ^(%tries%/3^)...
timeout /t 3 >nul
goto push

:ok
echo.
echo  上传完成  https://github.com/liuxiansheng12369/lyricon
echo.
if /i "%~1"=="nopause" exit /b 0
pause
exit /b 0

:fail
echo.
echo  推送失败
echo  请确认 Clash Verge 正在运行（代理 127.0.0.1:7897），再双击一次本脚本。
echo.
if /i "%~1"=="nopause" exit /b 1
pause
exit /b 1