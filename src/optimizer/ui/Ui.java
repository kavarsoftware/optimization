package optimizer.ui;

import optimizer.brain.*;
import optimizer.experiments.*;

import static optimizer.ui.Visuals.*;
import java.awt.*;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.*;
import javax.swing.table.DefaultTableModel;

/** A small shared theme using standard Swing; no external UI dependencies. */
final class Ui {
    static final Color NAV = new Color(21, 27, 41);
    static Font font(int weight, int size) { return new Font("SansSerif", weight, size); }
    static void install() {
        for (String key : new String[] {"Label.font", "Button.font", "ComboBox.font", "Spinner.font", "TabbedPane.font", "Table.font", "TextField.font"})
            UIManager.put(key, font(Font.PLAIN, 13));
        UIManager.put("Panel.background", BACK);
        UIManager.put("Label.foreground", INK);
        UIManager.put("SplitPane.background", BACK);
        UIManager.put("SplitPane.border", new EmptyBorder(0,0,0,0));
        UIManager.put("ScrollPane.border", new EmptyBorder(0,0,0,0));
        UIManager.put("TextField.selectionBackground", new Color(226, 225, 255));
    }
    static String scoreLabel(Evaluator e) { return "Objective J (dimensionless)"; }
    static double value(DefaultTableModel model, int r, int c) { return Double.parseDouble(String.valueOf(model.getValueAt(r,c)).strip()); }
    static String format(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT,"%.3f",value) : "—"; }
    static JPanel panel(LayoutManager layout) { JPanel p = new JPanel(layout); p.setOpaque(false); return p; }
    static JPanel tabPanel() { return panel(new BorderLayout(12,12)); }
    static JPanel card(LayoutManager layout) {
        JPanel p = new JPanel(layout) {
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create(); Visuals.quality(g);
                g.setColor(Color.WHITE); g.fillRoundRect(0,0,getWidth(),getHeight(),16,16);
                g.setColor(LINE); g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,16,16); g.dispose();
            }
        };
        p.setOpaque(false); return p;
    }
    static JLabel caption(String text) { JLabel label = new JLabel(text); label.setFont(font(Font.BOLD,10)); label.setForeground(MUTED); return label; }
    static JComponent badge(String text, Color color) {
        JPanel p = panel(new GridBagLayout());
        JLabel label = new JLabel(text); label.setFont(font(Font.BOLD,10)); label.setForeground(color);
        label.setBorder(new EmptyBorder(9,12,9,12)); p.add(label); return p;
    }
    private static JButton arrow(int direction) {
        JButton b = new JButton() {
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create(); Visuals.quality(g);
                g.setColor(isEnabled() ? MUTED : LINE);
                int x = getWidth()/2, y = getHeight()/2;
                int sign = direction == SwingConstants.NORTH ? -1 : 1;
                g.fillPolygon(new int[] {x-3,x+3,x}, new int[] {y-sign*2,y-sign*2,y+sign*2}, 3);
                g.dispose();
            }
        };
        b.setBorder(new EmptyBorder(0,0,0,0)); b.setPreferredSize(new Dimension(18,10));
        b.setContentAreaFilled(false); b.setFocusable(false); return b;
    }
    static JSpinner spinner(Number value, Comparable<?> min, Comparable<?> max, Number step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(value,min,max,step));
        s.setUI(new BasicSpinnerUI() {
            protected Component createNextButton() { return arrow(SwingConstants.NORTH); }
            protected Component createPreviousButton() { return arrow(SwingConstants.SOUTH); }
            private JButton arrow(int direction) {
                JButton b = Ui.arrow(direction);
                b.setBorder(new EmptyBorder(0,2,0,3));
                if (direction == SwingConstants.NORTH) installNextButtonListeners(b); else installPreviousButtonListeners(b);
                return b;
            }
        });
        s.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),new EmptyBorder(3,5,3,2)));
        s.setPreferredSize(new Dimension(value instanceof Long ? 100 : 80,32));
        var field = ((JSpinner.DefaultEditor)s.getEditor()).getTextField();
        s.getEditor().setBorder(null); s.setBackground(Color.WHITE);
        field.setFont(font(Font.PLAIN,12)); field.setForeground(INK); field.setBackground(Color.WHITE); field.setBorder(null);
        return s;
    }
    static void styleCombo(JComboBox<?> combo) {
        combo.setUI(new BasicComboBoxUI() {
            protected JButton createArrowButton() {
                JButton button = arrow(SwingConstants.SOUTH);
                button.setBorder(new EmptyBorder(0,5,0,5)); return button;
            }
        });
        combo.setBackground(Color.WHITE); combo.setForeground(INK); combo.setFont(font(Font.BOLD,12));
        combo.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),new EmptyBorder(3,8,3,3)));
        combo.setPreferredSize(new Dimension(178,34));
    }
    static JButton button(String label, Color color) {
        boolean primary = color.equals(BLUE);
        boolean accent = color.equals(TEAL);
        JButton b = new JButton(label) {
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D)graphics.create(); Visuals.quality(g);
                Color fill = primary ? BLUE : Color.WHITE;
                if (!isEnabled()) fill = primary ? new Color(224,225,240) : new Color(247,248,250);
                else if (getModel().isRollover()) fill = primary ? BLUE.darker() : new Color(243,244,249);
                g.setColor(fill); g.fillRoundRect(0,0,getWidth(),getHeight(),9,9);
                if (!primary) { g.setColor(LINE); g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,9,9); }
                g.dispose(); super.paintComponent(graphics);
            }
        };
        b.setUI(new BasicButtonUI()); b.setContentAreaFilled(false); b.setOpaque(false);
        b.setForeground(primary ? Color.WHITE : accent ? TEAL : INK);
        b.setFocusPainted(false); b.setRolloverEnabled(true);
        b.setFont(font(Font.BOLD,12)); b.setBorder(new EmptyBorder(10,14,10,14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b;
    }
    static JPanel flow() { return panel(new FlowLayout(FlowLayout.LEFT,8,0)); }
    static void addField(JPanel p,String label,JComponent component) {
        JPanel group = panel(new BorderLayout(6,0));
        JLabel text = new JLabel(label); text.setFont(font(Font.PLAIN,11)); text.setForeground(MUTED); text.setLabelFor(component);
        group.add(text,BorderLayout.WEST); group.add(component,BorderLayout.CENTER); p.add(group);
    }
    static JPanel metric(String title,JLabel value) {
        JPanel p = card(new BorderLayout(0,9)); p.setBorder(new EmptyBorder(15,16,15,12));
        value.setForeground(INK); value.setFont(font(Font.BOLD,24));
        p.add(caption(title),BorderLayout.NORTH); p.add(value,BorderLayout.CENTER); return p;
    }
    static JComponent section(String name,JComponent content) {
        JPanel p = card(new BorderLayout(0,12)); p.setBorder(new EmptyBorder(16,16,16,16));
        JLabel heading = new JLabel(name); heading.setFont(font(Font.BOLD,13)); heading.setForeground(INK);
        p.add(heading,BorderLayout.NORTH); p.add(content,BorderLayout.CENTER); return p;
    }
    static DefaultTableModel model(boolean editable,String... columns) {
        return new DefaultTableModel(columns,0) { public boolean isCellEditable(int r,int c) { return editable; } };
    }
    static JTable table(DefaultTableModel model) {
        JTable t = new JTable(model); t.setRowHeight(38); t.setFont(font(Font.PLAIN,12));
        t.setForeground(INK); t.setBackground(Color.WHITE); t.setShowVerticalLines(false);
        t.setGridColor(LINE); t.setIntercellSpacing(new Dimension(12,1));
        t.setSelectionBackground(new Color(238,237,255)); t.setSelectionForeground(INK);
        t.setFillsViewportHeight(true); t.setAutoCreateRowSorter(true);
        t.getTableHeader().setDefaultRenderer((table,value,selected,focus,row,column) -> {
            JLabel label = new JLabel(String.valueOf(value));
            label.setOpaque(true); label.setBackground(new Color(247,248,251)); label.setForeground(MUTED);
            label.setFont(font(Font.BOLD,11)); label.setBorder(new EmptyBorder(12,10,12,10)); return label;
        });
        t.getTableHeader().setPreferredSize(new Dimension(100,38));
        return t;
    }
    static JTable wideTable(DefaultTableModel model) {
        JTable t = table(model); t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for(int i=0;i<t.getColumnCount();i++) t.getColumnModel().getColumn(i).setPreferredWidth(i==0?105:120);
        return t;
    }
    static JScrollPane scrollTable(JTable table) {
        JScrollPane scroll = new JScrollPane(table); scroll.setBorder(new EmptyBorder(0,0,0,0));
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.getViewport().setBackground(Color.WHITE); return scroll;
    }
    static JScrollPane scrollCanvas(AssignmentCanvas canvas) {
        JScrollPane scroll = new JScrollPane(canvas, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null); scroll.getViewport().setBackground(BACK);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        return scroll;
    }
    static void polishScrollbars(Container container) {
        if (container instanceof JScrollPane scroll) {
            for (JScrollBar bar : new JScrollBar[] {scroll.getVerticalScrollBar(),scroll.getHorizontalScrollBar()}) {
                bar.setPreferredSize(new Dimension(9,9));
                bar.setUI(new BasicScrollBarUI() {
                    protected JButton createDecreaseButton(int orientation) { return empty(); }
                    protected JButton createIncreaseButton(int orientation) { return empty(); }
                    private JButton empty() { JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));return b; }
                    protected void paintTrack(Graphics g,JComponent c,Rectangle r) { g.setColor(BACK);g.fillRect(r.x,r.y,r.width,r.height); }
                    protected void paintThumb(Graphics graphics,JComponent c,Rectangle r) {
                        Graphics2D g=(Graphics2D)graphics.create();Visuals.quality(g);
                        g.setColor(new Color(197,203,216));g.fillRoundRect(r.x+1,r.y+1,Math.max(3,r.width-2),Math.max(3,r.height-2),7,7);g.dispose();
                    }
                });
            }
        }
        for (Component child : container.getComponents()) if(child instanceof Container nested) polishScrollbars(nested);
    }
    static void hideTabs(JTabbedPane tabs) {
        tabs.setUI(new BasicTabbedPaneUI() {
            protected int calculateTabAreaHeight(int placement,int runs,int max) { return 0; }
            protected void paintTabArea(Graphics g,int placement,int selected) { }
            protected void paintContentBorder(Graphics g,int placement,int selected) { }
            protected void installDefaults() { super.installDefaults(); contentBorderInsets = new Insets(0,0,0,0); tabAreaInsets = new Insets(0,0,0,0); }
        });
        tabs.setBorder(null); tabs.setOpaque(false);
    }
    static void styleTabs(JTabbedPane tabs) {
        tabs.setFont(font(Font.BOLD,12)); tabs.setForeground(MUTED); tabs.setBackground(BACK);
        tabs.setUI(new BasicTabbedPaneUI() {
            protected void installDefaults() {
                super.installDefaults(); tabInsets = new Insets(12,18,12,18); contentBorderInsets = new Insets(10,0,0,0); tabAreaInsets = new Insets(0,0,0,0);
            }
            protected void paintTabBackground(Graphics g,int placement,int index,int x,int y,int w,int h,boolean selected) { }
            protected void paintTabBorder(Graphics g,int placement,int index,int x,int y,int w,int h,boolean selected) {
                if(selected) {g.setColor(BLUE);g.fillRect(x+14,y+h-3,w-28,3);}
            }
            protected void paintContentBorder(Graphics g,int placement,int selected) { }
            protected void paintFocusIndicator(Graphics g,int placement,Rectangle[] rects,int index,Rectangle icon,Rectangle text,boolean selected) { }
        });
        tabs.setBorder(null);
    }
    static JPanel navigation(JTabbedPane tabs) {
        JPanel sidebar = new JPanel(new BorderLayout()); sidebar.setBackground(NAV);
        sidebar.setPreferredSize(new Dimension(190,600)); sidebar.setBorder(new EmptyBorder(28,16,22,16));
        JPanel top = panel(new BorderLayout(0,35));
        JPanel brand = panel(new GridLayout(2,1,0,8));
        JLabel name = new JLabel("PLACEMENT"); name.setFont(font(Font.BOLD,17)); name.setForeground(Color.WHITE);
        JLabel note = new JLabel("OPTIMIZATION LAB"); note.setFont(font(Font.PLAIN,10)); note.setForeground(new Color(139,149,172));
        brand.add(name); brand.add(note); top.add(brand,BorderLayout.NORTH);
        JPanel items = panel(new GridLayout(tabs.getTabCount()+1,1,0,6));
        JLabel section = new JLabel("WORKSPACE"); section.setFont(font(Font.BOLD,10)); section.setForeground(new Color(112,125,150)); items.add(section);
        for(int i=0;i<tabs.getTabCount();i++) {
            final int index=i;
            JButton button = new JButton(String.format("%02d   %s",i+1,tabs.getTitleAt(i))) {
                protected void paintComponent(Graphics graphics) {
                    if(tabs.getSelectedIndex()==index || getModel().isRollover()) {
                        Graphics2D g=(Graphics2D)graphics.create();Visuals.quality(g);
                        g.setColor(tabs.getSelectedIndex()==index?new Color(49,55,79):new Color(31,39,56));
                        g.fillRoundRect(0,0,getWidth(),getHeight(),9,9);g.dispose();
                    }
                    setForeground(tabs.getSelectedIndex()==index?Color.WHITE:new Color(161,171,191));
                    super.paintComponent(graphics);
                }
            };
            button.setUI(new BasicButtonUI()); button.setContentAreaFilled(false); button.setFocusPainted(false);
            button.setFont(font(Font.PLAIN,12)); button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setBorder(new EmptyBorder(12,11,12,5)); button.setRolloverEnabled(true);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addActionListener(e -> tabs.setSelectedIndex(index)); items.add(button);
        }
        tabs.addChangeListener(e -> sidebar.repaint()); top.add(items,BorderLayout.CENTER); sidebar.add(top,BorderLayout.NORTH);
        JLabel footer = new JLabel("<html>JAVA 17<br><br><font color='#77839c'>12 services. Four servers.<br>One better placement.</font></html>");
        footer.setFont(font(Font.PLAIN,11)); footer.setForeground(new Color(177,186,204)); sidebar.add(footer,BorderLayout.SOUTH);
        return sidebar;
    }
}
