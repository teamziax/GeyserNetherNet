#!/usr/bin/env bash
set -euo pipefail
# Build one immutable integration revision; no source merging during artifact creation.
extension_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
network_revision=$(sed -n 's/^commit=//p' "$extension_root/registration-network.properties")
[[ $network_revision =~ ^[0-9a-f]{40}$ ]] || { echo 'Invalid Network source pin' >&2; exit 1; }
native_revision=$network_revision
provider_revision=$network_revision
integration_root=$(mktemp -d "${TMPDIR:-/tmp}/geyser-native-build.XXXXXXXX")
trap 'rm -rf -- "$integration_root"' EXIT
git clone --quiet https://github.com/teamziax/NetworkCompatible.git "$integration_root/network"
git -C "$integration_root/network" checkout --quiet --detach "$network_revision"
(
    cd "$integration_root/network"
    ./scripts/bootstrap-native-admission.sh
    ./gradlew :warden-signalling:test :warden-signalling:nativeAdmissionTest :transport-nethernet:test
)
cd "$extension_root"
extension_revision=$(git rev-parse HEAD)
if [[ -n "$(git status --porcelain)" ]]; then extension_revision+="-dirty"; fi
bash gradlew build -PwardenNetworkPath="$integration_root/network" -PregistrationRevision="$extension_revision"
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
