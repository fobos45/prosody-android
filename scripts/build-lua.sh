#!/bin/bash
set -e

NDK=$ANDROID_NDK_HOME
TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64
CC=$TOOLCHAIN/bin/aarch64-linux-android24-clang
AR=$TOOLCHAIN/bin/llvm-ar
RANLIB=$TOOLCHAIN/bin/llvm-ranlib

LUA_VER=5.4.7
OUT=/tmp/lua-android

mkdir -p $OUT
cd /tmp
wget -q https://www.lua.org/ftp/lua-${LUA_VER}.tar.gz
tar xf lua-${LUA_VER}.tar.gz
cd lua-${LUA_VER}

# Patch Makefile for Android
sed -i 's|CC= gcc|CC= '"$CC"'|' src/Makefile
sed -i 's|AR= ar|AR= '"$AR"'|' src/Makefile
sed -i 's|RANLIB= ranlib|RANLIB= '"$RANLIB"'|' src/Makefile
sed -i 's|MYCFLAGS=|MYCFLAGS= -DLUA_USE_LINUX -fPIC|' src/Makefile

make -C src liblua.a lua luac 2>&1

mkdir -p $OUT/bin $OUT/lib $OUT/include
cp src/lua $OUT/bin/
cp src/luac $OUT/bin/
cp src/liblua.a $OUT/lib/
cp src/*.h $OUT/include/

echo "Lua built: $OUT"
ls -la $OUT/bin/
