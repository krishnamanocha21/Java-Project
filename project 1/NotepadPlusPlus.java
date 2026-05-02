import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * NotepadPlusPlus.java
 * 
 * A fully-featured Notepad++ Clone built with Java Swing.
 * 
 * Mini Project — CIC212 | 4th Semester
 * Department of Information Technology
 * Maharaja Surajmal Institute of Technology
 * 
 * Features:
 *  - Multi-tab document editing (JTabbedPane)
 *  - File operations: New, Open, Save, Save As
 *  - Edit operations: Cut, Copy, Paste, Select All, Undo, Redo
 *  - Font Customizer Dialog
 *  - Line Number Gutter (custom component)
 *  - Real-time Status Bar (Length, Lines, Ln, Col)
 *  - Toolbar with icon-style buttons
 *  - Keyboard shortcuts (Ctrl+N/O/S/Z/Y/A etc.)
 *  - Unsaved-change indicators on tabs (asterisk prefix)
 *  - System native Look & Feel
 */
public class NotepadPlusPlus extends JFrame {

    // ─────────────────────────── Constants ────────────────────────────────
    private static final String APP_TITLE   = "Notepad++ Clone";
    private static final String APP_VERSION = "v1.0";
    private static final int    WIN_WIDTH   = 1100;
    private static final int    WIN_HEIGHT  = 720;

    // ───────────────────────── UI Components ──────────────────────────────
    private final JTabbedPane tabbedPane;
    private final JLabel statusLength;
    private final JLabel statusLines;
    private final JLabel statusLn;
    private final JLabel statusCol;
    private final JLabel statusEncoding;
    private final JLabel statusEOL;

    // Track open files per tab
    private final List<File>           openFiles    = new ArrayList<>();
    private final List<UndoManager>    undoManagers = new ArrayList<>();
    private final List<Boolean>        modifiedFlags = new ArrayList<>();

    // ──────────────────────────── Entry Point ─────────────────────────────
    public static void main(String[] args) {
        // Apply system native Look and Feel for OS-native widget rendering
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Gracefully fall back to default Swing L&F if unavailable
            System.err.println("System L&F unavailable, using default.");
        }
        SwingUtilities.invokeLater(() -> new NotepadPlusPlus().setVisible(true));
    }

    // ─────────────────────────── Constructor ──────────────────────────────
    public NotepadPlusPlus() {
        super(APP_TITLE + " — " + APP_VERSION);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(WIN_WIDTH, WIN_HEIGHT);
        setLocationRelativeTo(null);
        setIconImage(buildAppIcon());

        // Intercept close to prompt save on unsaved tabs
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { exitApplication(); }
        });

        // ── Tabbed Pane (central document area) ──
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        // ── Status Bar labels ──
        statusLength   = makeStatusLabel("Length: 0");
        statusLines    = makeStatusLabel("Lines: 1");
        statusLn       = makeStatusLabel("Ln: 1");
        statusCol      = makeStatusLabel("Col: 1");
        statusEncoding = makeStatusLabel("UTF-8");
        statusEOL      = makeStatusLabel("Windows (CR LF)");

        // Assemble the frame
        setJMenuBar(buildMenuBar());
        add(buildToolBar(),   BorderLayout.NORTH);
        add(tabbedPane,       BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        // Open a default blank tab on startup
        createNewTab("new 1", null);

        // Refresh status whenever the active tab changes
        tabbedPane.addChangeListener(e -> refreshStatusBar());
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MENU BAR
    // ══════════════════════════════════════════════════════════════════════
    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        mb.add(buildFileMenu());
        mb.add(buildEditMenu());
        mb.add(buildFormatMenu());
        mb.add(buildViewMenu());
        mb.add(buildHelpMenu());
        return mb;
    }

    // ── File Menu ──────────────────────────────────────────────────────────
    private JMenu buildFileMenu() {
        JMenu menu = new JMenu("File");
        menu.setMnemonic(KeyEvent.VK_F);

        menu.add(makeMenuItem("New",      KeyEvent.VK_N, KeyEvent.VK_N,   e -> fileNew()));
        menu.add(makeMenuItem("Open…",    KeyEvent.VK_O, KeyEvent.VK_O,   e -> fileOpen()));
        menu.addSeparator();
        menu.add(makeMenuItem("Save",     KeyEvent.VK_S, KeyEvent.VK_S,   e -> fileSave()));
        menu.add(makeMenuItemShift("Save As…", KeyEvent.VK_S,             e -> fileSaveAs()));
        menu.addSeparator();
        menu.add(makeMenuItem("Close Tab",KeyEvent.VK_W, KeyEvent.VK_W,   e -> closeCurrentTab()));
        menu.addSeparator();
        menu.add(makeMenuItem("Exit",     KeyEvent.VK_Q, KeyEvent.VK_Q,   e -> exitApplication()));

        return menu;
    }

    // ── Edit Menu ──────────────────────────────────────────────────────────
    private JMenu buildEditMenu() {
        JMenu menu = new JMenu("Edit");
        menu.setMnemonic(KeyEvent.VK_E);

        menu.add(makeMenuItem("Undo",       KeyEvent.VK_Z, KeyEvent.VK_Z, e -> editUndo()));
        menu.add(makeMenuItem("Redo",       KeyEvent.VK_Y, KeyEvent.VK_Y, e -> editRedo()));
        menu.addSeparator();
        menu.add(makeMenuItem("Cut",        KeyEvent.VK_X, KeyEvent.VK_X, e -> activeArea().cut()));
        menu.add(makeMenuItem("Copy",       KeyEvent.VK_C, KeyEvent.VK_C, e -> activeArea().copy()));
        menu.add(makeMenuItem("Paste",      KeyEvent.VK_V, KeyEvent.VK_V, e -> activeArea().paste()));
        menu.addSeparator();
        menu.add(makeMenuItem("Select All", KeyEvent.VK_A, KeyEvent.VK_A, e -> activeArea().selectAll()));
        menu.addSeparator();
        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(e -> {
            JTextArea ta = activeArea();
            if (ta.getSelectedText() != null) ta.replaceSelection("");
        });
        menu.add(deleteItem);

        return menu;
    }

    // ── Format Menu ────────────────────────────────────────────────────────
    private JMenu buildFormatMenu() {
        JMenu menu = new JMenu("Format");
        menu.setMnemonic(KeyEvent.VK_O);

        JMenuItem fontItem = new JMenuItem("Font…");
        fontItem.addActionListener(e -> showFontChooserDialog());
        menu.add(fontItem);

        JCheckBoxMenuItem wordWrap = new JCheckBoxMenuItem("Word Wrap");
        wordWrap.addActionListener(e -> {
            JTextArea ta = activeArea();
            if (ta != null) {
                ta.setLineWrap(wordWrap.isSelected());
                ta.setWrapStyleWord(wordWrap.isSelected());
            }
        });
        menu.add(wordWrap);
        return menu;
    }

    // ── View Menu ──────────────────────────────────────────────────────────
    private JMenu buildViewMenu() {
        JMenu menu = new JMenu("View");
        menu.setMnemonic(KeyEvent.VK_V);
        JCheckBoxMenuItem statusBarItem = new JCheckBoxMenuItem("Status Bar", true);
        statusBarItem.addActionListener(e -> {
            Component sb = ((BorderLayout) getContentPane().getLayout())
                    .getLayoutComponent(BorderLayout.SOUTH);
            if (sb != null) sb.setVisible(statusBarItem.isSelected());
        });
        menu.add(statusBarItem);
        return menu;
    }

    // ── Help Menu ──────────────────────────────────────────────────────────
    private JMenu buildHelpMenu() {
        JMenu menu = new JMenu("Help");
        menu.setMnemonic(KeyEvent.VK_H);
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "<html><b>Notepad++ Clone</b><br>" +
                "Mini Project — CIC212<br>" +
                "4th Semester | IT Department<br>" +
                "Maharaja Surajmal Institute of Technology<br><br>" +
                "Built with Java Swing / AWT</html>",
                "About", JOptionPane.INFORMATION_MESSAGE));
        menu.add(about);
        return menu;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TOOLBAR
    // ══════════════════════════════════════════════════════════════════════
    private JToolBar buildToolBar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(180, 180, 180)));
        tb.setBackground(new Color(240, 240, 240));

        tb.add(makeToolButton("New",   "📄", e -> fileNew()));
        tb.add(makeToolButton("Open",  "📂", e -> fileOpen()));
        tb.add(makeToolButton("Save",  "💾", e -> fileSave()));
        tb.addSeparator();
        tb.add(makeToolButton("Cut",   "✂", e -> activeArea().cut()));
        tb.add(makeToolButton("Copy",  "📋", e -> activeArea().copy()));
        tb.add(makeToolButton("Paste", "📌", e -> activeArea().paste()));
        tb.addSeparator();
        tb.add(makeToolButton("Undo",  "↩", e -> editUndo()));
        tb.add(makeToolButton("Redo",  "↪", e -> editRedo()));
        tb.addSeparator();
        tb.add(makeToolButton("Font",  "Aa", e -> showFontChooserDialog()));

        return tb;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STATUS BAR
    // ══════════════════════════════════════════════════════════════════════
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(180, 180, 180)));
        bar.setBackground(new Color(240, 240, 240));
        bar.setPreferredSize(new Dimension(0, 22));

        bar.add(wrapStatus(statusLength));
        bar.add(statusSep());
        bar.add(wrapStatus(statusLines));
        bar.add(statusSep());
        bar.add(wrapStatus(statusLn));
        bar.add(wrapStatus(statusCol));
        bar.add(statusSep());
        bar.add(wrapStatus(statusEOL));
        bar.add(statusSep());
        bar.add(wrapStatus(statusEncoding));
        bar.add(statusSep());
        JLabel ins = makeStatusLabel("INS");
        bar.add(wrapStatus(ins));

        return bar;
    }

    private JPanel wrapStatus(JLabel label) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        p.setOpaque(false);
        p.add(label);
        return p;
    }

    private JSeparator statusSep() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 16));
        sep.setForeground(new Color(180, 180, 180));
        return sep;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TAB MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Creates a new editor tab. If file is null, opens a blank "Untitled" tab.
     */
    private void createNewTab(String tabTitle, File file) {
        // ── Text Area setup ──
        JTextArea textArea = new JTextArea();
        textArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        textArea.setLineWrap(false);
        textArea.setMargin(new Insets(4, 4, 4, 4));
        textArea.setTabSize(4);

        // ── Undo/Redo manager per tab ──
        UndoManager undoManager = new UndoManager();
        textArea.getDocument().addUndoableEditListener(undoManager);

        // ── Line number gutter ──
        LineNumberGutter gutter = new LineNumberGutter(textArea);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setRowHeaderView(gutter);

        // ── Register DocumentListener to mark tab as modified and refresh status ──
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { onDocumentChange(); }
            @Override public void removeUpdate(DocumentEvent e)  { onDocumentChange(); }
            @Override public void changedUpdate(DocumentEvent e) { onDocumentChange(); }

            private void onDocumentChange() {
                int idx = tabbedPane.getSelectedIndex();
                if (idx >= 0 && idx < modifiedFlags.size() && !modifiedFlags.get(idx)) {
                    modifiedFlags.set(idx, true);
                    markTabModified(idx);
                }
                refreshStatusBar();
                gutter.repaint(); // Repaint gutter whenever lines change
            }
        });

        // ── CaretListener for live Ln/Col updates ──
        textArea.addCaretListener(e -> refreshStatusBar());

        // Track the file associated with this tab
        openFiles.add(file);
        undoManagers.add(undoManager);
        modifiedFlags.add(false);

        // Add tab to pane
        tabbedPane.addTab(tabTitle, scrollPane);
        int idx = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(idx, new TabHeader(tabTitle, idx));
        tabbedPane.setSelectedIndex(idx);
    }

    /** Marks a tab's title with a '*' prefix to indicate unsaved changes. */
    private void markTabModified(int idx) {
        Component tc = tabbedPane.getTabComponentAt(idx);
        if (tc instanceof TabHeader th) th.setModified(true);
    }

    /** Clears the '*' modified indicator from a tab's title. */
    private void clearTabModified(int idx) {
        modifiedFlags.set(idx, false);
        Component tc = tabbedPane.getTabComponentAt(idx);
        if (tc instanceof TabHeader th) th.setModified(false);
    }

    /** Returns the JTextArea of the currently active tab, or null if none. */
    private JTextArea activeArea() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx < 0) return new JTextArea(); // Safe no-op fallback
        JScrollPane sp = (JScrollPane) tabbedPane.getComponentAt(idx);
        return (JTextArea) sp.getViewport().getView();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FILE OPERATIONS
    // ══════════════════════════════════════════════════════════════════════

    /** File → New: open a blank tab. */
    private void fileNew() {
        int count = tabbedPane.getTabCount() + 1;
        createNewTab("new " + count, null);
    }

    /** File → Open: show chooser, load text into a new tab. */
    private void fileOpen() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Open File");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        try {
            String content = Files.readString(file.toPath());
            createNewTab(file.getName(), file);
            int idx = tabbedPane.getSelectedIndex();
            activeArea().setText(content);
            activeArea().setCaretPosition(0);
            clearTabModified(idx);           // Fresh load = not modified
            setFrameTitle(file.getName());
        } catch (IOException ex) {
            showError("Cannot open file:\n" + ex.getMessage());
        }
    }

    /** File → Save: save to current file path, or delegate to Save As. */
    private boolean fileSave() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx < 0) return false;
        File file = openFiles.get(idx);
        if (file == null) return fileSaveAs();
        return writeFile(file, idx);
    }

    /** File → Save As: prompt for path, then save. */
    private boolean fileSaveAs() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx < 0) return false;

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save As");
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;

        File file = fc.getSelectedFile();
        openFiles.set(idx, file);
        return writeFile(file, idx);
    }

    /** Writes the active text area content to disk. */
    private boolean writeFile(File file, int idx) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(activeArea().getText());
            clearTabModified(idx);
            Component tc = tabbedPane.getTabComponentAt(idx);
            if (tc instanceof TabHeader th) th.setTitle(file.getName());
            setFrameTitle(file.getName());
            return true;
        } catch (IOException ex) {
            showError("Cannot save file:\n" + ex.getMessage());
            return false;
        }
    }

    /** Closes the currently active tab, prompting to save if modified. */
    private void closeCurrentTab() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx < 0) return;
        if (!confirmClose(idx)) return;

        tabbedPane.removeTabAt(idx);
        openFiles.remove(idx);
        undoManagers.remove(idx);
        modifiedFlags.remove(idx);

        if (tabbedPane.getTabCount() == 0) fileNew(); // Always keep at least one tab
    }

    /** Prompts to save unsaved changes. Returns false if user cancels. */
    private boolean confirmClose(int idx) {
        if (!modifiedFlags.get(idx)) return true;
        Component tc = tabbedPane.getTabComponentAt(idx);
        String name = tc instanceof TabHeader th ? th.getTitle() : "this file";
        int res = JOptionPane.showConfirmDialog(this,
                "Save changes to \"" + name + "\"?", "Unsaved Changes",
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res == JOptionPane.CANCEL_OPTION || res == JOptionPane.CLOSED_OPTION) return false;
        if (res == JOptionPane.YES_OPTION) {
            tabbedPane.setSelectedIndex(idx);
            return fileSave();
        }
        return true;
    }

    /** Exits the application, checking all tabs for unsaved changes. */
    private void exitApplication() {
        for (int i = tabbedPane.getTabCount() - 1; i >= 0; i--) {
            if (!confirmClose(i)) return; // User cancelled
        }
        System.exit(0);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EDIT OPERATIONS
    // ══════════════════════════════════════════════════════════════════════

    private void editUndo() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx >= 0 && undoManagers.get(idx).canUndo())
            undoManagers.get(idx).undo();
    }

    private void editRedo() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx >= 0 && undoManagers.get(idx).canRedo())
            undoManagers.get(idx).redo();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FONT CHOOSER DIALOG
    // ══════════════════════════════════════════════════════════════════════

    /** Opens the custom Font Chooser dialog and applies the selected font. */
    private void showFontChooserDialog() {
        JTextArea ta = activeArea();
        if (ta == null) return;
        FontChooserDialog dialog = new FontChooserDialog(this, ta.getFont());
        dialog.setVisible(true);
        Font chosen = dialog.getSelectedFont();
        if (chosen != null) ta.setFont(chosen);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STATUS BAR UPDATE
    // ══════════════════════════════════════════════════════════════════════

    /** Refreshes all status bar labels from the currently active text area. */
    private void refreshStatusBar() {
        JTextArea ta = activeArea();
        if (ta == null) return;

        String text    = ta.getText();
        int    length  = text.length();
        int    lines   = ta.getLineCount();
        int    caretPos = ta.getCaretPosition();

        int ln  = 1, col = 1;
        try {
            ln  = ta.getLineOfOffset(caretPos) + 1;
            col = caretPos - ta.getLineStartOffset(ln - 1) + 1;
        } catch (BadLocationException ignored) {}

        statusLength.setText("Length: " + length);
        statusLines .setText("Lines: "  + lines);
        statusLn    .setText("Ln: "     + ln);
        statusCol   .setText("Col: "    + col);
    }

    private void setFrameTitle(String fileName) {
        setTitle(fileName + " — " + APP_TITLE + " — " + APP_VERSION);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS / FACTORIES
    // ══════════════════════════════════════════════════════════════════════

    /** Creates a menu item with Ctrl+key shortcut and an ActionListener. */
    private JMenuItem makeMenuItem(String label, int mnemonic, int keyCode,
                                   ActionListener al) {
        JMenuItem item = new JMenuItem(label, mnemonic);
        item.setAccelerator(KeyStroke.getKeyStroke(keyCode,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        item.addActionListener(al);
        return item;
    }

    /** Creates a menu item with Ctrl+Shift+key shortcut. */
    private JMenuItem makeMenuItemShift(String label, int keyCode, ActionListener al) {
        JMenuItem item = new JMenuItem(label);
        item.setAccelerator(KeyStroke.getKeyStroke(keyCode,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() |
                InputEvent.SHIFT_DOWN_MASK));
        item.addActionListener(al);
        return item;
    }

    /** Creates a toolbar button using a Unicode symbol as icon. */
    private JButton makeToolButton(String tooltip, String symbol, ActionListener al) {
        JButton btn = new JButton(symbol);
        btn.setToolTipText(tooltip);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        btn.setPreferredSize(new Dimension(36, 28));
        btn.setMargin(new Insets(2, 4, 2, 4));
        btn.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        btn.setBackground(new Color(240, 240, 240));
        btn.addActionListener(al);
        return btn;
    }

    private JLabel makeStatusLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbl.setForeground(new Color(60, 60, 60));
        return lbl;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /** Generates a simple programmatic icon for the app window. */
    private Image buildAppIcon() {
        int size = 16;
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(size, size,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0, 122, 204));
        g.fillRect(0, 0, size, size);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Consolas", Font.BOLD, 10));
        g.drawString("N+", 1, 12);
        g.dispose();
        return img;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INNER CLASS: TabHeader — custom tab label with close button
    // ══════════════════════════════════════════════════════════════════════
    private class TabHeader extends JPanel {
        private final JLabel titleLabel;
        private String baseTitle;

        TabHeader(String title, int tabIndex) {
            super(new FlowLayout(FlowLayout.LEFT, 0, 0));
            setOpaque(false);
            this.baseTitle = title;
            titleLabel = new JLabel(title);
            titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

            JButton closeBtn = new JButton("×");
            closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            closeBtn.setPreferredSize(new Dimension(18, 18));
            closeBtn.setMargin(new Insets(0, 0, 0, 0));
            closeBtn.setFocusPainted(false);
            closeBtn.setBorderPainted(false);
            closeBtn.setContentAreaFilled(false);
            closeBtn.setForeground(new Color(120, 120, 120));
            closeBtn.addActionListener(e -> {
                int idx = tabbedPane.indexOfTabComponent(TabHeader.this);
                if (idx >= 0) { tabbedPane.setSelectedIndex(idx); closeCurrentTab(); }
            });

            add(titleLabel);
            add(Box.createHorizontalStrut(4));
            add(closeBtn);
        }

        void setModified(boolean modified) {
            titleLabel.setText(modified ? "* " + baseTitle : baseTitle);
        }

        void setTitle(String newTitle) {
            this.baseTitle = newTitle;
            titleLabel.setText(newTitle);
        }

        String getTitle() { return baseTitle; }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INNER CLASS: LineNumberGutter — displays line numbers beside text area
    // ══════════════════════════════════════════════════════════════════════
    static class LineNumberGutter extends JComponent {
        private static final int PADDING = 8;
        private final JTextArea textArea;
        private final Color gutterBg  = new Color(228, 228, 228);
        private final Color lineColor = new Color(130, 130, 130);
        private final Color borderCol = new Color(200, 200, 200);

        LineNumberGutter(JTextArea ta) {
            this.textArea = ta;
            setFont(new Font("Consolas", Font.PLAIN, 13));

            // Repaint on scroll and resize
            ta.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e)  { repaint(); }
                @Override public void removeUpdate(DocumentEvent e)  { repaint(); }
                @Override public void changedUpdate(DocumentEvent e) { repaint(); }
            });
            ta.addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { repaint(); }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            int digits = String.valueOf(Math.max(textArea.getLineCount(), 1)).length();
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.charWidth('9') * (digits + 1) + PADDING * 2, 0);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Rectangle clip = g.getClipBounds();
            g2.setColor(gutterBg);
            g2.fillRect(0, 0, getWidth(), getHeight());

            // Draw right border
            g2.setColor(borderCol);
            g2.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());

            FontMetrics fm = g2.getFontMetrics(getFont());
            g2.setFont(getFont());
            g2.setColor(lineColor);

            int startLine, endLine;
            try {
                int startOffset = textArea.viewToModel2D(
                        new Point(0, clip.y));
                int endOffset   = textArea.viewToModel2D(
                        new Point(0, clip.y + clip.height));
                startLine = textArea.getLineOfOffset(startOffset) + 1;
                endLine   = textArea.getLineOfOffset(endOffset)   + 1;
            } catch (BadLocationException ex) { return; }

            int lineHeight = textArea.getFontMetrics(textArea.getFont()).getHeight();
            int textOffset = textArea.getInsets().top;

            for (int i = startLine; i <= endLine; i++) {
                String num = String.valueOf(i);
                int x = getWidth() - fm.stringWidth(num) - PADDING;
                int y = textOffset + (i - 1) * lineHeight +
                        ((lineHeight + fm.getAscent() - fm.getDescent()) / 2);
                g2.drawString(num, x, y);
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
//  TOP-LEVEL CLASS: FontChooserDialog
// ══════════════════════════════════════════════════════════════════════════

/**
 * FontChooserDialog
 * A modal dialog that lets the user pick a font family, style, and size,
 * with a live preview of the chosen settings.
 */
class FontChooserDialog extends JDialog {

    private Font selectedFont = null;

    private final JList<String>  familyList;
    private final JList<String>  styleList;
    private final JList<Integer> sizeList;
    private final JTextField     familyField;
    private final JTextField     styleField;
    private final JTextField     sizeField;
    private final JLabel         previewLabel;

    private static final String[] STYLES = {"Regular", "Bold", "Italic", "Bold Italic"};
    private static final Integer[] SIZES  = {
        8, 9, 10, 11, 12, 13, 14, 16, 18, 20, 22, 24, 28, 32, 36, 48, 72
    };

    FontChooserDialog(Frame parent, Font currentFont) {
        super(parent, "Font", true); // Modal dialog
        setSize(520, 380);
        setLocationRelativeTo(parent);
        setResizable(false);

        // ── Fetch system font families ──
        String[] families = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();

        // ── Build UI ──
        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── Three columns: Family | Style | Size ──
        JPanel selectPanel = new JPanel(new GridLayout(1, 3, 8, 0));

        familyList = new JList<>(families);
        styleList  = new JList<>(STYLES);
        sizeList   = new JList<>(SIZES);

        familyField = new JTextField(currentFont.getFamily());
        styleField  = new JTextField(styleNameOf(currentFont.getStyle()));
        sizeField   = new JTextField(String.valueOf(currentFont.getSize()));

        selectPanel.add(buildColumn("Font:",       familyField, familyList));
        selectPanel.add(buildColumn("Font Style:", styleField,  styleList));
        selectPanel.add(buildColumn("Size:",       sizeField,   sizeList));

        // ── Preview panel ──
        previewLabel = new JLabel("AaBbYyZz 1234567890", SwingConstants.CENTER);
        previewLabel.setBorder(BorderFactory.createTitledBorder("Sample"));
        previewLabel.setPreferredSize(new Dimension(0, 80));
        previewLabel.setOpaque(true);
        previewLabel.setBackground(Color.WHITE);

        // ── Buttons ──
        JButton okBtn     = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        okBtn.setPreferredSize(new Dimension(80, 28));
        cancelBtn.setPreferredSize(new Dimension(80, 28));

        okBtn.addActionListener(e -> {
            selectedFont = buildSelectedFont();
            dispose();
        });
        cancelBtn.addActionListener(e -> dispose());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);

        mainPanel.add(selectPanel, BorderLayout.CENTER);
        mainPanel.add(previewLabel, BorderLayout.SOUTH);
        add(mainPanel, BorderLayout.CENTER);
        add(btnPanel,  BorderLayout.SOUTH);

        // ── Sync list selections with current font ──
        syncSelection(familyList, currentFont.getFamily());
        syncSelection(styleList,  styleNameOf(currentFont.getStyle()));
        syncSelection(sizeList,   currentFont.getSize());

        // ── Add change listeners to update preview ──
        ListSelectionListener previewUpdater = e -> updatePreview();
        familyList.addListSelectionListener(previewUpdater);
        styleList .addListSelectionListener(previewUpdater);
        sizeList  .addListSelectionListener(previewUpdater);

        // Sync text fields when list selection changes
        familyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) familyField.setText(familyList.getSelectedValue());
        });
        styleList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) styleField.setText(styleList.getSelectedValue());
        });
        sizeList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting())
                sizeField.setText(String.valueOf(sizeList.getSelectedValue()));
        });

        updatePreview(); // Initial preview render
    }

    /** Builds a labelled column: text field on top, scrollable list below. */
    private JPanel buildColumn(String title, JTextField field, JList<?> list) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(field,             BorderLayout.CENTER);
        panel.add(new JScrollPane(list), BorderLayout.SOUTH);
        ((JScrollPane) panel.getComponent(2)).setPreferredSize(new Dimension(0, 180));
        return panel;
    }

    /** Selects the list item matching the target value. */
    private <T> void syncSelection(JList<T> list, T target) {
        DefaultListModel<T> model = new DefaultListModel<>();
        // JList with array model — find the index manually
        ListModel<T> lm = list.getModel();
        for (int i = 0; i < lm.getSize(); i++) {
            if (Objects.equals(lm.getElementAt(i), target)) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    /** Rebuilds and renders the preview label using current selections. */
    private void updatePreview() {
        Font f = buildSelectedFont();
        if (f != null) previewLabel.setFont(f.deriveFont((float) Math.min(f.getSize(), 32)));
    }

    /** Constructs a Font object from the current dialog selections. */
    private Font buildSelectedFont() {
        String family = familyList.getSelectedValue();
        String style  = styleList .getSelectedValue();
        int    size   = 14;
        try { size = Integer.parseInt(sizeField.getText().trim()); }
        catch (NumberFormatException ignored) {
            if (sizeList.getSelectedValue() != null) size = sizeList.getSelectedValue();
        }
        if (family == null) family = familyField.getText().trim();
        if (style  == null) style  = "Regular";

        int styleConst = switch (style) {
            case "Bold"        -> Font.BOLD;
            case "Italic"      -> Font.ITALIC;
            case "Bold Italic" -> Font.BOLD | Font.ITALIC;
            default            -> Font.PLAIN;
        };
        return new Font(family, styleConst, Math.max(6, size));
    }

    /** Converts an AWT font style integer to its string name. */
    private String styleNameOf(int style) {
        return switch (style) {
            case Font.BOLD            -> "Bold";
            case Font.ITALIC          -> "Italic";
            case Font.BOLD | Font.ITALIC -> "Bold Italic";
            default                   -> "Regular";
        };
    }

    /** Returns the font the user chose, or null if they cancelled. */
    public Font getSelectedFont() { return selectedFont; }
}