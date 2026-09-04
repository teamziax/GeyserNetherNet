#!/usr/bin/env bash
set -euo pipefail
# Disposable local composition only. Neither source branch is merged or pushed.
native_revision=3c346c681396e0d7743467ac816a798f331dfb71
provider_revision=a9b163bc5914b44399381aedbc473e16e3ce17e5
extension_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
integration_root=$(mktemp -d "${TMPDIR:-/tmp}/geyser-native-build.XXXXXXXX")
trap 'rm -rf -- "$integration_root"' EXIT
git clone --quiet https://github.com/teamziax/NetworkCompatible.git "$integration_root/network"
git -C "$integration_root/network" checkout --quiet --detach "$native_revision"
# A source-only merge leaves HEAD at the pinned native revision; no integration commit is published.
if ! git -C "$integration_root/network" -c user.name=NativeBench -c user.email=native-bench.invalid merge --no-commit --no-ff "$provider_revision"; then
    conflicts=$(git -C "$integration_root/network" diff --name-only --diff-filter=U)
    if [[ "$conflicts" != 'warden-signalling/build.gradle.kts' ]]; then
        echo 'Unexpected integration conflicts; refusing to invent a resolution.' >&2
        exit 1
    fi
    python3 - "$integration_root/network" "$provider_revision" <<'PY'
import pathlib, subprocess, sys
root, provider = sys.argv[1:]
path = 'warden-signalling/build.gradle.kts'
def source(ref):
    return subprocess.check_output(['git', '-C', root, 'show', ref + ':' + path], text=True)
native, registration = source('HEAD'), source(provider)
anchor = 'description = "Provider registration and Warden control client"'
version = 'version = providers.gradleProperty("wardenVersion").getOrElse("0.1.0-registration-dev")'
assert native.count(anchor) == 1 and version in registration
native = native.replace(anchor, anchor + '\n' + version)
tasks = registration[registration.index('tasks.register<JavaExec>("providerStub")'):]
pathlib.Path(root, path).write_text(native + '\n' + tasks)
PY
    git -C "$integration_root/network" add warden-signalling/build.gradle.kts
fi
(
    cd "$integration_root/network"
    ./scripts/bootstrap-native-admission.sh
    ./gradlew :warden-signalling:test :warden-signalling:nativeAdmissionTest :transport-nethernet:test
)
cd "$extension_root"
bash gradlew build -PwardenNetworkPath="$integration_root/network"
python3 - "$extension_root" "$native_revision" "$provider_revision" <<'PY'
import pathlib, sys, zipfile
root, native, provider = sys.argv[1:]
jars = list(pathlib.Path(root, 'build/libs').glob('*.jar'))
assert len(jars) == 1, 'Expected one shaded extension'
with zipfile.ZipFile(jars[0]) as jar:
    service = 'META-INF/services/org.geyser.extension.nethernet.provider.ProviderHostFactory'
    assert jar.read(service).decode().strip() == 'org.geyser.extension.nethernet.admission.NativeProviderHostFactory'
    assert jar.namelist().count('native/libdatachannel-java.so') == 1
    # Unfold continuation lines in the manifest before checking full SHA pins.
    manifest = jar.read('META-INF/MANIFEST.MF').decode().replace('\r\n ', '')
    assert 'Native-Network-Revision: ' + native in manifest
    assert 'Registration-Network-Revision: ' + provider in manifest
print('Native extension packaging PASS:', jars[0])
PY
