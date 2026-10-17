package optimizer;
import static optimizer.Visuals.*;
import java.awt.*;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/** Shared Swing widgets; the application itself is AssessmentDashboard. */
final class Ui {
    static String scoreLabel(Evaluator e) {
        return "Objective J (dimensionless)";
    }

    static double value(DefaultTableModel model, int r, int c) {
        return Double.parseDouble(String.valueOf(model.getValueAt(r, c)).strip());
    }

    static String format(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "N/A";
    }

    static JSpinner spinner(Number value, Comparable<?> min, Comparable<?> max, Number step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        s.setPreferredSize(new Dimension(value instanceof Long ? 100 : 85, 29));
        return s;
    }

    static JPanel panel(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(8, 0, 0, 0));
        return p;
    }

    static JPanel tabPanel() {
        JPanel p = panel(new BorderLayout(10, 12));
        p.setOpaque(true);
        p.setBackground(BACK);
        return p;
    }

    static JScrollPane scrollTable(JTable t) {
        JScrollPane scroll = new JScrollPane(t);
        scroll.setColumnHeaderView(t.getTableHeader());
        return scroll;
    }

    static JPanel flow() {
        JPanel p = panel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        p.setBorder(null);
        return p;
    }

    static void addField(JPanel p, String label, JComponent component) {
        JLabel text = new JLabel(label);
        text.setLabelFor(component);
        p.add(text);
        p.add(component);
    }

    static JButton button(String label, Color color) {
        JButton b = new JButton(label);
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setBorder(new EmptyBorder(9, 13, 9, 13));
        return b;
    }

    static JPanel metric(String title, JLabel value) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Color.WHITE);
        p.setBorder(new EmptyBorder(10, 17, 10, 17));
        JLabel label = new JLabel(title);
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        label.setForeground(MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        value.setForeground(INK);
        value.setFont(new Font("SansSerif", Font.BOLD, 23));
        p.add(label);
        p.add(Box.createVerticalStrut(5));
        p.add(value);
        return p;
    }

    static JComponent section(String name, JComponent content) {
        JPanel p = panel(new BorderLayout(8, 8));
        p.add(new JLabel(name), BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    static DefaultTableModel model(boolean editable, String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return editable;
            }
        };
    }

    static JTable wideTable(DefaultTableModel model) {
        JTable t = table(model);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int i = 0; i < t.getColumnCount(); i++)
            t.getColumnModel().getColumn(i).setPreferredWidth(i == 0 ? 175 : 145);
        return t;
    }

    static JTable table(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setRowHeight(28);
        t.setFont(new Font("SansSerif", Font.PLAIN, 12));
        t.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        t.setGridColor(LINE);
        t.setSelectionBackground(new Color(218, 232, 255));
        t.setFillsViewportHeight(true);
        t.setAutoCreateRowSorter(true);
        return t;
    }
}
