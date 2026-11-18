#!/usr/bin/env python3
"""Converts data_src/grammar.txt into app/src/main/assets/data/grammar.json and validates it.

Usage:  python3 tools/build_grammar.py
To add a lesson: append a block to data_src/grammar.txt (see the header of that file) and run this script.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "data_src" / "grammar.txt"
OUT = ROOT / "app/src/main/assets/data"
errors = []


def err(msg):
    errors.append(msg)


def parse():
    lessons, cur = [], None
    for ln, raw in enumerate(SRC.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        where = f"grammar.txt:{ln}"
        if line.startswith("@lesson "):
            p = [x.strip() for x in line[8:].split("|")]
            if len(p) != 4:
                err(f"{where}: @lesson needs id|level|en|ar")
                continue
            cur = {"id": p[0], "num": len(lessons) + 1, "lv": p[1], "en": p[2], "ar": p[3],
                   "explain": [], "formulas": [], "examples": [], "mistakes": [], "quiz": [], "p": ""}
            lessons.append(cur)
            continue
        if cur is None:
            err(f"{where}: line before any @lesson")
            continue
        tag, _, rest = line.partition(":")
        tag, rest = tag.strip(), rest.strip()
        try:
            if tag == "E":
                cur["explain"].append(rest)
            elif tag == "F":
                a, b = [x.strip() for x in rest.split("|", 1)]
                cur["formulas"].append([a, b])
            elif tag == "X":
                a, b = [x.strip() for x in rest.split("|", 1)]
                cur["examples"].append([a, b])
            elif tag == "M":
                left, note = [x.strip() for x in rest.split("|", 1)]
                w, r = [x.strip() for x in left.split("→", 1)]
                cur["mistakes"].append({"w": w, "r": r, "n": note})
            elif tag == "Q":
                p = [x.strip() for x in rest.split("|")]
                q, ans, wrong = p[0], p[1], [x for x in p[2:] if x]
                if q.count("___") != 1:
                    err(f"{where}: question needs exactly one ___")
                if len(wrong) < 2:
                    err(f"{where}: need at least 2 wrong options")
                if ans in wrong or len(set(wrong)) != len(wrong):
                    err(f"{where}: options must be distinct")
                cur["quiz"].append({"q": q, "a": ans, "w": wrong})
            elif tag == "P":
                try:
                    re.compile(rest)
                except re.error as e:
                    err(f"{where}: bad regex ({e})")
                cur["p"] = rest
            else:
                err(f"{where}: unknown tag '{tag}'")
        except ValueError:
            err(f"{where}: malformed '{tag}' line")
    order = {"A1": 0, "A2": 1, "B1": 2, "B2": 3}
    lessons.sort(key=lambda l: order.get(l["lv"], 9))
    for i, l in enumerate(lessons, 1):
        l["num"] = i
        seen_q = set()
        for q in l["quiz"]:
            if q["q"] in seen_q:
                err(f"{l['id']}: duplicate question '{q['q']}'")
            seen_q.add(q["q"])
    return lessons


def sentences():
    out = []
    for f in sorted((OUT).glob("topic[0-9]*.json")):
        out += [s["en"] for s in json.loads(f.read_text(encoding="utf-8"))["sentences"]]
    rd = OUT / "reading.json"
    if rd.exists():
        data = json.loads(rd.read_text(encoding="utf-8"))
        passages = list(data["paragraphs"]) + [c for b in data["books"] for c in b["ch"]]
        out += [en for p in passages for en, _ in p["s"]]
    return out


def main():
    lessons = parse()
    sents = sentences()
    ids = set()
    for l in lessons:
        if l["id"] in ids:
            err(f"duplicate lesson id {l['id']}")
        ids.add(l["id"])
        for key, minimum in (("explain", 3), ("formulas", 2), ("examples", 3), ("mistakes", 1), ("quiz", 8)):
            if len(l[key]) < minimum:
                err(f"{l['id']}: needs at least {minimum} '{key}' entries")
        matches = 0
        if l["p"]:
            rx = re.compile(l["p"], re.I)
            matches = sum(1 for s in sents if rx.search(s))
            if matches < 2:
                err(f"{l['id']}: pattern matches only {matches} app sentences")
        print(f"{l['id']} [{l['lv']}] {l['en']:<42} E{len(l['explain'])} F{len(l['formulas'])} "
              f"X{len(l['examples'])} M{len(l['mistakes'])} Q{len(l['quiz'])}  linked sentences: {matches}")
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "grammar.json").write_text(
        json.dumps({"lessons": lessons}, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"TOTAL: {len(lessons)} lessons, {sum(len(l['quiz']) for l in lessons)} quiz questions")
    if errors:
        print("\nERRORS:")
        for e in errors:
            print(" -", e)
        sys.exit(1)
    print("OK - grammar valid")


main()
