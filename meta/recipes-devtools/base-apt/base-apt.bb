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

# XXX: Should be obtaibed from buildchroot, image and packages recipes.
BASE_PREINSTALL ?= "gcc \
                    make \
                    build-essential \
                    debhelper \
                    autotools-dev \
                    dpkg \
                    locales \
                    docbook-to-man \
                    apt \
                    automake \
                    dbus \
                    localepurge \
                    gdb \
                    strace \
                   "
BASE_PREINSTALL += "${IMAGE_PREINSTALL}"

BASE_CACHE_CONF_DIR = "${BASE_DIR_APT}/conf"
do_base_cache[dirs] = "${BASE_CACHE_CONF_DIR}"
do_base_cache[stamp-extra-info] = "${DISTRO}-${DISTRO_ARCH}"

do_base_cache() {
    PACKAGES=$(echo ${BASE_PREINSTALL} | xargs | sed 's/ /,/g')

    sudo debootstrap \
        --arch ${DISTRO_ARCH} \
        --download-only \
        --include=$PACKAGES \
        ${DISTRO_SUITE} \
        ${WORKDIR}/download \
        ${DISTRO_APT_SOURCE}

    if [ ! -e "${BASE_CACHE_CONF_DIR}/distributions" ]; then
        sed -e "s#{DISTRO_NAME}#"${DISTRO_SUITE}"#g" \
            ${WORKDIR}/distributions.in > ${BASE_CACHE_CONF_DIR}/distributions
    fi

    path_cache="${BASE_DIR_APT}"
    path_databases="${BASE_DIR_DB}"

    if [ ! -d "${path_databases}" ]; then
        reprepro -b ${path_cache} \
                 --dbdir ${path_databases} \
                 export ${DISTRO_SUITE}
    fi

    reprepro -b ${BASE_DIR_APT} \
             --dbdir ${BASE_DIR_DB} \
             -C main \
             includedeb ${DISTRO_SUITE} \
             ${WORKDIR}/download/var/cache/apt/archives/*.deb

    sudo rm -rf ${WORKDIR}/download
}

addtask base_cache after do_unpack before do_build
