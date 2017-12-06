# This software is a part of ISAR.
# Copyright (C) 2017 Siemens AG

# Add dependency from buildchroot creation
do_build[depends] = "buildchroot:do_prepare"

# Each package should have its own unique build folder, so use
# recipe name as identifier
PP = "/home/builder/${PN}"

BUILDROOT = "${BUILDCHROOT_DIR}/${PP}"
do_build[stamp-extra-info] = "${DISTRO}-${DISTRO_ARCH}"

# default to "emtpy" implementation
dpkg_runbuild() {
    die "This should never be called, overwrite it in your derived class"
}

# Wrap the function dpkg_runbuild with the bind mount for buildroot
do_build() {
    if [ -d ${WORKDIR}/git/.git ]; then
        OBJ_PATH=$(cat ${WORKDIR}/git/.git/objects/info/alternates)
        REPO_PATH=$(dirname $OBJ_PATH)
        REPO_NAME=$(basename $REPO_PATH)
        echo "/git/$REPO_NAME/objects" > ${WORKDIR}/git/.git/objects/info/alternates
    fi

    mkdir -p ${BUILDROOT}
    sudo mount --bind ${WORKDIR} ${BUILDROOT}
    _do_build_cleanup() {
        ret=$?
        sudo umount ${BUILDROOT} 2>/dev/null || true
        sudo rmdir ${BUILDROOT} 2>/dev/null || true

        if [ -d ${WORKDIR}/git/.git ]; then
            echo $OBJ_PATH > ${WORKDIR}/git/.git/objects/info/alternates
        fi

        (exit $ret) || sudo umount ${BUILDCHROOT_DIR}/apt
        (exit $ret) || sudo umount ${BUILDCHROOT_DIR}/git
        (exit $ret) || bb_exit_handler
    }
    trap '_do_build_cleanup' EXIT
    dpkg_runbuild
    _do_build_cleanup
}

# Install package to dedicated deploy directory
do_deploy_deb() {
    install -m 644 ${WORKDIR}/*.deb ${DEPLOY_DIR_DEB}/
}

addtask deploy_deb after do_build
do_deploy_deb[dirs] = "${DEPLOY_DIR_DEB}"
do_deploy_deb[stamp-extra-info] = "${MACHINE}"

do_get_deps[stamp-extra-info] = "${DISTRO}-${DISTRO_ARCH}"

# Derive dependencies from debian/control and provide them to base-apt
do_get_deps() {
    if [ -e ${S}/debian/control ]; then
        DEPS=$(perl -ne 'next if /^#/; $p=(s/^Build-Depends:\s*/ / or (/^ / and $p)); s/,|\n|\([^)]+\)|\[[^]]+\]//mg; print if $p' < ${S}/debian/control)
    fi

    echo $DEPS > ${BASE_APT_DIR}/deps/${PN}
}

addtask get_deps after do_unpack before do_build
