#!/usr/bin/env python3
"""windows-dll-deps.py <folder> <dll>

Prints <dll> and every DLL in <folder> it loads, directly or through another, one per line: what
has to be copied beside it. DLLs not in <folder> (Windows' own) are left out. Reads the PE import
tables with pefile (pip install pefile). Used by the Windows CI build for Tesseract's libraries.
"""
import os
import sys

import pefile


def imports(path):
    pe = pefile.PE(path, fast_load=True)
    pe.parse_data_directories(directories=[pefile.DIRECTORY_ENTRY["IMAGE_DIRECTORY_ENTRY_IMPORT"]])
    return [entry.dll.decode() for entry in getattr(pe, "DIRECTORY_ENTRY_IMPORT", [])]


def main():
    folder, root = sys.argv[1], sys.argv[2]
    present = {name.lower(): name for name in os.listdir(folder) if name.lower().endswith(".dll")}
    needed, queue = [], [root]
    while queue:
        name = queue.pop()
        actual = present.get(name.lower())
        if actual is None or actual in needed:
            continue
        needed.append(actual)
        queue.extend(imports(os.path.join(folder, actual)))
    print("\n".join(sorted(needed)))


if __name__ == "__main__":
    main()
