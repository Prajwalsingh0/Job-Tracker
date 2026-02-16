@echo off
echo Node Version: > env.txt
node -v >> env.txt
echo NPM Version: >> env.txt
call npm -v >> env.txt
echo PNPM Version: >> env.txt
call pnpm -v >> env.txt 2>&1
if %errorlevel% neq 0 echo PNPM not found >> env.txt
