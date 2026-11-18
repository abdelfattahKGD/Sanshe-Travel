#!/usr/bin/env python3
"""Converts data_src/reading.txt into:
  - app/src/main/assets/data/reading.json   (paragraphs + books with chapters)
  - app/src/main/assets/data/topic11.json   (glossary words, so they join the normal review system)

Run order (or just run tools/build_all.py):  build_data -> build_reading -> build_data -> build_grammar
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "data_src" / "reading.txt"
OUT = ROOT / "app/src/main/assets/data"
POS = {"n", "v", "adj", "adv", "pron", "det", "prep", "conj", "interj", "phrase"}
LEVELS = {"A1", "A2", "B1", "B2"}
errors = []

IRREGULAR = {"geese": "goose", "mice": "mouse", "men": "man", "women": "woman", "children": "child",
             "feet": "foot", "teeth": "tooth", "fell": "fall", "went": "go", "saw": "see", "took": "take",
             "gave": "give", "ran": "run", "was": "be", "were": "be", "had": "have", "said": "say",
             "bought": "buy", "found": "find", "knew": "know", "thought": "think", "told": "tell",
             "became": "become", "woke": "wake", "flew": "fly", "sat": "sit"}


def variants(raw):
    """Same rules as Library.variants() in the app (keep them in sync)."""
    t = raw.lower().replace("’", "'").strip(" .,;:!?\"()[]")
    if not t:
        return []
    out = [t]

    def add(s):
        if len(s) >= 2 and s not in out:
            out.append(s)

    if t in IRREGULAR:
        add(IRREGULAR[t])
    if t.endswith("'s"):
        add(t[:-2])
    if t.endswith("ies"):
        add(t[:-3] + "y")
    if t.endswith("ves"):
        add(t[:-3] + "f")
        add(t[:-3] + "fe")
    if t.endswith("es"):
        add(t[:-2])
    if t.endswith("s"):
        add(t[:-1])
    if t.endswith("ied"):
        add(t[:-3] + "y")
    if t.endswith("ed"):
        add(t[:-2])
        add(t[:-1])
        if len(t) > 4 and t[-3] == t[-4]:
            add(t[:-3])
    if t.endswith("ing"):
        add(t[:-3])
        add(t[:-3] + "e")
        if len(t) > 5 and t[-4] == t[-5]:
            add(t[:-4])
    if t.endswith("iest"):
        add(t[:-4] + "y")
    if t.endswith("est"):
        add(t[:-3])
    if t.endswith("er"):
        add(t[:-2])
    if t.endswith("ly"):
        add(t[:-2])
    return out


def err(m):
    errors.append(m)


def new_passage(pid, level, en, ar, emoji):
    if level not in LEVELS:
        err(f"{pid}: bad level {level}")
    return {"id": pid, "lv": level, "en": en, "ar": ar, "e": emoji, "s": [], "w": [], "q": []}


def parse():
    paragraphs, books = [], []
    cur = None       # current passage
    book = None
    for ln, raw in enumerate(SRC.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        where = f"reading.txt:{ln}"
        if line.startswith("@text "):
            p = [x.strip() for x in line[6:].split("|")]
            if len(p) != 5:
                err(f"{where}: @text needs id|level|en|ar|emoji")
                continue
            cur = new_passage(p[0], p[1], p[2], p[3], p[4])
            paragraphs.append(cur)
            book = None
            continue
        if line.startswith("@book "):
            p = [x.strip() for x in line[6:].split("|")]
            if len(p) != 7:
                err(f"{where}: @book needs id|level|en|ar|emoji|author|description")
                continue
            book = {"id": p[0], "lv": p[1], "en": p[2], "ar": p[3], "e": p[4], "au": p[5], "d": p[6], "ch": []}
            books.append(book)
            cur = None
            continue
        if line.startswith("@chapter "):
            if book is None:
                err(f"{where}: @chapter outside a @book")
                continue
            p = [x.strip() for x in line[9:].split("|")]
            n = len(book["ch"]) + 1
            cur = new_passage(f"{book['id']}c{n}", book["lv"], p[0], p[1], book["e"])
            book["ch"].append(cur)
            continue
        if cur is None:
            err(f"{where}: line before any @text/@chapter")
            continue
        tag, _, rest = line.partition(":")
        tag, rest = tag.strip(), rest.strip()
        try:
            if tag == "S":
                en, ar = [x.strip() for x in rest.split("|", 1)]
                if not en or not ar:
                    raise ValueError
                cur["s"].append([en, ar])
            elif tag == "W":
                w, pos, ar = [x.strip() for x in rest.split("|", 2)]
                if pos not in POS:
                    err(f"{where}: bad pos '{pos}'")
                cur["w"].append([w, pos, ar])
            elif tag == "Q":
                p = [x.strip() for x in rest.split("|")]
                q, a, wrong = p[0], p[1], [x for x in p[2:] if x]
                if a in wrong or len(set(wrong)) != len(wrong) or len(wrong) < 2:
                    err(f"{where}: question options must be distinct (need 2+ wrong)")
                cur["q"].append({"q": q, "a": a, "w": wrong})
            else:
                err(f"{where}: unknown tag '{tag}'")
        except ValueError:
            err(f"{where}: malformed '{tag}' line")
    return paragraphs, books


def check(p):
    where = p["id"]
    if len(p["s"]) < 5:
        err(f"{where}: needs at least 5 sentences")
    if len(p["w"]) < 3:
        err(f"{where}: needs at least 3 glossary words")
    if len(p["q"]) < 2:
        err(f"{where}: needs at least 2 questions")
    tokens = set()
    for en, _ in p["s"]:
        for tok in re.split(r"\s+", en):
            tokens.update(variants(tok))
    text = " ".join(en.lower() for en, _ in p["s"])
    for w, _, _ in p["w"]:
        wl = w.lower()
        ok = (wl in text) if " " in wl else (wl in tokens)
        if not ok:
            err(f"{where}: glossary word '{w}' does not appear in the text")


def existing_words():
    words = set()
    for f in sorted(OUT.glob("topic[0-9]*.json")):
        if f.name == "topic11.json":
            continue
        for w in json.loads(f.read_text(encoding="utf-8"))["words"]:
            words.add(w["en"].lower())
    return words


def example_for(p, word):
    wl = word.lower()
    for en, _ in p["s"]:
        low = en.lower()
        if " " in wl and wl in low:
            return en
        if " " not in wl and any(wl in variants(t) for t in re.split(r"\s+", en)):
            return en
    return ""


def main():
    paragraphs, books = parse()
    passages = list(paragraphs)
    for b in books:
        if not b["ch"]:
            err(f"{b['id']}: book has no chapters")
        passages += b["ch"]
    ids = set()
    for p in passages:
        if p["id"] in ids:
            err(f"duplicate id {p['id']}")
        ids.add(p["id"])
        check(p)

    # glossary words become vocabulary (only words the app does not already teach)
    known = existing_words()
    groups, words, n = [], [], 0
    titles = {}
    for b in books:
        for c in b["ch"]:
            titles[c["id"]] = (f"{b['en']} – {c['en']}", f"{b['ar']} – {c['ar']}")
    for p in passages:
        en_t, ar_t = titles.get(p["id"], (p["en"], p["ar"]))
        gid = f"r_{p['id']}"
        added = 0
        for w, pos, ar in p["w"]:
            key = w.lower()
            if key in known:
                continue
            known.add(key)
            n += 1
            added += 1
            words.append({"id": f"t11w{n:03d}", "g": gid, "lv": p["lv"], "en": w, "pos": pos, "ar": ar,
                          "ex": example_for(p, w)})
        if added:
            groups.append({"id": gid, "ar": ar_t, "en": en_t})

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "reading.json").write_text(
        json.dumps({"paragraphs": paragraphs, "books": books}, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8")
    (OUT / "topic11.json").write_text(
        json.dumps({"groups": groups, "words": words, "sentences": []}, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8")

    nsent = sum(len(p["s"]) for p in passages)
    print(f"paragraphs: {len(paragraphs)}, books: {len(books)}, chapters: {sum(len(b['ch']) for b in books)}, "
          f"sentences: {nsent}, quiz questions: {sum(len(p['q']) for p in passages)}")
    print(f"new vocabulary for 'Words from Reading': {len(words)} words in {len(groups)} groups")
    if errors:
        print("\nERRORS:")
        for e in errors:
            print(" -", e)
        sys.exit(1)
    print("OK - reading valid")


main()
