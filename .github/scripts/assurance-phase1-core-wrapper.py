#!/usr/bin/env python3
from pathlib import Path
import runpy

p = Path('.github/scripts/assurance-phase1-core.py')
s = p.read_text(encoding='utf-8')
old = 'end = s.find("    public static final class ReleaseInfo", start)'
new = 'end = s.find("    public static String getAppVersion", start)'
if old in s:
    p.write_text(s.replace(old, new, 1), encoding='utf-8')
runpy.run_path(str(p), run_name='__main__')
