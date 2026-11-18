#!/usr/bin/env python3
"""Rebuilds ALL app data in the right order: python3 tools/build_all.py"""
import pathlib
import subprocess
import sys

here = pathlib.Path(__file__).resolve().parent
for script in ("build_data.py", "build_reading.py", "build_data.py", "build_grammar.py"):
    print(f"\n=== {script} ===")
    if subprocess.call([sys.executable, str(here / script)]) != 0:
        sys.exit(1)
print("\nALL DATA OK")
