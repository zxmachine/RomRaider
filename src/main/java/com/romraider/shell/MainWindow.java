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

package com.romraider.shell;

import static com.romraider.Version.PRODUCT_NAME;

import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.log4j.Logger;

import com.romraider.Settings;
import com.romraider.util.ResourceUtil;
import com.romraider.util.SettingsManager;

/**
 * The one main window: a tab per view, the active view's content below the
 * tabs, and a menu bar of the active view's menus beside the common Window
 * menu. Ctrl+1, Ctrl+2, ... switch views.
 */
public final class MainWindow extends JFrame {
    private static final long serialVersionUID = 2216523946815413017L;
    private static final Logger LOGGER = Logger.getLogger(MainWindow.class);
    static final ResourceBundle rb = new ResourceUtil().getBundle(MainWindow.class.getName());

    private final ViewRegistry registry;
    private final String[] startArgs;
    private final JTabbedPane tabs = new JTabbedPane();
    private final Map<ShellView, JPanel> holders = new HashMap<ShellView, JPanel>();
    private final Map<ShellView, JRadioButtonMenuItem> viewItems = new HashMap<ShellView, JRadioButtonMenuItem>();
    private final JMenu windowMenu = new JMenu(rb.getString("WINDOW"));
    private final JMenuBar bar = new JMenuBar();
    private boolean closing;

    MainWindow(ViewRegistry registry, String[] startArgs) {
        super(PRODUCT_NAME);
        this.registry = registry;
        this.startArgs = startArgs == null ? new String[0] : startArgs.clone();

        setIconImage(new ImageIcon(getClass().getResource("/graphics/romraider-ico.gif")).getImage());
        windowMenu.setMnemonic(KeyEvent.VK_W);

        ButtonGroup group = new ButtonGroup();
        final List<ShellView> views = registry.getViews();
        for (int i = 0; i < views.size(); i++) {
            final ShellView view = views.get(i);
            JPanel holder = new JPanel(new BorderLayout());
            holder.add(placeholder(view), BorderLayout.CENTER);
            holders.put(view, holder);
            tabs.addTab(view.getName(), holder);
            if (i < 9) tabs.setMnemonicAt(i, KeyEvent.VK_1 + i);

            JRadioButtonMenuItem item = new JRadioButtonMenuItem(view.getName());
            item.setAccelerator(ViewRegistry.shortcut(i));
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    showView(view.getId());
                }
            });
            group.add(item);
            windowMenu.add(item);
            viewItems.put(view, item);
        }
        tabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                int i = tabs.getSelectedIndex();
                if (i >= 0) activate(views.get(i));
            }
        });
        getContentPane().add(tabs, BorderLayout.CENTER);
        setJMenuBar(bar);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                close();
            }
        });

        Settings settings = SettingsManager.getSettings();
        setSize(settings.getWindowSize());
        setLocation(settings.getWindowLocation());
        if (settings.isWindowMaximized()) setExtendedState(MAXIMIZED_BOTH);
    }

    public ViewRegistry getRegistry() {
        return registry;
    }

    /** Switches to the view with this id, starting it the first time. On the event thread. */
    public void showView(String id) {
        int index = registry.indexOf(id);
        if (index < 0) {
            LOGGER.warn("No view with id " + id);
            return;
        }
        if (tabs.getSelectedIndex() != index) {
            tabs.setSelectedIndex(index);   // the change listener activates it
        } else {
            activate(registry.getViews().get(index));
        }
    }

    /** A started view has its content and menus: show them. On the event thread. */
    public void viewReady(ShellView view) {
        JPanel holder = holders.get(view);
        holder.removeAll();
        holder.add(view.getContent(), BorderLayout.CENTER);
        holder.revalidate();
        holder.repaint();
        if (registry.getActive() == view) {
            view.activated();
            refreshMenusAndTitle();
        }
    }

    /** A view's title changed; any thread. */
    public void titleChanged(final ShellView view) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (registry.getActive() == view) setTitle(titleOf(view));
            }
        });
    }

    /** Brings the window to the front, out of the task bar if it was minimised. */
    public void bringToFront() {
        if ((getExtendedState() & ICONIFIED) != 0) setExtendedState(getExtendedState() & ~ICONIFIED);
        toFront();
        requestFocus();
    }

    public boolean isClosing() {
        return closing;
    }

    /**
     * Closes every view and ends the program: the started views stop with
     * the settings saves held, then the base view stops and saves the
     * settings once.
     */
    public void close() {
        if (closing) return;
        String busy = registry.busyReason();
        if (busy != null) {
            javax.swing.JOptionPane.showMessageDialog(this, busy, PRODUCT_NAME,
                    javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        closing = true;
        ShellView base = registry.getBase();
        List<ShellView> order = registry.closeOrder();
        SettingsManager.holdSaves(true);
        try {
            for (ShellView view : order) {
                if (view != base) stopQuietly(view);
            }
        } finally {
            SettingsManager.holdSaves(false);
        }
        if (base != null) stopQuietly(base);
        dispose();
        System.exit(0);
    }

    private void stopQuietly(ShellView view) {
        try {
            view.stop(this);
        } catch (RuntimeException e) {
            LOGGER.warn("Error stopping the " + view.getId() + " view", e);
        }
    }

    private void activate(ShellView view) {
        registry.setActive(view);
        viewItems.get(view).setSelected(true);
        if (registry.markStarted(view)) {
            view.start(this, startArgs);
        } else if (view.getContent() != null) {
            view.activated();
        }
        refreshMenusAndTitle();
    }

    private void refreshMenusAndTitle() {
        ShellView view = registry.getActive();
        List<JMenu> own = view != null && view.getContent() != null
                ? view.getMenus() : Collections.<JMenu>emptyList();
        bar.removeAll();
        for (JMenu menu : MenuLayout.arrange(own, Collections.singletonList(windowMenu))) {
            bar.add(menu);
        }
        bar.revalidate();
        bar.repaint();
        setTitle(titleOf(view));
    }

    private String titleOf(ShellView view) {
        if (view == null) return PRODUCT_NAME;
        String title = view.getContent() == null ? null : view.getTitle();
        return title == null || title.length() == 0 ? PRODUCT_NAME + " - " + view.getName() : title;
    }

    private static JComponent placeholder(ShellView view) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel label = new JLabel(MessageFormat.format(rb.getString("STARTING"), view.getName()));
        label.setAlignmentX(0.5f);
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setAlignmentX(0.5f);
        box.add(label);
        box.add(progress);
        JPanel centred = new JPanel(new GridBagLayout());
        centred.add(box);
        return centred;
    }
}
