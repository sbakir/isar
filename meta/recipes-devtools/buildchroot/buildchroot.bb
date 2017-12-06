# Root filesystem for packages building
#
# This software is a part of ISAR.
# Copyright (C) 2015-2016 ilbers GmbH

DESCRIPTION = "Multistrap development filesystem"

LICENSE = "gpl-2.0"
LIC_FILES_CHKSUM = "file://${LAYERDIR_isar}/licenses/COPYING.GPLv2;md5=751419260aa954499f7abaabaa882bbe"

FILESPATH =. "${LAYERDIR_core}/recipes-devtools/buildchroot/files:"
SRC_URI = "file://multistrap.conf.in \
           file://configscript.sh \
           file://setup.sh \
           file://download_dev-random \
           file://build.sh"
PV = "1.0"

BUILDCHROOT_PREINSTALL ?= "gcc \
                           make \
                           build-essential \
                           debhelper \
                           autotools-dev \
                           dpkg \
                           locales \
                           docbook-to-man \
                           apt \
                           automake"

WORKDIR = "${TMPDIR}/work/${DISTRO}-${DISTRO_ARCH}/${PN}"

do_build[stamp-extra-info] = "${DISTRO}-${DISTRO_ARCH}"
do_build[dirs] = "${WORKDIR}/hooks_multistrap"
do_build[depends] = "base-apt:do_get_base_apt"

do_build() {
    chmod +x "${WORKDIR}/setup.sh"
    chmod +x "${WORKDIR}/configscript.sh"
    install -m 755 "${WORKDIR}/download_dev-random" "${WORKDIR}/hooks_multistrap/"

    # Multistrap accepts only relative path in configuration files, so get it:
    cd ${TOPDIR}
    WORKDIR_REL=${@ os.path.relpath(d.getVar("WORKDIR", True))}

    # Adjust multistrap config
    sed -e 's|##BUILDCHROOT_PREINSTALL##|${BUILDCHROOT_PREINSTALL}|g' \
        -e 's|##DISTRO##|${DISTRO}|g' \
        -e 's|##DISTRO_APT_SOURCE##|copy:///${BASE_APT_DIR}/apt|g' \
        -e 's|##DISTRO_SUITE##|${DISTRO_SUITE}|g' \
        -e 's|##DISTRO_COMPONENTS##|${DISTRO_COMPONENTS}|g' \
        -e 's|##CONFIG_SCRIPT##|./'"$WORKDIR_REL"'/configscript.sh|g' \
        -e 's|##SETUP_SCRIPT##|./'"$WORKDIR_REL"'/setup.sh|g' \
        -e 's|##DIR_HOOKS##|./'"$WORKDIR_REL"'/hooks_multistrap|g' \
           "${WORKDIR}/multistrap.conf.in" > "${WORKDIR}/multistrap.conf"

    [ ! -d ${BUILDCHROOT_DIR}/proc ] && install -d -m 555 ${BUILDCHROOT_DIR}/proc
    sudo mount -t proc none ${BUILDCHROOT_DIR}/proc
    _do_build_cleanup() {
        ret=$?
        sudo umount ${BUILDCHROOT_DIR}/proc 2>/dev/null || true
        sudo umount ${BUILDCHROOT_DIR}/apt || true
        (exit $ret) || bb_exit_handler
    }
    trap '_do_build_cleanup' EXIT

    # Create root filesystem
    sudo multistrap -a ${DISTRO_ARCH} -d "${BUILDCHROOT_DIR}" -f "${WORKDIR}/multistrap.conf"

    # Install package builder script
    sudo install -m 755 ${WORKDIR}/build.sh ${BUILDCHROOT_DIR}

    # Create share point for downloads
    sudo install -d ${BUILDCHROOT_DIR}/git

    # Create share point for apt
    sudo install -d ${BUILDCHROOT_DIR}/apt
    sudo mount --bind ${BASE_APT_DIR}/apt ${BUILDCHROOT_DIR}/apt

    # Configure root filesystem
    sudo chroot ${BUILDCHROOT_DIR} /configscript.sh
    _do_build_cleanup
}

do_prepare[nostamp] = "1"

do_prepare() {
    sudo mount --bind ${BASE_APT_DIR} ${BUILDCHROOT_DIR}/apt
    sudo mount --bind ${GITDIR} ${BUILDCHROOT_DIR}/git
}

addtask prepare after do_build

DEPENDS += "${IMAGE_INSTALL}"
do_cleanup[deptask] = "do_deploy_deb"
do_cleanup[nostamp] = "1"

do_cleanup() {
    sudo umount ${BUILDCHROOT_DIR}/apt
    sudo umount ${BUILDCHROOT_DIR}/git
}

addtask cleanup after do_prepare

# Share buildchroot extra packages with base-apt
do_get_deps[stamp-extra-info] = "${DISTRO}-${DISTRO_ARCH}"

do_get_deps() {
    echo ${BUILDCHROOT_PREINSTALL} > ${BASE_APT_DIR}/deps/${PN}
}

addtask get_deps after unpack before build
