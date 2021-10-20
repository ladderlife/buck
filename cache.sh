#!/usr/bin/env nix-shell
#!nix-shell -i bash -p nixUnstable -p cachix

# Get in touch with @piperswe to be added to the Cachix cache

set -euxo pipefail

CACHIX_CACHE="${CACHIX_CACHE:-ladderlife-buck}"
FLAKE="${FLAKE:-github:LadderLife/buck/piper/dev-with-async}"

# See https://docs.cachix.org/pushing#flakes

# Cache build dependencies
cachix watch-exec "$CACHIX_CACHE" nix -- --experimental-features 'nix-command flakes' build "$FLAKE"
# Cache inputs
nix --experimental-features 'nix-command flakes' flake archive "$FLAKE" --json \
  | jq -r '.path,(.inputs|to_entries[].value.path)' \
  | cachix push "$CACHIX_CACHE"
# Cache artifacts
nix --experimental-features 'nix-command flakes' build "$FLAKE" --json \
  | jq -r '.[].outputs | to_entries[].value' \
  | cachix push "$CACHIX_CACHE"
# Cache dev shell
nix --experimental-features 'nix-command flakes' develop "$FLAKE" --profile dev-profile --command true
cachix push "$CACHIX_CACHE" dev-profile
rm dev-profile*
