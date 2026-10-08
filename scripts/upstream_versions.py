#!/usr/bin/env python3
"""Print iso-codes releases this generator supports, oldest first, one per line (or only the newest with --latest).

Upstream releases come from the tags of https://salsa.debian.org/iso-codes-team/iso-codes.
Releases before 3.67 are skipped: 3.66 used a different JSON layout and older ones had no JSON at all.
"""
import json
import re
import sys
import urllib.request

TAGS_URL = "https://salsa.debian.org/api/v4/projects/iso-codes-team%2Fiso-codes/repository/tags?per_page=100&page={}"
OLDEST = (3, 67)


def upstream_versions():
    versions, page = set(), 1
    while True:
        with urllib.request.urlopen(TAGS_URL.format(page)) as response:
            tags = json.load(response)
        if not tags:
            break
        for tag in tags:
            match = re.fullmatch(r"(?:v|iso-codes-)(\d+(?:\.\d+)+)", tag["name"])
            if match and tuple(map(int, match.group(1).split("."))) >= OLDEST:
                versions.add(match.group(1))
        page += 1
    return sorted(versions, key=lambda v: tuple(map(int, v.split("."))))


def main():
    versions = upstream_versions()
    print("\n".join(versions[-1:] if "--latest" in sys.argv[1:] else versions))


if __name__ == "__main__":
    main()
