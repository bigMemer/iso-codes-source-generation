#!/usr/bin/env python3
"""Extract ISO 3166 history evidence from iso-codes, CLDR and ISO's change log into history/iso3166-evidence.json.

This only records what each source said and when (docs/sources.md §9). It draws no conclusions: date ranges,
holders and `recorded_since` are worked out by the generator's `source-history` module.

    scripts/build_history.py [--cache DIR]

Needs git and network access. Clones are cached (default: build/history-cache) and updated on each run.
"""
import argparse
import json
import re
import subprocess
import sys
import urllib.request
import html
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "history" / "iso3166-evidence.json"
ISO_CODES = "https://salsa.debian.org/iso-codes-team/iso-codes.git"
CLDR = "https://github.com/unicode-org/cldr.git"
UPDATES_API = "https://api.github.com/repos/amckenna41/iso3166-updates/commits/main"
UPDATES_RAW = "https://raw.githubusercontent.com/amckenna41/iso3166-updates/{sha}/iso3166_updates/iso3166-updates.json"
USER_AGENT = "iso-codes-source-generation history builder (https://github.com/bigMemer/iso-codes-source-generation)"

# Paths that held ISO 3166 data in iso-codes, newest first.
SUBDIVISION_PATHS = ["data/iso_3166-2.json", "iso_3166_2/iso_3166_2.xml", "iso_3166/iso_3166_2/iso_3166_2.xml",
                     "iso_3166/iso_3166_2/iso_3166_2.tab", "iso_3166/iso_3166_2.tab"]
COUNTRY_PATHS = ["data/iso_3166-1.json", "iso_3166/iso_3166.xml", "iso_3166/iso_3166.tab"]
FORMER_PATHS = ["data/iso_3166-3.json", "iso_3166/iso_3166.xml"]


def git(repo, *args, check=True):
    return subprocess.run(["git", "-C", str(repo), *args], capture_output=True, text=True, check=check).stdout


def clone(url, path, blobless=False):
    if (path / ".git").exists() or (path / "HEAD").exists():
        git(path, "fetch", "--quiet", "--tags", "origin")
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        args = ["git", "clone", "--quiet", "--no-checkout"] + (["--filter=blob:none"] if blobless else []) + [url, str(path)]
        subprocess.run(args, check=True)
    return path


def show(repo, rev, path):
    """The file's text at a revision, or None. A few old files contain stray invalid bytes; those are replaced
    rather than misreading the whole file."""
    result = subprocess.run(["git", "-C", str(repo), "show", f"{rev}:{path}"], capture_output=True)
    if result.returncode != 0:
        return None
    return result.stdout.decode("utf-8", errors="replace")


MOJIBAKE = re.compile("[\u00c2-\u00c5\u00d0\u00d1][\u0080-\u00bf]")


def repair(name):
    """Undoes UTF-8 text that was once stored as if it were Latin-1 (GuÃ©ra for Guéra), as some 2007 iso-codes
    commits did."""
    if not name or not MOJIBAKE.search(name):
        return name
    try:
        return name.encode("latin-1").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return name


def xml_tags(text, names):
    """Yields (tag, attributes) for the named start tags, in document order.

    A tolerant scan rather than an XML parser: 77 of iso-codes' historical XML snapshots aren't well-formed (stray
    characters, mismatched end tags, duplicate attributes), and a scan reads all of them the same way.
    """
    pattern = re.compile(r"<(%s)\b([^>]*)>" % "|".join(names))
    for match in pattern.finditer(re.sub(r"<!--.*?-->", "", text, flags=re.S)):
        attributes = {k: html.unescape(v) for k, v in re.findall(r'([\w:]+)\s*=\s*"([^"]*)"', match.group(2))}
        yield match.group(1), attributes


# ---- iso-codes parsers: each returns {code: {field: value}} ------------------------------------------------------

def parse_subdivisions(path, text):
    if path.endswith(".json"):
        rows = json.loads(text)["3166-2"]
        if rows and "subsets" in rows[0]:  # iso-codes 3.66 nested layout
            return None
        return {r["code"]: {k: (repair(r[k]) if k == "name" else r[k]) for k in ("name", "type", "parent") if k in r}
                for r in rows}
    if path.endswith(".xml"):
        out, subset_type = {}, None
        for tag, a in xml_tags(text, ["iso_3166_subset", "iso_3166_2_entry"]):
            if tag == "iso_3166_subset":
                subset_type = a.get("type")
                continue
            code = a.get("code", "")
            if not re.fullmatch(r"[A-Z]{2}-[A-Z0-9]{1,3}", code):
                continue
            values = {"name": repair(a.get("name")), "type": subset_type}
            parent = a.get("parent")
            if parent:
                values["parent"] = parent if "-" in parent else f"{code[:2]}-{parent}"
            out[code] = {k: v for k, v in values.items() if v}
        return out
    out = {}
    for line in text.splitlines():
        parts = line.split("\t")
        if len(parts) >= 2 and re.fullmatch(r"[A-Z]{2}-[A-Z0-9]{1,3}", parts[0].strip()):
            out[parts[0].strip()] = {"name": parts[1].strip()}
    return out


def parse_countries(path, text):
    if path.endswith(".json"):
        return {r["alpha_2"]: {k: r[k] for k in ("alpha_3", "numeric", "name", "official_name", "common_name") if k in r}
                for r in json.loads(text)["3166-1"]}
    if path.endswith(".xml"):
        out = {}
        for _, a in xml_tags(text, ["iso_3166_entry"]):
            if not re.fullmatch(r"[A-Z]{2}", a.get("alpha_2_code", "")):
                continue
            values = {"alpha_3": a.get("alpha_3_code"), "numeric": a.get("numeric_code"), "name": a.get("name"),
                      "official_name": a.get("official_name"), "common_name": a.get("common_name")}
            out[a["alpha_2_code"]] = {k: v for k, v in values.items() if v}
        return out
    out = {}
    for line in text.splitlines():
        parts = line.split("\t")
        if len(parts) >= 2 and re.fullmatch(r"[A-Z]{2}", parts[0].strip()):
            out[parts[0].strip()] = {"name": parts[-1].strip()}
    return out


def parse_former(path, text):
    if path.endswith(".json"):
        return json.loads(text)["3166-3"]
    out = []
    for _, a in xml_tags(text, ["iso_3166_3_entry"]):
        out.append({k: v for k, v in {
            "alpha_2": (a.get("alpha_4_code") or "")[:2] or None, "alpha_3": a.get("alpha_3_code"),
            "alpha_4": a.get("alpha_4_code"), "numeric": a.get("numeric_code"), "name": a.get("names") or a.get("name"),
            "withdrawal_date": a.get("date_withdrawn"), "comment": a.get("comment")}.items() if v})
    return out


def iso_codes_snapshots(repo, paths, parser):
    commits = git(repo, "log", "--reverse", "--format=%H %cs", "--", *paths).split("\n")
    snapshots = []
    for line in filter(None, commits):
        sha, date = line.split()
        # While iso-codes moved between formats, old and new files coexisted and the new one could be incomplete
        # (in March-April 2004 the first XML held 2,237 subdivisions while the tab file still held 3,806). Read
        # every candidate and keep the most complete.
        parsed = [parser(path, text) for path in paths if (text := show(repo, sha, path)) is not None]
        parsed = [p for p in parsed if p]
        if parsed:
            snapshots.append((date, sha, max(parsed, key=len)))
    return snapshots


# ---- CLDR -------------------------------------------------------------------------------------------------------

def cldr_ids(xml, type_, status):
    for m in re.finditer(r"<id type=['\"]%s['\"] idStatus=['\"]%s['\"]>(.*?)</id>" % (type_, status), xml, re.S):
        body = re.sub(r"<!--.*?-->", "", m.group(1), flags=re.S)
        for token in body.split():
            if "~" in token:
                start, end = token.split("~")
                for c in range(ord(start[-1]), ord(end) + 1):
                    yield start[:-1] + chr(c)
            else:
                yield token


def cldr_subdivision_code(cldr_id):
    """CLDR 28 wrote ISO's form (AD-02); later releases write ad02."""
    upper = cldr_id.upper()
    return upper if "-" in upper else f"{upper[:2]}-{upper[2:]}"


def cldr_snapshots(repo):
    tags = [t for t in git(repo, "tag", "--list", "release-*").split() if re.fullmatch(r"release-\d+(-\d+)?", t)]
    tags.sort(key=lambda t: [int(x) for x in t.split("-")[1:]])
    countries, subdivisions = [], []
    for tag in tags:
        regions = show(repo, tag, "common/validity/region.xml")
        subs = show(repo, tag, "common/validity/subdivision.xml")
        if regions is None or subs is None:
            continue
        date = git(repo, "log", "-1", "--format=%cs", tag).strip()
        version = tag[len("release-"):].replace("-", ".")
        countries.append((date, version, {i: {} for i in cldr_ids(regions, "region", "regular") if re.fullmatch(r"[A-Z]{2}", i)}))
        subdivisions.append((date, version, {cldr_subdivision_code(i): {} for i in cldr_ids(subs, "subdivision", "regular")}))
    return countries, subdivisions


# ---- turning snapshots into presence intervals --------------------------------------------------------------------

def intervals(snapshots):
    """{code: [{first_seen, last_seen, gone_by, values, names}]}: one interval per continuous run of snapshots listing
    the code. `values` are from the run's last snapshot; `names` records every name the code had during the run, so
    a code handed to a different holder without a gap (MA-02 in 2018) is visible."""
    out, open_ = {}, {}
    for date, ref, codes in snapshots:
        for code, values in codes.items():
            name = values.get("name")
            if code in open_:
                interval = open_[code]
                interval.update(last_seen=date, values=values)
                if name and name != interval["names"][-1]["name"]:
                    interval["names"].append({"first_seen": date, "last_seen": date, "name": name})
                else:
                    interval["names"][-1]["last_seen"] = date
            else:
                open_[code] = {"first_seen": date, "last_seen": date, "gone_by": None, "values": values,
                               "names": [{"first_seen": date, "last_seen": date, "name": name}]}
                out.setdefault(code, []).append(open_[code])
        for code in [c for c in open_ if c not in codes]:
            open_.pop(code)["gone_by"] = date
    return out


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request) as response:
        return response.read().decode("utf-8")


def first_date(text):
    match = re.match(r"\d{4}-\d{2}-\d{2}", text.strip())
    return match.group(0) if match else None


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--cache", type=Path, default=ROOT / "build" / "history-cache")
    args = parser.parse_args()

    iso = clone(ISO_CODES, args.cache / "iso-codes")
    cldr = clone(CLDR, args.cache / "cldr", blobless=True)
    print("Reading iso-codes history...", file=sys.stderr)
    iso_sub = iso_codes_snapshots(iso, SUBDIVISION_PATHS, parse_subdivisions)
    iso_cty = iso_codes_snapshots(iso, COUNTRY_PATHS, parse_countries)
    head = git(iso, "rev-parse", "origin/HEAD").strip()
    former = {}
    for path in FORMER_PATHS:
        text = show(iso, head, path)
        if text:
            for row in parse_former(path, text):
                former[row.get("alpha_4", row.get("alpha_3"))] = row
            break
    print("Reading CLDR releases...", file=sys.stderr)
    cldr_cty, cldr_sub = cldr_snapshots(cldr)
    print("Reading ISO change log...", file=sys.stderr)
    updates_sha = json.loads(fetch(UPDATES_API))["sha"]
    updates = json.loads(fetch(UPDATES_RAW.format(sha=updates_sha)))

    change_log, mentions = {}, {}
    for country, entries in sorted(updates.items()):
        for entry in entries:
            text = (entry.get("Change", "") + " " + entry.get("Description of Change", "")).strip()
            record = {"date": first_date(entry["Date Issued"]), "date_text": entry["Date Issued"], "text": text}
            change_log.setdefault(country, []).append(record)
            for code in sorted(set(re.findall(r"\b[A-Z]{2}-[A-Z0-9]{1,3}\b", text))):
                mentions.setdefault(code, []).append(record)

    def standard(iso_snapshots, cldr_snapshots_):
        iso_iv, cldr_iv = intervals(iso_snapshots), intervals(cldr_snapshots_)
        codes = sorted(set(iso_iv) | set(cldr_iv))
        return {code: {k: v for k, v in {"iso-codes": iso_iv.get(code), "cldr": cldr_iv.get(code),
                                          "change-log": mentions.get(code)}.items() if v}
                for code in codes}

    evidence = {
        "sources": {
            "iso-codes": {"commit": head, "observed_from": iso_sub[0][0], "observed_to": iso_sub[-1][0],
                          "snapshots_3166_1": len(iso_cty), "snapshots_3166_2": len(iso_sub)},
            "cldr": {"observed_from": cldr_sub[0][0], "observed_to": cldr_sub[-1][0], "releases": [v for _, v, _ in cldr_sub]},
            "iso3166-updates": {"commit": updates_sha, "license": "MIT"},
        },
        "3166-1": standard(iso_cty, cldr_cty),
        "3166-2": standard(iso_sub, cldr_sub),
        "former-countries": sorted(former.values(), key=lambda r: r.get("alpha_4", "")),
        "change-log": change_log,
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(evidence, indent=1, ensure_ascii=False, sort_keys=True) + "\n")
    print(f"Wrote {OUTPUT.relative_to(ROOT)}: {len(evidence['3166-1'])} country codes, "
          f"{len(evidence['3166-2'])} subdivision codes", file=sys.stderr)


if __name__ == "__main__":
    main()
