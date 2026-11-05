import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import java.io.FileWriter;
import java.io.PrintWriter;

/** Ghidra 12.x headless post-script (Java provider; Jython was removed). */
public class GhidraFunctions extends GhidraScript {
    @Override
    public void run() throws Exception {
        PrintWriter out = new PrintWriter(new FileWriter("/tmp/ghidra-functions.txt"));
        int n = 0;
        for (Function fn : currentProgram.getFunctionManager().getFunctions(true)) {
            out.println(fn.getEntryPoint() + " " + fn.getName());
            n++;
        }
        out.println("# program=" + currentProgram.getName() + " functions=" + n);
        out.close();
        println("Exported " + n + " functions");
    }
}
