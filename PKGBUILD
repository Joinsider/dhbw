# Maintainer: Joinsider <public@joinside.de>
pkgname=dhbw-next
pkgver=1.0.4
pkgrel=1
pkgdesc='DHBW Horb Studenten App - Desktop application for DHBW Stuttgart students'
arch=('x86_64' 'aarch64')
url='https://github.com/Joinsider/dhbw-next'
license=('unknown')
depends=('java-runtime>=21' 'hicolor-icon-theme')
makedepends=('java-environment=21' 'jdk21-openjdk')
source=("${pkgname}-${pkgver}.tar.gz::${url}/archive/refs/tags/v${pkgver}.tar.gz")
sha256sums=('SKIP')

prepare() {
    cd "${srcdir}/${pkgname}-${pkgver}"
    chmod +x gradlew
}

build() {
    cd "${srcdir}/${pkgname}-${pkgver}"

    # These exports are good to keep, even if Gradle picks up system defaults
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
    export PATH="${JAVA_HOME}/bin:${PATH}"

    # Build using the new release distributable task
    ./gradlew :composeApp:createReleaseDistributable --no-daemon --stacktrace
}


package() {
    cd "${srcdir}/${pkgname}-${pkgver}"

    local app_dir="composeApp/build/compose/binaries/main-release/DHBW Horb Studenten App"
    local launcher_script="${app_dir}/${pkgname}"
    local jar_directory="${app_dir}/libs"
    local main_jar=$(find "${jar_directory}" -maxdepth 1 -name "*.jar" | head -n 1)

    if [ ! -f "${launcher_script}" ] || [ -z "${main_jar}" ]; then
        echo "Error: Could not find generated application files or JAR."
        ls -R composeApp/build/compose/binaries/
        return 1
    fi

    # Install the entire directory structure into /usr/share/java/dhbw-next/
    install -d "${pkgdir}/usr/share/java/${pkgname}/"
    cp -r "${app_dir}/." "${pkgdir}/usr/share/java/${pkgname}/"

    # The existing launcher script provided by Gradle needs adjustment for standard Linux paths
    # We will create a symlink to the generated launcher script in /usr/bin

    ln -s "/usr/share/java/${pkgname}/${pkgname}" "${pkgdir}/usr/bin/${pkgname}"

    # Install desktop file (no changes needed here from original)
    install -Dm644 /dev/stdin "${pkgdir}/usr/share/applications/${pkgname}.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=DHBW Horb Studenten App
Comment=${pkgdesc}
Exec=${pkgname}
Icon=${pkgname}
Categories=Education;Utility;
Terminal=false
StartupWMClass=de-joinside-dhbw-MainKt
EOF

    # Install icon
    if [ -f "composeApp/icon.png" ]; then
        install -Dm644 "composeApp/icon.png" \
            "${pkgdir}/usr/share/pixmaps/${pkgname}.png"
    fi

    # Install license
    if [ -f "LICENSE" ]; then
        install -Dm644 LICENSE "${pkgdir}/usr/share/licenses/${pkgname}/LICENSE"
    fi
}
