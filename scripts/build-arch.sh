#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ $(uname -m) != x86_64 ]]; then
    echo 'This package currently targets Arch Linux x86_64.' >&2
    exit 1
fi
bash ./gradlew -PdesktopOnly=true :desktop:createDistributable --console=plain
version=$(sed -n 's/^pkgver=//p' packaging/arch/PKGBUILD)
staging=$(mktemp -d)
trap 'rm -r -- "$staging"' EXIT
cp -a desktop/build/compose/binaries/main/app/youschool "$staging/youschool"
cp packaging/arch/start-youschool "$staging/youschool/start-youschool"
cp packaging/arch/youschool.desktop packaging/arch/youschool.svg "$staging/"
mkdir -p desktop/build/arch
archive="youschool_${version}_linux_x86_64.tar.gz"
tar -C "$staging" -czf "desktop/build/arch/$archive" youschool youschool.desktop youschool.svg
cp packaging/arch/PKGBUILD desktop/build/arch/PKGBUILD
archive_hash=$(sha256sum "desktop/build/arch/$archive" | cut -d ' ' -f1)
sed -i "s/sha256sums=('SKIP').*/sha256sums=('$archive_hash')/" desktop/build/arch/PKGBUILD
(cd desktop/build/arch && makepkg --force --cleanbuild --clean --noconfirm)
