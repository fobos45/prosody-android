#!/bin/bash
set -e

NDK=$ANDROID_NDK_HOME
TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64
CC=$TOOLCHAIN/bin/aarch64-linux-android24-clang
CXX=$TOOLCHAIN/bin/aarch64-linux-android24-clang++
AR=$TOOLCHAIN/bin/llvm-ar
RANLIB=$TOOLCHAIN/bin/llvm-ranlib
STRIP=$TOOLCHAIN/bin/llvm-strip

LUA_DIR=/tmp/lua-android
OUT=/tmp/prosody-out
PROSODY_VER=0.12.4

mkdir -p $OUT/lib $OUT/share/prosody

# ── 1. luafilesystem ─────────────────────────────────────────────────────────
cd /tmp
git clone -q --depth=1 --branch v1_8_0 https://github.com/lunarmodules/luafilesystem
cd luafilesystem
$CC -shared -fPIC -o $OUT/lib/lfs.so src/lfs.c \
  -I$LUA_DIR/include -L$LUA_DIR/lib -llua \
  -landroid -llog
echo "lfs.so built"

# ── 2. libexpat (static, for luaexpat) ───────────────────────────────────────
cd /tmp
wget -q https://github.com/libexpat/libexpat/releases/download/R_2_6_2/expat-2.6.2.tar.gz
tar xf expat-2.6.2.tar.gz
cd expat-2.6.2
CC=$CC AR=$AR RANLIB=$RANLIB \
  ./configure --host=aarch64-linux-android --prefix=/tmp/expat-out \
  --disable-shared --enable-static --without-xmlwf 2>/dev/null
make -j4 install 2>&1 | tail -5
echo "libexpat built"

# ── 3. luaexpat ─────────────────────────────────────────────────────────────
cd /tmp
git clone -q --depth=1 --branch v1.5.0 https://github.com/lunarmodules/luaexpat
cd luaexpat
$CC -shared -fPIC -o $OUT/lib/lxp.so src/lxplib.c \
  -I$LUA_DIR/include -I/tmp/expat-out/include \
  -L$LUA_DIR/lib -L/tmp/expat-out/lib \
  -llua -lexpat
echo "lxp.so built"

# ── 4. luasocket ─────────────────────────────────────────────────────────────
cd /tmp
git clone -q --depth=1 --branch v3.1.0 https://github.com/lunarmodules/luasocket
cd luasocket
$CC -shared -fPIC -o $OUT/lib/socket.core.so \
  src/luasocket.c src/timeout.c src/buffer.c src/io.c \
  src/auxiliar.c src/options.c src/inet.c src/tcp.c \
  src/udp.c src/select.c src/usocket.c \
  -I$LUA_DIR/include -L$LUA_DIR/lib -llua -DLUASOCKET_API= \
  -DUNIX_HAS_SUN_LEN
$CC -shared -fPIC -o $OUT/lib/mime.core.so src/mime.c \
  -I$LUA_DIR/include -L$LUA_DIR/lib -llua
echo "luasocket built"

# ── 5. Prosody (pure Lua) ────────────────────────────────────────────────────
cd /tmp
wget -q https://prosody.im/downloads/source/prosody-${PROSODY_VER}.tar.gz
tar xf prosody-${PROSODY_VER}.tar.gz
cp -r prosody-${PROSODY_VER}/* $OUT/share/prosody/
echo "Prosody Lua files copied"

# ── 6. Copy to Android assets ────────────────────────────────────────────────
ASSETS=$GITHUB_WORKSPACE/app/src/main/assets
mkdir -p $ASSETS/native/arm64-v8a $ASSETS/prosody

cp $LUA_DIR/bin/lua $ASSETS/native/arm64-v8a/lua
$STRIP $ASSETS/native/arm64-v8a/lua

for so in lfs.so lxp.so socket.core.so mime.core.so; do
  cp $OUT/lib/$so $ASSETS/native/arm64-v8a/
  $STRIP $ASSETS/native/arm64-v8a/$so
done

cp -r $OUT/share/prosody/* $ASSETS/prosody/

echo "=== Assets ready ==="
find $ASSETS -name "*.so" -o -name "lua" | head -20
