# Maintainer: Joinsider <public@joinside.de>
pkgname=dhbw-next
pkgver=1.1.0
pkgrel=1
pkgdesc='DHBW Horb Studenten App - Desktop application for DHBW Stuttgart students'
arch=('x86_64' 'aarch64')
url='https://github.com/Joinsider/dhbw-next'
license=('unknown')
depends=('java-runtime>=21' 'hicolor-icon-theme')
makedepends=('java-environment=21' 'gradle')
source=("${pkgname}-${pkgver}.tar.gz::${url}/archive/refs/tags/v${pkgver}.tar.gz")
sha256sums=('SKIP')

prepare() {
    cd "${srcdir}/${pkgname}-${pkgver}"
    chmod +x gradlew
}

build() {
    cd "${srcdir}/${pkgname}-${pkgver}"

    # Set Java environment
    export JAVA_HOME=/usr/lib/jvm/java-11-openjdk
    export PATH="${JAVA_HOME}/bin:${PATH}"

    # Build the desktop JAR (JVM target is architecture-independent)
    ./gradlew :composeApp:desktopJar --no-daemon --stacktrace
}

package() {
    cd "${srcdir}/${pkgname}-${pkgver}"

    # Find the generated JAR in desktop build output
    local jarfile="composeApp/build/libs/composeApp-desktop.jar"

    if [ ! -f "${jarfile}" ]; then
        # Try alternative location
        jarfile=$(find composeApp/build/libs -name "*desktop*.jar" -type f | head -n1)
    fi

    if [ -z "${jarfile}" ] || [ ! -f "${jarfile}" ]; then
        echo "Error: Could not find generated JAR file"
        ls -la composeApp/build/libs/ || true
        return 1
    fi

    # Install JAR
    install -Dm644 "${jarfile}" "${pkgdir}/usr/share/java/${pkgname}/${pkgname}.jar"

    # Copy all dependencies
    if [ -d "composeApp/build/libs" ]; then
        cp -r composeApp/build/libs/* "${pkgdir}/usr/share/java/${pkgname}/" || true
    fi

    # Create launcher script with classpath
    install -Dm755 /dev/stdin "${pkgdir}/usr/bin/${pkgname}" <<EOF
#!/bin/sh
exec java -cp "/usr/share/java/${pkgname}/*" de.joinside.dhbw.MainKt "\$@"
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
