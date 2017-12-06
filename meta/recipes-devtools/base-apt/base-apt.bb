# Caching upstream apt repository to local one.
#
# This software is a part of ISAR.
# Copyright (C) 2015-2017 ilbers GmbH

DESCRIPTION = "Upstream apt caching"

LICENSE = "gpl-2.0"
LIC_FILES_CHKSUM = "file://${LAYERDIR_isar}/licenses/COPYING.GPLv2;md5=751419260aa954499f7abaabaa882bbe"

FILESPATH =. "${LAYERDIR_core}/recipes-devtools/base-apt/files:"
SRC_URI = "file://distributions.in"

WORKDIR = "${TMPDIR}/work/${DISTRO}-${DISTRO_ARCH}/${PN}"

BASE_APT_CONF_DIR = "${BASE_APT_DIR}/apt/conf"
do_get_base_apt[dirs] = "${BASE_APT_CONF_DIR}"
do_get_base_apt[stamp-extra-info] = "${DISTRO}-${DISTRO_ARCH}"
DEPENDS = "buildchroot ${IMAGE_INSTALL}"
do_get_base_apt[deptask] = "do_get_deps"

do_get_base_apt() {
    for package in `ls ${WORKDIR}/deps`; do
        DEPS=$(cat ${WORKDIR}/deps/$package | xargs)
        PACKAGES="$PACKAGES $DEPS"
    done

    PACKAGES=$(echo ${PACKAGES} | sed 's/ /,/g')
    if [ -n  "$PACKAGES" ]; then
        PACKAGES="--include=$PACKAGES"
    fi

    sudo debootstrap \
        --components=main,contrib,non-free,firmware \
        --no-check-gpg \
        --arch ${DISTRO_ARCH} \
        --download-only \
        $PACKAGES \
        ${DISTRO_SUITE} \
        ${WORKDIR}/download \
        ${DISTRO_APT_SOURCE}

    if [ ! -e "${BASE_APT_CONF_DIR}/distributions" ]; then
        sed -e "s#{DISTRO_NAME}#"${DISTRO_SUITE}"#g" \
            ${WORKDIR}/distributions.in > ${BASE_APT_CONF_DIR}/distributions
    fi

    if [ ! -d "${BASE_APT_DIR}/apt" ]; then
        reprepro -b ${BASE_APT_DIR}/apt \
                 --dbdir ${BASE_APT_DIR}/db \
                 export ${DISTRO_SUITE}
    fi

    reprepro -b ${BASE_APT_DIR}/apt \
             --dbdir ${BASE_APT_DIR}/db \
             -C main \
             includedeb ${DISTRO_SUITE} \
             ${WORKDIR}/download/var/cache/apt/archives/*.deb

    sudo rm -rf ${WORKDIR}/download
}

addtask get_base_apt after do_unpack before do_build
