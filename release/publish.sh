#!/bin/sh
# The standalone YMXS executables: the five tools of doc/tools.md, one set
# per platform, each a single file a machine runs with nothing installed
# beside it.
#
#   release/publish.sh [version]      # the six platforms below
#   TARGETS="linux-x64" release/publish.sh
#   OUT=dir release/publish.sh
#
# They are built from go/, so there are six of them: go build
# cross-compiles to any target from any host, with no toolchain installed
# for that target.
#
# NO JAVA RUNS HERE. The Java tree is the reference and ParityTest checks
# the two against one another, byte for byte; a release is built from one
# tree.
#
# A Go executable runs as it stands: a Java tool runs through bin/run,
# which finds the classpath first, and these run with no wrapper.
set -e
cd "$(dirname "$0")/.."
REPO=$(pwd)
OUT=${OUT:-dist}
# A relative OUT counts from the repository, and every use below is the
# resolved one: a build runs from go/, where a relative path counts from
# somewhere else.
case $OUT in /*) ;; *) OUT=$REPO/$OUT ;; esac
TARGETS=${TARGETS:-"win-x64 win-arm64 osx-x64 osx-arm64 linux-x64 linux-arm64"}
TOOLS="ym-to-ymxs ymxs-check ymxs-csv-to-json ymxs-json-to-csv ymxs-merge"

# The version names the zips. The pom is where it is written down, and this
# reads the text rather than running anything.
VERSION=${1:-$(sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p' pom.xml | head -1)}
if [ -z "$VERSION" ]; then
    echo "publish: pom.xml does not name a version" >&2
    exit 1
fi

rm -rf "$OUT/release"
mkdir -p "$OUT/release"

for target in $TARGETS; do
    case "$target" in
        win-*)   os=windows; ext=.exe ;;
        osx-*)   os=darwin;  ext= ;;
        linux-*) os=linux;   ext= ;;
        *) echo "publish: $target is not a platform this builds" >&2; exit 1 ;;
    esac
    case "$target" in
        *-x64)   arch=amd64 ;;
        *-arm64) arch=arm64 ;;
        *) echo "publish: $target does not name an architecture" >&2; exit 1 ;;
    esac

    # The directory is where a built tool gets tried out, so the build
    # starts from an empty one.
    rm -rf "$OUT/$target"
    mkdir -p "$OUT/$target"
    for tool in $TOOLS; do
        # CGO off makes the binary static and the cross-build runs; -s -w
        # drop the symbol and debug tables, which nothing here reads.
        (cd go && CGO_ENABLED=0 GOOS=$os GOARCH=$arch \
            go build -ldflags="-s -w" -o "$OUT/$target/$tool$ext" \
            ./cmd/"$tool")
    done
    zip="ymxs-tools-$target-v$VERSION.zip"
    (cd "$OUT/$target" && zip -q -X "../release/$zip" *)
    echo "$OUT/release/$zip: $(wc -c < "$OUT/release/$zip" | tr -d ' ') bytes"
done

# What the release contains, by name, size and hash: release/manifest.sh.
release/manifest.sh "$VERSION" "$OUT/release"

# The host's executables, tried as a user would: from a directory that is
# not this repository, with nothing beside them. A dump goes in and the
# tables come out, which is every tool but the merge in one pipe.
case "$(uname -s)-$(uname -m)" in
    Darwin-arm64) host=osx-arm64 ;;
    Darwin-x86_64) host=osx-x64 ;;
    Linux-x86_64) host=linux-x64 ;;
    Linux-aarch64) host=linux-arm64 ;;
    *) host= ;;
esac
if [ -n "$host" ] && [ -d "$OUT/$host" ]; then
    try=$(mktemp -d)
    cp src/test/resources/packed.ym "$try/tune.ym"
    (cd "$try" && "$OUT/$host/ym-to-ymxs" -silent < tune.ym \
        | "$OUT/$host/ymxs-check" -silent \
        | "$OUT/$host/ymxs-json-to-csv" -silent > tune.csv)
    "$OUT/$host/ymxs-csv-to-json" -silent < "$try/tune.csv" > "$try/tune.json"
    echo "tried: a dump through four tools from $OUT/$host, outside the repository," \
         "$(wc -c < "$try/tune.json" | tr -d ' ') bytes of JSON"
    rm -rf "$try"
fi

echo "$OUT/release is this release: the tools, one zip a platform."
