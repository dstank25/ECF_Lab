package hr.fer.zemris.ecf.lab.engine.console;

import java.io.File;
import java.io.IOException;

/**
 * This class is the Unix'x terminal implementation of {@link Console}.
 *
 * @version 1.0
 */
public class Terminal implements Console {

    @Override
    public void pardump(String ecfPath, String pardumpPath) {
        try {
            File file = new File(pardumpPath);
            file.getParentFile().mkdirs();
            Process process = new ProcessBuilder(ecfPath, "-gui", "-pardump", pardumpPath).start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println("Problem with writing to linux terminal.");
            e.printStackTrace();
        }

    }

    @Override
    public void execute(Job job) {
        String programPath = job.getEcfPath();
        String args = job.getConfigPath();
        synchronized (System.out) {
            System.out.println(programPath + " " + args);
        }
        try {
            job.started();
            Process process = new ProcessBuilder(programPath, args).start();
            process.waitFor();
            ProcessOutput output = new ProcessOutput(process.getInputStream(), process.getErrorStream());
            job.finished(output);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            job.failed();
        }
    }

}
