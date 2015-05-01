package hr.fer.zemris.ecf.lab.view.layout;

import hr.fer.zemris.ecf.lab.engine.console.ProcessOutput;
import hr.fer.zemris.ecf.lab.model.logger.LoggerProvider;
import hr.fer.zemris.ecf.lab.view.ECFLab;
import hr.fer.zemris.ecf.lab.view.Utils;
import hr.fer.zemris.ecf.lab.engine.conf.ConfigurationService;
import hr.fer.zemris.ecf.lab.engine.console.JobObserver;
import hr.fer.zemris.ecf.lab.engine.console.Job;
import hr.fer.zemris.ecf.lab.engine.param.*;
import hr.fer.zemris.ecf.lab.engine.task.TaskMannager;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;

/**
 * Panel that displays available parameters for the selected ECF executable
 * file. When all the parameters are selected, configuration can be ran.
 * Configuration and log files are created on the specified paths.
 *
 * @author Domagoj Stanković
 * @version 1.0
 */
public class ParametersSelection extends JPanel implements JobObserver {

    private static final long serialVersionUID = 1L;

    private static final boolean DAEMON = false;

    private ECFLab parent;
    private EntryBlockSelection<EntryBlock> algSel;
    private EntryBlockSelection<EntryBlock> genSel;
    private EntryListPanel regList;
    private String ecfPath;
    private DefinePanel definePanel;
    private String lastLogFilePath = null;

    /**
     * Creates new {@link ParametersSelection} object for choosing ECF
     * parameters.
     *
     * @param parent {@link ECFLab} frame that displays this panel
     */
    public ParametersSelection(ECFLab parent) {
        super(new BorderLayout());
        this.parent = parent;
        ecfPath = parent.getEcfPath();
        if (ecfPath == null) {
            throw new NullPointerException("ECF executable file undefined!");
        }
        algSel = new EntryBlockSelection<>(new DropDownPanel<>(parent.getParDump().algorithms));
        genSel = new EntryBlockSelection<>(new DropDownPanel<>(parent.getParDump().genotypes));
        regList = EntryListPanel.getComponent(parent.getParDump().registry.getEntryList());
        String file = new File("").getAbsolutePath();
        String log = file;
        add(new TempPanel(algSel, genSel, new JScrollPane(regList)), BorderLayout.CENTER);
        JButton button = new JButton(new AbstractAction() {

            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                clicked();
            }
        });
        button.setText("Run");
        int cores = Runtime.getRuntime().availableProcessors();
        definePanel = new DefinePanel(file, log, cores, button);
        add(definePanel, BorderLayout.SOUTH);
    }

    /**
     * Action performed when the "Run" button is clicked. Configuration file is
     * created under the specified path. Then the ECF exe is run and the results
     * are written to the log file under the specified path.
     */
    protected void clicked() {
        try {
            Configuration temp = getParameters();
            String file = definePanel.getParamsPath();
            String log = definePanel.getLogPath();
            lastLogFilePath = log;
            int pn = definePanel.getThreadsCount();
            boolean change = false;
            int repeats = 1;
            List<Entry> list = temp.registry.getEntryList();
            Entry e = Utils.findEntry(list, "batch.repeats");
            if (pn > 1) {
                if (e != null) {
                    repeats = Integer.parseInt(e.value);
                    if (repeats > 1) {
                        // N repeats, N threads -> separate repeats in N jobs (1 repeat per job)
                        e.value = "1";
                        change = true;
                    } else {
                        // 1 job (1 repeat), N threads -> change to 1 thread
                        pn = 1;
                    }
                } else {
                    // 1 job, N threads -> change to 1 thread
                    pn = 1;
                }
            } else {
                if (e != null) {
                    repeats = Integer.parseInt(e.value);
                    if (repeats > 1) {
                        Entry l = Utils.findEntry(list, "log.filename");
                        if (l != null) {
                            String value = l.value;
                            FileWriter fw = new FileWriter(log + Utils.LOG_EXT);
                            fw.write(repeats + "\n");
                            fw.write(value);
                            fw.close();
                        }
                    }
                }
            }
            ConfigurationService.getInstance().getWriter().write(new File(file), temp);
            final List<Job> jobs;
            if (change) {
                jobs = new ArrayList<>(repeats);
                for (int i = 0; i < repeats; i++) {
                    Job job = new Job(ecfPath, file);
                    job.setObserver(this);
                    jobs.add(job);
                }
            } else {
                jobs = new ArrayList<>(1);
                Job job = new Job(ecfPath, file);
                job.setObserver(this);
                jobs.add(job);
            }
            final int tCount = pn;
            Thread t = new Thread(new Runnable() {

                @Override
                public void run() {
                    TaskMannager tm = new TaskMannager();
                    try {
                        tm.startTasks(jobs, tCount);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            t.setDaemon(DAEMON);
            t.start();
        } catch (Exception e) {
            String message = e.getMessage();
            if (message.startsWith("java.lang.Exception: ")) {
                message = message.substring(21);
            }
            parent.reportError(message);
            LoggerProvider.getLogger().log(e);
        }
    }

    /**
     * Collects all the selected parameters from the selected
     * {@link ParametersSelection} panel.
     *
     * @return {@link Configuration} object containing all the selected
     * parameters
     */
    public Configuration getParameters() {
        // Algorithm filling
        List<EntryFieldDisplay<EntryBlock>> algList = algSel.getAddedEntries();
        List<EntryBlock> algs = new ArrayList<>(algList.size());
        for (EntryFieldDisplay<EntryBlock> a : algList) {
            algs.add(new EntryBlock(a.getBlock().getName(), a.getBlockDisplay().getSelectedEntries()));
        }

        // Genotype filling
        List<EntryFieldDisplay<EntryBlock>> genList = genSel.getAddedEntries();
        List<EntryBlock> gens = new ArrayList<>(genList.size());
        for (EntryFieldDisplay<EntryBlock> g : genList) {
            gens.add(new EntryBlock(g.getBlock().getName(), g.getBlockDisplay().getSelectedEntries()));
        }
        List<List<EntryBlock>> genBlock = new ArrayList<>(1);
        genBlock.add(gens);

        // Registry filling
        int size = regList.getEntriesCount();
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (regList.isSelected(i)) {
                entries.add(new Entry(regList.getKeyAt(i), regList.getDescriptionAt(i), regList.getValueAt(i)));
            }
        }
        EntryList reg = new EntryList(entries);

        Configuration temp = new Configuration();
        temp.algorithms = algs;
        temp.genotypes = genBlock;
        temp.registry = reg;
        return temp;
    }

    /**
     * @return {@link AlgorithmSelection} from the selected
     * {@link ParametersSelection} panel
     */
    public EntryBlockSelection<EntryBlock> getAlgSel() {
        return algSel;
    }

    /**
     * @return {@link GenotypeSelection} from the selected
     * {@link ParametersSelection} panel
     */
    public EntryBlockSelection<EntryBlock> getGenSel() {
        return genSel;
    }

    /**
     * @return {@link EntryFieldPanel} representing Registry from the selected
     * {@link ParametersSelection} panel
     */
    public EntryListPanel getRegList() {
        return regList;
    }

    /**
     * @return Selected algorithm from the selected
     * {@link ParametersSelection} panel
     */
    public EntryBlock getSelectedAlgorithm() {
        return algSel.getSelectedItem();
    }

    /**
     * @return Selected genotype from the selected
     * {@link ParametersSelection} panel
     */
    public EntryBlock getSelectedGenotype() {
        return genSel.getSelectedItem();
    }

    /**
     * @return {@link DefinePanel} containing information about configuration
     * and log files paths
     */
    public DefinePanel getDefinePanel() {
        return definePanel;
    }

    @Override
    public void jobFinished(Job job, ProcessOutput output) {
        // String logFile = subject.getLogFilePath();
        // parent.getResultDisplay().displayLog(logFile);
        // subject.removeObserver();

        try {
            SwingUtilities.invokeAndWait(() -> {
                InputStream is = output.getStdout();
                try {
                    // TODO

//                    parent.getResultDisplay().displayLog(logFile);
                } catch (Exception e) {
                    LoggerProvider.getLogger().log(e);
                    parent.reportError(e.getMessage());
                }
            });
        } catch (InterruptedException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void jobFailed(Job job) {
    }

    /**
     * Panel used for grouping {@link AlgorithmSelection},
     * {@link GenotypeSelection} and {@link EntryFieldPanel} panels.
     *
     * @author Domagoj Stanković
     * @version 1.0
     */
    private static class TempPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        public TempPanel(Component algSel, Component genSel, Component regList) {
            super();
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            add(algSel);
            add(new JSeparator(JSeparator.VERTICAL));
            add(genSel);
            add(new JSeparator(JSeparator.VERTICAL));
            add(regList);
        }

    }

    /**
     * @return <code>true</code> if "Run" button was ever run before,
     * <code>false</code> otherwise
     */
    public boolean wasRunBefore() {
        return lastLogFilePath == null ? false : true;
    }

    /**
     * @return Path to the log file created during last experiment,
     * <code>null</code> if selected experiment was never run before
     */
    public String getLastLogFilePath() {
        return lastLogFilePath;
    }

}
