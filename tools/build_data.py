#!/usr/bin/env python3
"""Converts data_src/*.txt into app/src/main/assets/data/*.json and validates the content.

Usage:  python3 tools/build_data.py

To add a topic: write data_src/topicNN.txt (same format as topic01.txt),
set its "src" in data_src/topics.json, then run this script.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC, OUT = ROOT / "data_src", ROOT / "app/src/main/assets/data"
LEVELS = {"A1", "A2", "B1", "B2"}
POS = {"n", "v", "adj", "adv", "pron", "det", "prep", "conj", "interj", "phrase"}
errors, seen_w, seen_s = [], {}, {}


def err(msg):
    errors.append(msg)


def parse(path, num):
    groups, words, sents, mode = [], [], [], None
    gids = set()
    for ln, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        where = f"{path.name}:{ln}"
        if line.startswith("@group "):
            gid, ar, en = line[7:].split("|")
            groups.append({"id": gid, "ar": ar, "en": en})
            gids.add(gid)
            continue
        if line in ("@words", "@sentences"):
            mode = line[1:]
            continue
        p = [x.strip() for x in line.split("|")]
        if mode == "words":
            if len(p) != 6:
                err(f"{where}: expected 6 fields")
                continue
            g, lv, en, pos, ar, ex = p
            n = len(ex.split())
            if not 3 <= n <= 7:
                err(f"{where}: example has {n} words ({en})")
            if pos not in POS:
                err(f"{where}: bad pos '{pos}'")
            key = en.lower()
            if key in seen_w:
                err(f"{where}: duplicate word '{en}' (also {seen_w[key]})")
            seen_w[key] = where
            words.append({"g": g, "lv": lv, "en": en, "pos": pos, "ar": ar, "ex": ex})
        elif mode == "sentences":
            if len(p) != 4:
                err(f"{where}: expected 4 fields")
                continue
            g, lv, en, ar = p
            n = len(en.split())
            if not 5 <= n <= 12:
                err(f"{where}: sentence has {n} words ({en})")
            key = en.lower()
            if key in seen_s:
                err(f"{where}: duplicate sentence (also {seen_s[key]})")
            seen_s[key] = where
            sents.append({"g": g, "lv": lv, "en": en, "ar": ar})
        else:
            err(f"{where}: line outside @words/@sentences")
            continue
        if g not in gids:
            err(f"{where}: unknown group '{g}'")
        if lv not in LEVELS:
            err(f"{where}: bad level '{lv}'")
        if any(not x for x in p):
            err(f"{where}: empty field")
    for i, w in enumerate(words, 1):
        w["id"] = f"t{num}w{i:03d}"
    for i, s in enumerate(sents, 1):
        s["id"] = f"t{num}s{i:03d}"
    return {"groups": groups, "words": words, "sentences": sents}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    topics, total_w, total_s = [], 0, 0
    for t in json.loads((SRC / "topics.json").read_text(encoding="utf-8")):
        fname = None
        if t["src"]:
            data = parse(SRC / t["src"], t["num"])
            fname = f"topic{t['num']:02d}.json"
            (OUT / fname).write_text(
                json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
            total_w += len(data["words"])
            total_s += len(data["sentences"])
            print(f"topic {t['num']:>2}: {len(data['words'])} words, "
                  f"{len(data['sentences'])} sentences, {len(data['groups'])} groups")
        topics.append({"id": f"t{t['num']}", "num": t["num"], "ar": t["ar"],
                       "en": t["en"], "emoji": t["emoji"], "file": fname})
    (OUT / "topics.json").write_text(
        json.dumps({"topics": topics}, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"TOTAL: {total_w} words, {total_s} sentences")
    if errors:
        print("\nERRORS:")
        for e in errors:
            print(" -", e)
        sys.exit(1)
    print("OK - data valid")


main()
