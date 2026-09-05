from pathlib import Path

# One-time staging script; this comment intentionally triggers the registered push gate.
TARGET = Path('app/src/main/java/com/gree1d/reappzuku/utils/triggers/analyzers/ProcessAnalyzer.java')
EXPECTED_BLOB = '2e7bbdf94590b37d3e506ee5838b7cdf7e7044b8'

text = TARGET.read_text()

replacements = [
    ('''// ---- CONST:BINDER_PATS ----
    private static final Pattern[] BINDER_PATS = {
            Pattern.compile("ProcessRecord\\\\{[^}]+\\\\s([\\\\w.]+)/"),
            Pattern.compile("client=ProcessRecord\\\\{[^}]+\\\\s([\\\\w.]+)/")
    };


''', ''''''),
    ('''        boolean inBlock    = false;
        int     adj        = Integer.MAX_VALUE;
        String  procState  = null;
        boolean persistent = false;

        Pattern procPat  = Pattern.compile(
                "ProcessRecord\\\\{[^}]+\\\\s" + Pattern.quote(packageName) + "/");
        Pattern adjPat   = Pattern.compile("\\\\badj=([-\\\\d]+)");
        Pattern statePat = Pattern.compile("\\\\bcurProcState=(\\\\w+)");

        for (String line : output.split("\\n")) {
            if (procPat.matcher(line).find()) {
                inBlock    = true;
                persistent = line.contains("persistent=true");
                continue;
            }
            if (inBlock && line.trim().startsWith("ProcessRecord{")
                    && !line.contains(packageName)) break;
            if (!inBlock) continue;

            Matcher mAdj = adjPat.matcher(line);
            if (mAdj.find() && adj == Integer.MAX_VALUE)
                adj = Integer.parseInt(mAdj.group(1));

            Matcher mState = statePat.matcher(line);
            if (mState.find() && procState == null)
                procState = mState.group(1);

            if (line.contains("persistent=true")) persistent = true;
        }

        if (procState == null && adj == Integer.MAX_VALUE) return list;
''', '''        ProcessDumpParser.ProcessStateSnapshot state =
                ProcessDumpParser.parseProcessState(output, packageName);
        if (state == null) return list;

        int adj = state.adj;
        String procState = state.procState;
        boolean persistent = state.persistent;
'''),
    ('''            if (t.contains("ServiceRecord") && t.contains(packageName)) {''',
     '''            if (ProcessDumpParser.isServiceRecordForPackage(t, packageName)) {'''),
    ('''            if (inBlock && t.contains("ServiceRecord") && !t.contains(packageName)) {''',
     '''            if (inBlock && ProcessDumpParser.isServiceRecordLine(t)
                    && !ProcessDumpParser.isServiceRecordForPackage(t, packageName)) {'''),
    ('''            for (Pattern bp : BINDER_PATS) {
                Matcher m = bp.matcher(t);
                if (m.find()) {
                    String pkg = m.group(1);
                    if (!pkg.equals(packageName) && !pkg.equals("android")
                            && !binders.contains(pkg)) binders.add(pkg);
                }
            }
''', '''            String binderPackage = ProcessDumpParser.extractProcessRecordPackage(t);
            if (binderPackage != null
                    && !binderPackage.equals(packageName)
                    && !binderPackage.equals("android")
                    && !binders.contains(binderPackage)) {
                binders.add(binderPackage);
            }
'''),
    ('''        Matcher m = Pattern.compile("ServiceRecord\\\\{[^}]+\\\\s([\\\\w./]+)\\\\}").matcher(line);
        if (!m.find()) return null;
        String full = m.group(1);
        if (!full.contains("/")) return full;
        String cls = full.substring(full.indexOf('/') + 1);
        if (cls.startsWith("."))               return cls.substring(1);
        if (cls.startsWith(packageName + ".")) return cls.substring(packageName.length() + 1);
        return cls;
''', '''        return ProcessDumpParser.extractServiceShortName(line, packageName);
'''),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'expected exactly one ProcessAnalyzer source match, got {count}')
    text = text.replace(old, new, 1)

TARGET.write_text(text)
