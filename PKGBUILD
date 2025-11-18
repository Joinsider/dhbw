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

    export JAVA_HOME=/usr/lib/jvm/java-11-openjdk
    export PATH="${JAVA_HOME}/bin:${PATH}"

    # Build using custom fat JAR task
    ./gradlew :composeApp:packageFatJar --no-daemon --stacktrace
}

package() {
    cd "${srcdir}/${pkgname}-${pkgver}"

    local jarfile="composeApp/build/libs/dhbw-next-*.jar"

    if [ ! -f "${jarfile}" ]; then
        echo "Error: Could not find generated JAR file"
        ls -la composeApp/build/libs/
        return 1
    fi

    # Install JAR
    install -Dm644 "${jarfile}" "${pkgdir}/usr/share/java/${pkgname}/${pkgname}.jar"

    # Create launcher script
    install -Dm755 /dev/stdin "${pkgdir}/usr/bin/${pkgname}" <<EOF
#!/bin/sh
exec java -jar /usr/share/java/${pkgname}/${pkgname}.jar "\$@"
EOF

    # Install desktop file
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
