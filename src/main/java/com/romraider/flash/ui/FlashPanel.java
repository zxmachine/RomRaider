/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2026 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.romraider.flash.ui;

import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.INFORMATION_MESSAGE;
import static javax.swing.JOptionPane.WARNING_MESSAGE;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.apache.log4j.Logger;

import com.romraider.Settings;
import com.romraider.editor.ecu.ECUEditor;
import com.romraider.editor.ecu.ECUEditorManager;
import com.romraider.flash.BackupStore;
import com.romraider.flash.CancelToken;
import com.romraider.flash.EcuIdentity;
import com.romraider.flash.FlashBlock;
import com.romraider.flash.FlashContext;
import com.romraider.flash.FlashException;
import com.romraider.flash.FlashListener;
import com.romraider.flash.FlashMethod;
import com.romraider.flash.FlashMethods;
import com.romraider.flash.FlashOptions;
import com.romraider.flash.FlashSession;
import com.romraider.flash.Kernel;
import com.romraider.flash.KernelProfile;
import com.romraider.flash.Timing;
import com.romraider.flash.WritePolicy;
import com.romraider.flash.WriteRequest;
import com.romraider.flash.WriteResult;
import com.romraider.io.j2534.api.J2534;
import com.romraider.io.j2534.api.J2534Impl;
import com.romraider.io.j2534.api.J2534Library;
import com.romraider.io.j2534.api.J2534LibraryLocator;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.shell.Shell;
import com.romraider.shell.ShellView;
import com.romraider.util.SettingsManager;

/**
 * The content of the Flash view: adapter, kernel and folder choices, the
 * image to write, the steps at the car, the buttons Read, Compare and Write,
 * progress, battery voltage and a log that is also kept in a file.
 */
public final class FlashPanel extends JPanel {
    private static final long serialVersionUID = -3530839101536133591L;
    private static final Logger LOGGER = Logger.getLogger(FlashPanel.class);

    private static final String STEPS =
            "At the car\n\n"
            + "1. Connect the J2534 adapter to the OBD connector and to this computer. Close other programs"
            + " that use it (the logger, other flashing tools).\n"
            + "2. Connect the test-mode connector, and the car's flash connector, so that OBD pin 9 reaches the"
            + " ECU's reflash input and pin 11 its programming-voltage input.\n"
            + "3. Keep the battery charged; for a write, connect a charger or power supply. Lights, fan and"
            + " radio off.\n"
            + "4. Ignition ON, engine off.\n"
            + "5. Press Read, Compare or Write. When asked, turn the ignition OFF, wait 3 seconds and turn it"
            + " ON again: RomRaider then enters the boot loader and starts the kernel.\n\n"
            + "Afterwards the ECU stays in the kernel: disconnect the test-mode connector and turn the ignition"
            + " off and on to start it normally.\n\n"
            + "Compare is a dry run: it writes nothing. Write is off until you allow it under Settings; each"
            + " write first saves a whole read of the ECU, writes only the blocks that differ and asks you to"
            + " type WRITE and the ROM ID.\n\n"
            + "If a write stops part way: leave everything connected and press Write again.\n\n"
            + "None of this has been tried on a car yet. See docs/flashing.md.";

    private final JComboBox<AdapterChoice> adapter = new JComboBox<AdapterChoice>();
    private final JTextField kernelFile = new JTextField(30);
    private final JComboBox<KernelProfile> profile = new JComboBox<KernelProfile>();
    private final JTextField folder = new JTextField(30);
    private final JLabel imageLabel = new JLabel("No image");
    private final JButton fixChecksum = new JButton("Fix checksum");
    private final JButton fromEditor = new JButton("From the editor");
    private final JButton openFile = new JButton("Open file...");
    private final JCheckBox writing = new JCheckBox("Allow writing to the ECU (off at every start)");
    private final JCheckBox allowOtherId = new JCheckBox("Allow another ROM ID");
    private final JCheckBox allowUnknownKernel = new JCheckBox("Allow an unknown kernel (off at every start)");
    private final JTextField minBattery = new JTextField(5);
    private final JLabel instruction = new JLabel(" ");
    private final JTextArea log = new JTextArea();
    private final JButton read = new JButton("Read the ECU into a new image");
    private final JButton compare = new JButton("Compare (dry run)");
    private final JButton write = new JButton();
    private final JButton stopButton = new JButton("Stop");
    private final JProgressBar progress = new JProgressBar();
    private final JLabel battery = new JLabel("Battery: -");

    private final Settings settings = SettingsManager.getSettings();
    private BackupStore backups;
    /** Where the image to compare and write comes from; taken again at each Compare and Write. */
    private ImageSource source;
    /** The image as last taken, for the label. */
    private ImageSource.Snapshot shown;
    private volatile boolean busy;
    private volatile CancelToken cancel;
    private volatile boolean kernelMayBeRunning;
    private Thread worker;

    public FlashPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        name(adapter, "flash.adapter");
        name(kernelFile, "flash.kernel");
        name(profile, "flash.profile");
        name(folder, "flash.folder");
        name(writing, "flash.writing");
        name(allowOtherId, "flash.allowOtherId");
        name(allowUnknownKernel, "flash.allowUnknownKernel");
        allowUnknownKernel.setToolTipText("<html>RomRaider uploads only kernels whose code it knows: they erase and"
                + " program only the application flash.<br>Another kernel could erase the boot loader, and the ECU"
                + " could then not be written again over the K-line.</html>");
        name(read, "flash.read");
        name(compare, "flash.compare");
        name(write, "flash.write");
        name(stopButton, "flash.stop");
        name(log, "flash.log");
        name(fixChecksum, "flash.fixChecksum");
        name(fromEditor, "flash.fromEditor");
        name(openFile, "flash.openFile");
        name(minBattery, "flash.minBattery");
        name(imageLabel, "flash.image");

        add(form(), BorderLayout.NORTH);
        add(middle(), BorderLayout.CENTER);
        add(buttons(), BorderLayout.SOUTH);

        loadSettings();
        updateWriteButton();
        setBusy(false);
        refreshImageFromEditor(false);
    }

    private static void name(JComponent c, String n) {
        c.setName(n);
    }

    // ---- layout

    private JPanel form() {
        JPanel p = new JPanel(new GridBagLayout());
        int row = 0;
        row = row(p, row, "Adapter:", adapter, button("Refresh", new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { fillAdapters(); }
        }));
        row = row(p, row, "Kernel file:", kernelFile, button("Browse...", new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { chooseFile(kernelFile, false); }
        }));
        row = row(p, row, "Kernel profile:", profile, allowUnknownKernel);
        row = row(p, row, "Folder for reads and logs:", folder, button("Browse...", new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { chooseFile(folder, true); }
        }));

        JPanel imageButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        fromEditor.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { refreshImageFromEditor(true); }
        });
        imageButtons.add(fromEditor);
        openFile.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { openImageFile(); }
        });
        imageButtons.add(openFile);
        fixChecksum.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { fixImageChecksum(); }
        });
        imageButtons.add(fixChecksum);
        row = row(p, row, "Image to write:", imageLabel, imageButtons);

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        options.add(writing);
        options.add(allowOtherId);
        options.add(new JLabel("  Battery minimum (V):"));
        options.add(minBattery);
        row = row(p, row, "Settings:", options, null);

        for (KernelProfile kp : KernelProfile.all()) profile.addItem(kp);
        writing.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                updateWriteButton();     // never saved: writing is off at every start
            }
        });
        return p;
    }

    private static int row(JPanel p, int row, String label, JComponent field, JComponent extra) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(2, 2, 2, 6);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        p.add(new JLabel(label), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        p.add(field, c);
        if (extra != null) {
            c.gridx = 2;
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;
            p.add(extra, c);
        }
        return row + 1;
    }

    private static JButton button(String text, ActionListener l) {
        JButton b = new JButton(text);
        b.addActionListener(l);
        return b;
    }

    private JComponent middle() {
        JTextArea steps = new JTextArea(STEPS);
        steps.setEditable(false);
        steps.setLineWrap(true);
        steps.setWrapStyleWord(true);
        steps.setBackground(getBackground());
        steps.setFont(new JLabel().getFont());
        JPanel left = new JPanel(new BorderLayout(4, 4));
        instruction.setFont(instruction.getFont().deriveFont(Font.BOLD));
        instruction.setForeground(new Color(0xB0, 0x30, 0x00));
        left.add(instruction, BorderLayout.NORTH);
        left.add(new JScrollPane(steps), BorderLayout.CENTER);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(new JLabel("Log"), BorderLayout.NORTH);
        right.add(new JScrollPane(log), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.35);
        split.setDividerLocation(360);
        return split;
    }

    private JPanel buttons() {
        JPanel p = new JPanel(new BorderLayout(8, 4));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        read.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { startRead(); }
        });
        compare.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { startWrite(true); }
        });
        write.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { startWrite(false); }
        });
        stopButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                CancelToken c = cancel;
                if (c != null) {
                    c.cancel();
                    appendLog("stop asked: the session stops at the next safe point (never while erasing or programming)");
                }
            }
        });
        left.add(read);
        left.add(compare);
        left.add(write);
        left.add(stopButton);
        p.add(left, BorderLayout.WEST);
        progress.setStringPainted(true);
        progress.setString("");
        p.add(progress, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.add(battery);
        right.add(button("Save log...", new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { saveLogAs(); }
        }));
        p.add(right, BorderLayout.EAST);
        return p;
    }

    // ---- settings

    private void loadSettings() {
        fillAdapters();
        kernelFile.setText(settings.getFlashKernel());
        profile.setSelectedItem(KernelProfile.byId(settings.getFlashKernelProfile()));
        folder.setText(settings.getFlashFolder().length() > 0 ? settings.getFlashFolder() : defaultFolder().getPath());
        writing.setSelected(false);     // off at every start, whatever was ticked before
        minBattery.setText(String.valueOf(settings.getFlashMinBattery()));
    }

    private void storeSettings() {
        settings.setFlashKernel(kernelFile.getText().trim());
        KernelProfile kp = (KernelProfile) profile.getSelectedItem();
        settings.setFlashKernelProfile(kp == null ? "" : kp.getId());
        settings.setFlashFolder(folder.getText().trim());
        double min = minBatteryVolts();
        if (!Double.isNaN(min)) settings.setFlashMinBattery(min);
        AdapterChoice a = (AdapterChoice) adapter.getSelectedItem();
        if (a != null && a.library != null) settings.setFlashAdapter(a.library);
    }

    private double minBatteryVolts() {
        return parseMinBattery(minBattery.getText());
    }

    /**
     * The battery minimum typed in: at least 11.0 V; NaN for anything that is
     * not a finite number, which refuses a write.
     */
    static double parseMinBattery(String text) {
        try {
            double v = Double.parseDouble(text == null ? "" : text.trim());
            if (Double.isNaN(v) || Double.isInfinite(v)) return Double.NaN;
            return Math.max(11.0, v);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static File defaultFolder() {
        return new File(new File(System.getProperty("user.home"), ".RomRaider"), "flash");
    }

    private void fillAdapters() {
        adapter.removeAllItems();
        String extra = System.getProperty(FlashAdapterProvider.PROPERTY);
        if (extra != null && extra.length() > 0) {
            try {
                FlashAdapterProvider p = (FlashAdapterProvider) Class.forName(extra).getDeclaredConstructor().newInstance();
                adapter.addItem(new AdapterChoice(p.getName(), null, p));
            } catch (Exception e) {
                LOGGER.warn("Cannot load the adapter " + extra, e);
            }
        }
        try {
            for (J2534Library lib : J2534LibraryLocator.getLibraries("ISO9141")) {
                AdapterChoice c = new AdapterChoice(lib.getVendor() + " (" + lib.getLibrary() + ")", lib.getLibrary(), null);
                adapter.addItem(c);
                if (lib.getLibrary().equals(settings.getFlashAdapter())) adapter.setSelectedItem(c);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Cannot list the J2534 libraries", e);
        }
        if (adapter.getItemCount() == 0) adapter.addItem(new AdapterChoice("(no J2534 adapter found)", null, null));
    }

    private void chooseFile(JTextField field, boolean directory) {
        JFileChooser fc = new JFileChooser(field.getText());
        fc.setFileSelectionMode(directory ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(fc.getSelectedFile().getPath());
            storeSettings();
        }
    }

    private void updateWriteButton() {
        if (writing.isSelected()) {
            write.setText("Write this image");
            write.setToolTipText("Writes the blocks that differ, after a backup read and your typed confirmation");
        } else {
            write.setText("Write (switched off: see Settings)");
            write.setToolTipText(WritePolicy.HOW_TO_ENABLE);
        }
    }

    private void setBusy(boolean b) {
        busy = b;
        read.setEnabled(!b);
        compare.setEnabled(!b);
        write.setEnabled(!b);
        fixChecksum.setEnabled(!b);
        fromEditor.setEnabled(!b);
        openFile.setEnabled(!b);
        stopButton.setEnabled(b);
        if (!b) instruction.setText(" ");
    }

    public boolean isBusy() {
        return busy;
    }

    // ---- the image

    /**
     * Makes the editor's selected ROM the image to write; it is taken again
     * at each Compare and Write. With ask, says so when there is none.
     */
    void refreshImageFromEditor(boolean ask) {
        if (busy || (!ask && source != null)) return;
        if (editorRom() == null) {
            if (ask) JOptionPane.showMessageDialog(this, "No ROM is open in the editor.", "Flash", INFORMATION_MESSAGE);
            return;
        }
        source = ImageSource.following(new ImageSource.Live() {
            @Override public byte[] bytes() {
                Rom rom = editorRom();
                return rom == null ? null : rom.getBinary();
            }
            @Override public String name() {
                Rom rom = editorRom();
                return "the editor (" + (rom == null ? "no ROM" : rom.getFileName()) + ")";
            }
            @Override public String flashMethod() {
                Rom rom = editorRom();
                RomID id = rom == null ? null : rom.getRomID();
                return id == null ? null : id.getFlashMethod();
            }
            @Override public String memoryModel() {
                Rom rom = editorRom();
                RomID id = rom == null ? null : rom.getRomID();
                return id == null ? null : id.getMemModel();
            }
        });
        takeImage();
    }

    /** The view is shown again: take the editor's ROM if there is no image yet, else show the image as it is now. */
    void activated() {
        if (busy) return;
        if (source == null) refreshImageFromEditor(false);
        else takeImage();
    }

    private static Rom editorRom() {
        ECUEditor editor = ECUEditorManager.getECUEditorWithoutCreation();
        Rom rom = editor == null ? null : editor.getLastSelectedRom();
        return rom == null || rom.getBinary() == null ? null : rom;
    }

    private void openImageFile() {
        JFileChooser fc = new JFileChooser(folder.getText());
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            File f = fc.getSelectedFile();
            setImage(Files.readAllBytes(f.toPath()), f.getName(), null, null);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot read the file: " + e.getMessage(), "Flash", ERROR_MESSAGE);
        }
    }

    /** Sets a fixed image to compare and write. */
    public void setImage(byte[] data, String name, String flashMethod, String memoryModel) {
        source = ImageSource.fixed(data, name, flashMethod, memoryModel);
        takeImage();
    }

    /** Takes the image as it is now and shows it; null when there is none. */
    private ImageSource.Snapshot takeImage() {
        shown = source == null ? null : source.take();
        describeImage();
        return shown;
    }

    private void describeImage() {
        if (shown == null) {
            imageLabel.setText("No image");
            return;
        }
        FlashMethod m = method(shown);
        if (m == null) {
            imageLabel.setText(String.format("From %s: flash method %s / %s is not supported here",
                    shown.name, shown.flashMethod, shown.memoryModel));
            return;
        }
        List<String> problems = m.imageProblems(shown.bytes);
        String state = problems.isEmpty() ? "checks passed" : problems.get(0);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(shown.bytes);
        imageLabel.setText(String.format("%s from %s, %d bytes, CRC-32 %08X, taken %s: %s", m.romId(shown.bytes),
                shown.name, shown.bytes.length, crc.getValue(), new SimpleDateFormat("HH:mm:ss").format(new Date()),
                state));
    }

    private static FlashMethod method(ImageSource.Snapshot s) {
        if (s.flashMethod == null && s.memoryModel == null) return FlashMethods.all().get(0);
        return FlashMethods.forRom(s.flashMethod, s.memoryModel);
    }

    private void fixImageChecksum() {
        ImageSource.Snapshot s = takeImage();
        FlashMethod m = s == null ? null : method(s);
        if (m == null) return;
        boolean changed = source.fixChecksum(m);
        appendLog(changed ? "checksum fixed in " + s.name + (s.name.startsWith("the editor")
                ? ": save the ROM in the editor to keep it" : "") : "the checksum was already right");
        takeImage();
    }

    // ---- actions

    private void startRead() {
        final FlashMethod m = FlashMethods.all().get(0);
        run("Read", new Job() {
            @Override
            public void run(FlashSession s) throws FlashException {
                EcuIdentity id = s.identify();
                byte[] data = s.read();
                final File f = backups.save(data, id.getRomId(), m.contentKey(data), "read");
                appendLog("saved the read: " + f);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        openInEditor(f);
                    }
                });
            }
        }, m);
    }

    private void openInEditor(File f) {
        ECUEditor editor = ECUEditorManager.getECUEditorWithoutCreation();
        if (editor == null) return;
        Shell.showView(ShellView.EDITOR);
        editor.openImage(f);
    }

    private void startWrite(final boolean dryRun) {
        if (!dryRun && !writing.isSelected()) {
            JOptionPane.showMessageDialog(this, WritePolicy.HOW_TO_ENABLE, "Writing is switched off", INFORMATION_MESSAGE);
            return;
        }
        // the image as it is now (the editor's ROM is taken again here, with any edits since)
        final ImageSource.Snapshot s = takeImage();
        if (s == null) {
            JOptionPane.showMessageDialog(this, "Choose an image first: from the editor or from a file.",
                    "Flash", INFORMATION_MESSAGE);
            return;
        }
        final FlashMethod m = method(s);
        if (m == null) {
            JOptionPane.showMessageDialog(this, "This image's flash method is not supported here.", "Flash", ERROR_MESSAGE);
            return;
        }
        appendLog("image to " + (dryRun ? "compare" : "write") + ": " + imageLabel.getText());
        final WriteRequest request = dryRun ? WriteRequest.dryRun(s.bytes)
                : new WriteRequest(s.bytes, s.flashMethod, s.memoryModel, false, allowOtherId.isSelected(), confirmer());
        try {
            WritePolicy.preflight(m, options(), request);
        } catch (FlashException e) {
            appendLog(e.describe());
            JOptionPane.showMessageDialog(this, e.describe(), dryRun ? "Compare" : "Write", WARNING_MESSAGE);
            return;
        }
        run(dryRun ? "Compare" : "Write", new Job() {
            @Override
            public void run(FlashSession s) throws FlashException {
                WriteResult r = s.write(request);
                switch (r.getOutcome()) {
                    case NOTHING_TO_WRITE:
                        appendLog("RESULT: the ECU already holds this image");
                        break;
                    case DRY_RUN:
                        appendLog("RESULT: dry run, " + r.getBlocks().size() + " block(s) differ; nothing written");
                        break;
                    default:
                        appendLog("RESULT: " + r.getBlocks().size() + " block(s) written and verified: flash correct");
                        break;
                }
            }
        }, m);
    }

    private WriteRequest.Confirmer confirmer() {
        return new WriteRequest.Confirmer() {
            @Override
            public String confirm(final List<FlashBlock> blocks, final String romId, final String image,
                    final String phrase) {
                final AtomicReference<String> typed = new AtomicReference<String>();
                try {
                    SwingUtilities.invokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            StringBuilder names = new StringBuilder();
                            for (FlashBlock b : blocks) names.append(b.getName()).append(' ');
                            String msg = String.format("Image: %s%n%n%d block(s) will be erased and written: %s%n"
                                    + "Once it starts, the write can't be stopped, and RomRaider can't be closed"
                                    + " until it ends.%n%nType %s to write:",
                                    image, blocks.size(), names.toString().trim(), phrase);
                            typed.set(JOptionPane.showInputDialog(FlashPanel.this, msg, "Confirm the write",
                                    WARNING_MESSAGE));
                        }
                    });
                } catch (Exception e) {
                    return null;
                }
                return typed.get();
            }
        };
    }

    private FlashOptions options() {
        FlashOptions o = new FlashOptions();
        o.writingEnabled = writing.isSelected();
        o.minBatteryVolts = minBatteryVolts();
        o.kernelMayBeRunning = kernelMayBeRunning;
        o.allowUnknownKernel = allowUnknownKernel.isSelected();     // never saved
        return o;
    }

    private interface Job {
        void run(FlashSession session) throws FlashException;
    }

    private void run(final String what, final Job job, final FlashMethod m) {
        if (busy) return;
        storeSettings();
        final AdapterChoice choice = (AdapterChoice) adapter.getSelectedItem();
        final KernelProfile kp = (KernelProfile) profile.getSelectedItem();
        final File kernelPath = new File(kernelFile.getText().trim());
        final File dir = new File(folder.getText().trim().length() > 0 ? folder.getText().trim() : defaultFolder().getPath());
        if (backups == null || !backups.getFolder().equals(dir)) backups = new BackupStore(dir);
        final FlashOptions opts = options();
        final CancelToken token = new CancelToken();
        cancel = token;
        setBusy(true);
        progress.setValue(0);
        progress.setString(what);
        appendLog("---- " + what + " " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                FlashSession session = null;
                try {
                    if (choice == null || !choice.usable()) {
                        throw new FlashException("No J2534 adapter is chosen.",
                                "Install the adapter's driver, plug it in and press Refresh.");
                    }
                    Kernel kernel = Kernel.load(kernelPath, kp);
                    appendLog(String.format("kernel %s (%d bytes), %s", kernel.getSource(), kernel.size(), kp.getName()));
                    J2534 api = choice.create();
                    FlashContext ctx = new FlashContext(api, kernel, opts, backups, Timing.SYSTEM, listener(), token);
                    session = m.open(ctx);
                    job.run(session);
                } catch (FlashException e) {
                    appendLog(e.describe());
                    showLater(e);
                } catch (RuntimeException e) {
                    LOGGER.error("Flash " + what + " failed", e);
                    appendLog("ERROR: " + e);
                    showLater(new FlashException("Unexpected error: " + e, "See the log; turn the ignition off and try again."));
                } finally {
                    if (session != null) {
                        session.close();
                        kernelMayBeRunning = true;
                    }
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(false);
                            progress.setString(what + " ended");
                        }
                    });
                }
            }
        }, "Flash " + what);
        worker.setDaemon(false);     // the program does not end under a running session
        worker.start();
    }

    private void showLater(final FlashException e) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                boolean partly = e.getFlashState() == FlashException.FlashState.PARTLY_WRITTEN;
                JOptionPane.showMessageDialog(FlashPanel.this, e.describe(),
                        partly ? "WRITE STOPPED: the ECU is partly written" : "Flash", partly ? ERROR_MESSAGE : WARNING_MESSAGE);
            }
        });
    }

    private FlashListener listener() {
        return new FlashListener() {
            @Override
            public void log(String line) {
                appendLog(line);
            }

            @Override
            public void progress(final String task, final int done, final int total) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        progress.setMaximum(Math.max(1, total));
                        progress.setValue(done);
                        progress.setString(task);
                    }
                });
            }

            @Override
            public void instruction(final String text) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        instruction.setText("<html>NOW: " + text + "</html>");
                    }
                });
            }

            @Override
            public void battery(final double volts) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        battery.setText(String.format("Battery: %.2f V", volts));
                    }
                });
            }
        };
    }

    // ---- the log

    private void appendLog(final String line) {
        writeLogFile(line);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                log.append(line + "\n");
                log.setCaretPosition(log.getDocument().getLength());
            }
        });
    }

    private synchronized void writeLogFile(String line) {
        File dir = backups != null ? backups.getFolder() : null;
        if (dir == null) return;
        File f = new File(dir, "flash-log-" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + ".txt");
        try {
            Files.createDirectories(dir.toPath());
            PrintWriter w = new PrintWriter(new FileWriter(f, true));
            try {
                w.println(line);
            } finally {
                w.close();
            }
        } catch (IOException e) {
            LOGGER.warn("Cannot write the flash log " + f, e);
        }
    }

    private void saveLogAs() {
        JFileChooser fc = new JFileChooser(folder.getText());
        fc.setSelectedFile(new File(folder.getText(), "flash-log.txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Files.write(fc.getSelectedFile().toPath(), log.getText().getBytes("UTF-8"));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot save the log: " + e.getMessage(), "Flash", ERROR_MESSAGE);
        }
    }

    /** The view stops: keep the settings, stop a running session at its next safe point. */
    void stop() {
        storeSettings();
        CancelToken c = cancel;
        if (busy && c != null) c.cancel();
    }

    /** One entry of the adapter list. */
    private static final class AdapterChoice {
        final String name;
        final String library;
        final FlashAdapterProvider provider;

        AdapterChoice(String name, String library, FlashAdapterProvider provider) {
            this.name = name;
            this.library = library;
            this.provider = provider;
        }

        boolean usable() {
            return library != null || provider != null;
        }

        J2534 create() {
            if (provider != null) return provider.create();
            return new J2534Impl(J2534Impl.Protocol.ISO9141, library);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
