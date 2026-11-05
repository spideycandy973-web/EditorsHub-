# Ghidra headless post-script (Jython). Exports a native function inventory.
# Usage: analyzeHeadless <proj> <name> -import <lib.so> -scriptPath tools/apk-re -postScript GhidraFunctions.py
f = open('/tmp/ghidra-functions.txt', 'w')
try:
    fm = currentProgram.getFunctionManager()
    for fn in fm.getFunctions(True):
        f.write('%s %s\n' % (fn.getEntryPoint(), fn.getName()))
finally:
    f.close()
