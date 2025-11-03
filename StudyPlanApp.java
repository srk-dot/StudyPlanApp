import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.sql.*;

// =====================================================
// =============== MAIN APPLICATION =====================
// =====================================================

public class StudyPlanApp {
    private static final Scanner sc = new Scanner(System.in);
    private static Map<String, User> users = new HashMap<>();
    private static User currentUser = null;

    public static void main(String[] args) {

        // Test DB Connection
        try (Connection conn = DBUtil.getConnection()) {
            if (conn != null)
                System.out.println("\u001B[32mDatabase connected successfully!\u001B[0m");
            else
                System.out.println("\u001B[31mDatabase connection failed.\u001B[0m");
        } catch (Exception e) {
            System.out.println("DB Error: " + e.getMessage());
        }

        users = User.load();

        while (true) {
            try {
                System.out.println("\n=== Study Plan Generator ===");
                System.out.println("1) Signup");
                System.out.println("2) Login");
                System.out.println("3) Exit");
                System.out.print("Choose: ");

                String c = sc.nextLine().trim();
                switch (c) {
                    case "1" -> User.signup(users, sc);
                    case "2" -> {
                        currentUser = User.login(users, sc);
                        if (currentUser != null) menu();
                    }
                    case "3" -> {
                        System.out.println("Goodbye!");
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            } catch (NoSuchElementException ex) {
                // stdin closed or no more input; exit gracefully
                System.out.println("\nNo input available, exiting.");
                return;
            }
        }
    }

    private static void menu() {
        while (currentUser != null) {
            System.out.println("\n--- " + currentUser.username + " ---");
            System.out.println("1) Enter/Upload Syllabus");
            System.out.println("2) Open Dashboard (GUI)");
            System.out.println("3) Logout");
            System.out.println("4) Save Plan to File");
            System.out.print("Choose: ");

            String ch = sc.nextLine().trim();
            switch (ch) {
                case "1" -> SyllabusManager.enter(sc, currentUser);
                case "2" -> DashboardGUI.show(currentUser);
                case "3" -> currentUser = null;
                case "4" -> saveFile();
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    // ✅ saves to file + MySQL for study plan only
    private static void saveFile() {
        if (currentUser.lastPlan == null) {
            System.out.println("No plan.");
            return;
        }
        try (PrintWriter w = new PrintWriter(currentUser.username + "_plan.txt")) {
            String planText = ViewGUI.render(currentUser.lastPlan);
            w.print(planText);
            System.out.println("Saved to " + currentUser.username + "_plan.txt");

            currentUser.savePlanToDatabase();  // save plan to DB

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // =====================================================
    // =============== DATA CLASSES =========================
    // =====================================================

    static class Subtopic implements Serializable {
        String topic, priority;
        boolean completed = false; // in-memory only

        Subtopic(String t, String p) { topic = t; priority = p; }

        @Override
        public String toString() {
            return topic + " [" + (priority == null ? "Medium" : priority) + "]"
                    + (completed ? " ✅" : "");
        }
    }

    static class Entry implements Serializable {
        String topic, priority;
        double hours;

        Entry(String t, String p, double h) {
            topic = t; priority = p; hours = h;
        }
    }

    static class Week implements Serializable {
        java.util.List<Entry> entries = new ArrayList<>();
        double totalHours;
        int weekNo;
    }

    static class StudyPlan implements Serializable {
        LocalDate generatedDate;
        java.util.List<Week> weeks = new ArrayList<>();
    }

    // =====================================================
    // ================== USER + DATABASE ===================
    // =====================================================

    static class User implements Serializable {
        String username, password;
        java.util.List<Subtopic> syllabus = new ArrayList<>(); // in-memory only
        StudyPlan lastPlan;

        User(String u, String p) {
            username = u;
            password = p;
        }

        // ---------------- SIGNUP ----------------
        static boolean signup(Map<String, User> users, Scanner sc) {
            System.out.print("Username: ");
            String u = sc.nextLine().trim();
            System.out.print("Password: ");
            String p = sc.nextLine().trim();

            try (Connection conn = DBUtil.getConnection()) {

                String check = "SELECT username FROM users WHERE username=?";
                PreparedStatement ps = conn.prepareStatement(check);
                ps.setString(1, u);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    System.out.println("Username already exists.");
                    return false;
                }

                String insert = "INSERT INTO users (username, password) VALUES (?,?)";
                ps = conn.prepareStatement(insert);
                ps.setString(1, u);
                ps.setString(2, p);
                ps.executeUpdate();

                users.put(u, new User(u, p));
                System.out.println("Signup successful!");
                return true;

            } catch (Exception ex) {
                System.out.println("Signup DB error: " + ex.getMessage());
                return false;
            }
        }

        // ---------------- LOGIN ----------------
        static User login(Map<String, User> users, Scanner sc) {
            System.out.print("Username: ");
            String u = sc.nextLine().trim();
            System.out.print("Password: ");
            String p = sc.nextLine().trim();

            try (Connection conn = DBUtil.getConnection()) {

                String sql = "SELECT password FROM users WHERE username=?";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setString(1, u);
                ResultSet rs = ps.executeQuery();

                if (rs.next() && p.equals(rs.getString("password"))) {
                    System.out.println("Login successful!");
                    User user = new User(u, p);
                    users.put(u, user);
                    return user;
                }

                System.out.println("Invalid username or password.");
                return null;

            } catch (Exception ex) {
                System.out.println("Login DB error: " + ex.getMessage());
                return null;
            }
        }

        // Save generated study plan to DB (unchanged)
        public void savePlanToDatabase() {
            if (this.lastPlan == null) {
                System.out.println("No plan to save.");
                return;
            }

            String planText = ViewGUI.render(this.lastPlan);

            String sql = "INSERT INTO study_plans (username, plan_text, generated_date) VALUES (?, ?, ?)";
            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, this.username);
                ps.setString(2, planText);
                ps.setDate(3, java.sql.Date.valueOf(this.lastPlan.generatedDate));
                ps.executeUpdate();

                System.out.println("Study plan saved to database!");

            } catch (SQLException e) {
                System.out.println("Error saving plan to DB: " + e.getMessage());
            }
        }

        static void save(Map<String, User> users) {}
        static Map<String, User> load() { return new HashMap<>(); }
    }

    // =====================================================
    // ================= SYLLABUS MANAGER ===================
    // =====================================================

    static class SyllabusManager {
        static void enter(Scanner sc, User u) {
            java.util.List<Subtopic> list = new ArrayList<>();
            System.out.println("1) Manual\n2) Upload File\nChoose: ");
            String ch = sc.nextLine().trim();
            if (ch.equals("2")) {
                System.out.print("File path: ");
                File f = new File(sc.nextLine().trim());
                if (!f.exists()) {
                    System.out.println("File not found.");
                    return;
                }
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String l;
                    while ((l = br.readLine()) != null) {
                        String[] a = l.split(",", 2);
                        String t = a[0].trim();
                        String p = a.length > 1 ? a[1].trim() : "Medium";
                        list.add(new Subtopic(t, p));
                    }
                } catch (Exception e) {
                    System.out.println("Read error: " + e.getMessage());
                }
            } else {
                while (true) {
                    System.out.print("Topic (blank to stop): ");
                    String t = sc.nextLine().trim();
                    if (t.isEmpty()) break;
                    System.out.print("Priority (Hard/Medium/Easy): ");
                    String p = sc.nextLine().trim();
                    if (p.isEmpty()) p = "Medium";
                    list.add(new Subtopic(t, p));
                }
            }
            u.syllabus = list;
            System.out.println("Syllabus saved successfully. (" + (list.size()) + " topics)");
        }
    }

    // =====================================================
    // ================= STUDY PLAN GENERATOR ==============
    // =====================================================

    static class StudyPlanGenerator {
        static StudyPlan gen(java.util.List<Subtopic> t, int wks, String capacity) {
            StudyPlan p = new StudyPlan();
            int n = t.size();
            if (n == 0) return p;
            int base = n / wks;
            int r = n % wks;
            int idx = 0;

            double mult = switch (capacity) {
                case "Slow Learner" -> 1.5;
                case "Fast Learner" -> 0.75;
                default -> 1.0;
            };

            for (int w = 0; w < wks; w++) {
                Week wk = new Week();
                int take = base + (w < r ? 1 : 0);
                for (int j = 0; j < take && idx < n; j++) {
                    Subtopic s = t.get(idx++);
                    String pr = s.priority == null ? "medium" : s.priority.toLowerCase();
                    double h = switch (pr) {
                        case "hard" -> 2.0;
                        case "medium" -> 1.25;
                        case "easy" -> 0.75;
                        default -> 1.0;
                    };
                    h *= mult;
                    wk.entries.add(new Entry(s.topic, s.priority, h));
                    wk.totalHours += h;
                }
                p.weeks.add(wk);
            }
            p.generatedDate = LocalDate.now();
            return p;
        }

        // =====================================================
// ============ SMART REBALANCING FEATURE (UPDATED) =====
// =====================================================
static void rebalancePlan(User user, String capacity) {
    if (user.lastPlan == null) return;

    List<Week> weeks = user.lastPlan.weeks;
    int totalWeeks = weeks.size();

    // Step 1: collect incomplete topics from earlier weeks
    List<Entry> carryOver = new ArrayList<>();

    for (int i = 0; i < totalWeeks; i++) {
        Week wk = weeks.get(i);
        List<Entry> remaining = new ArrayList<>();
        for (Entry e : wk.entries) {
            // inline findSubtopicByTopic to avoid cross-class resolution issues
            Subtopic s = null;
            for (Subtopic ss : user.syllabus) {
                if (ss.topic.equalsIgnoreCase(e.topic)) { s = ss; break; }
            }
            if (s == null || !s.completed) {
                remaining.add(e);
            }
        }

        // remove all items from this week; we'll reassign completed ones back
        wk.entries.clear();

        // keep only completed topics in current week, carry others
        for (Entry e : remaining) {
            Subtopic s = null;
            for (Subtopic ss : user.syllabus) {
                if (ss.topic.equalsIgnoreCase(e.topic)) { s = ss; break; }
            }
            if (s != null && s.completed) {
                wk.entries.add(e);
            } else {
                carryOver.add(e);
            }
        }
    }

    // Step 2: redistribute remaining topics into upcoming weeks
    int weekIndex = 0;
    for (Entry e : carryOver) {
        while (weekIndex < totalWeeks && weeks.get(weekIndex).entries.size() >= 6) {
            weekIndex++; // skip full weeks
        }
        if (weekIndex >= totalWeeks) weekIndex = totalWeeks - 1; // stay within total
        weeks.get(weekIndex).entries.add(e);
    }
    // UI rebuild is handled by caller (DashboardGUI)
}
}

  // =====================================================
// ================= DASHBOARD GUI =====================
// =====================================================

static class DashboardGUI {
    static void show(User u) {
        JFrame f = new JFrame("Dashboard - " + u.username);
        f.setSize(920, 720);
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        f.setLocationRelativeTo(null);

        Color bg = new Color(230, 240, 250);
        Color panelBg = new Color(210, 225, 245);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(bg);

        JLabel title = new JLabel("Study Plan Dashboard", JLabel.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setBorder(new EmptyBorder(10, 0, 10, 0));
        content.add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tabs.setBackground(panelBg);

        // ---- Generate Tab ----
        JPanel genPanel = styledPanel(panelBg);
        JTextArea genOut = styledArea();
        SpinnerNumberModel weekModel = new SpinnerNumberModel(4, 1, 52, 1);
        JSpinner weekSpinner = new JSpinner(weekModel);
        String[] caps = {"Slow Learner", "Average Learner", "Fast Learner"};
        JComboBox<String> capBox = new JComboBox<>(caps);
        JButton genBtn = new JButton("Generate Plan");
        genBtn.setBackground(new Color(80, 130, 190));
        genBtn.setForeground(Color.white);
        JButton saveBtn = new JButton("Save Plan");
        saveBtn.setBackground(new Color(60, 180, 80));
        saveBtn.setForeground(Color.white);

        JPanel genTop = new JPanel();
        genTop.setBackground(panelBg);
        genTop.add(new JLabel("Weeks:"));
        genTop.add(weekSpinner);
        genTop.add(new JLabel("Learning Capacity:"));
        genTop.add(capBox);
        genTop.add(genBtn);
        genTop.add(saveBtn);
        genPanel.add(genTop, BorderLayout.NORTH);
        genPanel.add(new JScrollPane(genOut), BorderLayout.CENTER);

        // ---- View Tab ----
        JTextArea viewArea = styledArea();
        JPanel viewPanel = styledPanel(panelBg);
        viewPanel.add(new JScrollPane(viewArea), BorderLayout.CENTER);

        // ---- Edit Syllabus Tab ----
        JPanel editPanel = styledPanel(panelBg);
        editPanel.setLayout(new BorderLayout());

        // List of topics
        DefaultListModel<Subtopic> listModel = new DefaultListModel<>();
        for (Subtopic s : u.syllabus) listModel.addElement(s);

        JList<Subtopic> syllabusList = new JList<>(listModel);
        syllabusList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        syllabusList.setFixedCellHeight(24);
        syllabusList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JScrollPane listScroll = new JScrollPane(syllabusList);
        listScroll.setPreferredSize(new Dimension(320, 520));
        listScroll.setBorder(new CompoundBorder(new LineBorder(Color.GRAY), new EmptyBorder(5, 5, 5, 5)));

        // Right panel (form + buttons)
        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JLabel topicLbl = new JLabel("Topic:");
        JTextField topicField = new JTextField();
        topicField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        topicField.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        JLabel prLbl = new JLabel("Priority:");
        String[] priorities = {"Hard", "Medium", "Easy"};
        JComboBox<String> prBox = new JComboBox<>(priorities);
        prBox.setMaximumSize(new Dimension(200, 28));

        form.add(topicLbl);
        form.add(Box.createRigidArea(new Dimension(0, 4)));
        form.add(topicField);
        form.add(Box.createRigidArea(new Dimension(0, 10)));
        form.add(prLbl);
        form.add(Box.createRigidArea(new Dimension(0, 4)));
        form.add(prBox);
        right.add(form, BorderLayout.NORTH);

        // ---- Buttons ----
        JPanel btnPanel = new JPanel();
        JButton selectBtn = new JButton("Select");
        JButton addBtn = new JButton("Add");
        JButton delBtn = new JButton("Delete");
        JButton updateBtn = new JButton("Update");

        selectBtn.setBackground(new Color(70, 130, 180));
        addBtn.setBackground(new Color(100, 170, 100));
        delBtn.setBackground(new Color(190, 80, 80));
        updateBtn.setBackground(new Color(80, 130, 190));

        selectBtn.setForeground(Color.white);
        addBtn.setForeground(Color.white);
        delBtn.setForeground(Color.white);
        updateBtn.setForeground(Color.white);

        btnPanel.add(selectBtn);
        btnPanel.add(addBtn);
        btnPanel.add(delBtn);
        btnPanel.add(updateBtn);
        right.add(btnPanel, BorderLayout.SOUTH);

        editPanel.add(listScroll, BorderLayout.WEST);
        editPanel.add(right, BorderLayout.CENTER);

        // ---- Button Logic ----
        selectBtn.addActionListener(e -> {
            int idx = syllabusList.getSelectedIndex();
            if (idx >= 0) {
                Subtopic s = u.syllabus.get(idx);
                topicField.setText(s.topic);
                prBox.setSelectedItem(s.priority);
            }
        });

        addBtn.addActionListener(e -> {
            String topic = topicField.getText().trim();
            if (topic.isEmpty()) {
                msg(f, "Please enter a topic name!");
                return;
            }
            String priority = (String) prBox.getSelectedItem();
            Subtopic s = new Subtopic(topic, priority);
            u.syllabus.add(s);
            listModel.addElement(s);
            topicField.setText("");
            msg(f, "Topic added successfully!");
        });

        delBtn.addActionListener(e -> {
            int idx = syllabusList.getSelectedIndex();
            if (idx >= 0) {
                int confirm = JOptionPane.showConfirmDialog(f, "Delete this topic?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    u.syllabus.remove(idx);
                    listModel.remove(idx);
                    topicField.setText("");
                    msg(f, "Topic deleted.");
                }
            }
        });

        updateBtn.addActionListener(e -> {
            int idx = syllabusList.getSelectedIndex();
            if (idx >= 0) {
                Subtopic s = u.syllabus.get(idx);
                s.topic = topicField.getText().trim();
                s.priority = (String) prBox.getSelectedItem();
                listModel.set(idx, s);
                msg(f, "Topic updated successfully!");
            }
        });

        // ---- Progress Tracker Tab ----
        JPanel progressPanel = styledPanel(panelBg);
        progressPanel.setLayout(new BorderLayout());

        JPanel weeksContainer = new JPanel();
        weeksContainer.setLayout(new BoxLayout(weeksContainer, BoxLayout.Y_AXIS));
        JScrollPane progressScroll = new JScrollPane(weeksContainer);
        progressScroll.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel bottomProgress = new JPanel(new FlowLayout(FlowLayout.RIGHT));
bottomProgress.setBackground(panelBg);

JButton rebalanceBtn = new JButton("Rebalance Plan");
rebalanceBtn.setBackground(new Color(100, 150, 200));
rebalanceBtn.setForeground(Color.white);

JProgressBar totalProgressBar = new JProgressBar();
totalProgressBar.setStringPainted(true);
totalProgressBar.setPreferredSize(new Dimension(300, 25));

bottomProgress.add(new JLabel("Overall Progress:"));
bottomProgress.add(totalProgressBar);
bottomProgress.add(rebalanceBtn);

progressPanel.add(progressScroll, BorderLayout.CENTER);
progressPanel.add(bottomProgress, BorderLayout.SOUTH);


        tabs.add("Generate", genPanel);
        tabs.add("View Plan", viewPanel);
        tabs.add("Edit Syllabus", editPanel);
        tabs.add("Progress Tracker", progressPanel);

        content.add(tabs, BorderLayout.CENTER);
        f.add(content);
        f.setVisible(true);

        // ---- EVENT LISTENERS ----
        genBtn.addActionListener(e -> {
            if (u.syllabus.isEmpty()) {
                msg(f, "Please add syllabus first.");
                return;
            }
            int wks = (Integer) weekSpinner.getValue();
            String cap = (String) capBox.getSelectedItem();
            u.lastPlan = StudyPlanGenerator.gen(u.syllabus, wks, cap);
            genOut.setText(ViewGUI.render(u.lastPlan));
            msg(f, "Plan generated successfully!");
        });

        saveBtn.addActionListener(e -> {
            if (u.lastPlan == null) {
                msg(f, "No plan to save!");
                return;
            }
            u.savePlanToDatabase();
            msg(f, "Plan saved to database successfully!");
        });

    

        rebalanceBtn.addActionListener(e -> {
            if (u.lastPlan == null) {
                msg(f, "No plan to rebalance. Please generate one first.");
                return;
            }
            String cap = (String) capBox.getSelectedItem();
            StudyPlanGenerator.rebalancePlan(u, cap);
            buildProgressUI(u, weeksContainer, totalProgressBar);
            msg(f, "Plan rebalanced! Incomplete topics redistributed across weeks.");
        });

        // ---- Tab Switch Behavior ----
        tabs.addChangeListener(ev -> {
            if (tabs.getSelectedIndex() == 1 && u.lastPlan != null) {
                viewArea.setText(ViewGUI.render(u.lastPlan));
            } else if (tabs.getSelectedIndex() == 2) {
                listModel.clear();
                for (Subtopic s : u.syllabus) listModel.addElement(s);
            } else if (tabs.getSelectedIndex() == 3) {
                buildProgressUI(u, weeksContainer, totalProgressBar);
            }
        });
    }

    // ====================== HELPER METHODS ======================

    static void buildProgressUI(User u, JPanel container, JProgressBar totalProgressBar) {
        container.removeAll();
        if (u.lastPlan == null) {
            container.add(new JLabel("No study plan generated yet."));
            container.revalidate();
            container.repaint();
            return;
        }

        int total = 0, done = 0;
        for (int w = 0; w < u.lastPlan.weeks.size(); w++) {
            Week wk = u.lastPlan.weeks.get(w);
            JPanel wp = new JPanel();
            wp.setLayout(new BoxLayout(wp, BoxLayout.Y_AXIS));
            wp.setBorder(BorderFactory.createTitledBorder("Week " + (w + 1)));
            wp.setBackground(Color.white);

            for (Entry e : wk.entries) {
                String formattedTime = ViewGUI.formatHours(e.hours);
                JCheckBox cb = new JCheckBox(e.topic + " (" + e.priority + ", " + formattedTime + ")");
                Subtopic s = findSubtopicByTopic(u, e.topic);
                if (s != null) cb.setSelected(s.completed);
                cb.addActionListener(ev -> {
                    if (s != null) s.completed = cb.isSelected();
                    buildProgressUI(u, container, totalProgressBar);
                });
                wp.add(cb);
                total++;
                if (s != null && s.completed) done++;
            }
            container.add(Box.createRigidArea(new Dimension(0, 10)));
            container.add(wp);
        }

        int percent = total == 0 ? 0 : (done * 100 / total);
        totalProgressBar.setValue(percent);
        totalProgressBar.setString(percent + "% Complete");
        container.revalidate();
        container.repaint();
    }

    static Subtopic findSubtopicByTopic(User u, String topic) {
        for (Subtopic s : u.syllabus) {
            if (s.topic.equalsIgnoreCase(topic)) return s;
        }
        return null;
    }

    static JPanel styledPanel(Color c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(c);
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        return p;
    }

    static JTextArea styledArea() {
        JTextArea a = new JTextArea();
        a.setFont(new Font("Consolas", Font.PLAIN, 14));
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setEditable(false);
        return a;
    }

    static void msg(Component p, String t) {
        JOptionPane.showMessageDialog(p, t);
    }
}

    // =====================================================
    // =================== VIEW GUI =========================
    // =====================================================

   static class ViewGUI {
    static String render(StudyPlan p) {
        if (p == null || p.weeks.isEmpty()) return "No plan.";
        StringBuilder sb = new StringBuilder();
        sb.append("Study Plan (Generated: ").append(p.generatedDate).append(")\n\n");
        int w = 1;
        for (Week wk : p.weeks) {
            sb.append("=== Week ").append(w++).append(" ===\n");
            for (Entry e : wk.entries) {
                sb.append("- ").append(e.topic).append(" (")
                  .append(e.priority).append(", ")
                  .append(formatHours(e.hours)).append(")\n");
            }
            sb.append("Total: ").append(formatHours(wk.totalHours)).append("\n\n");
        }
        return sb.toString();
    }

    // 🔹 Converts decimal hours (e.g., 1.875) into "1 hr 53 mins"
   public static String formatHours(double hours)  {
        int hr = (int) hours;
        int mins = (int) Math.round((hours - hr) * 60);

        // Fix cases like 1.999h = 2 hr 0 mins
        if (mins == 60) {
            hr += 1;
            mins = 0;
        }

        if (hr == 0) return mins + " mins";
        else if (mins == 0) return hr + (hr == 1 ? " hr" : " hrs");
        else return hr + (hr == 1 ? " hr " : " hrs ") + mins + " mins";
    }
}
}



