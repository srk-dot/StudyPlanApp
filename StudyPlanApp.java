import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.sql.*;
public class StudyPlanApp {
    private static final Scanner sc = new Scanner(System.in);
    private static Map<String, User> users = new HashMap<>();
    private static User currentUser = null;

    public static void main(String[] args) {
        users = User.load();
        while (true) {
            System.out.println("\n=== Study Plan Generator ===");
            System.out.println("1) Signup\n2) Login\n3) Exit");
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
        }
    }

    private static void menu() {
        while (currentUser != null) {
            System.out.println("\n--- " + currentUser.username + " ---");
            System.out.println("1) Enter/Upload Syllabus");
            System.out.println("2) Open Dashboard (GUI: Generate + View)");
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

    private static void saveFile() {
        if (currentUser.lastPlan == null) {
            System.out.println("No plan.");
            return;
        }
        try (PrintWriter w = new PrintWriter(currentUser.username + "_plan.txt")) {
            w.print(ViewGUI.render(currentUser.lastPlan));
            System.out.println("Saved to " + currentUser.username + "_plan.txt");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ========================== Data Classes ==========================
    static class Subtopic implements Serializable {
        String topic, priority;
        Subtopic(String t, String p) { topic = t; priority = p; }
    }
    static class Entry implements Serializable {
        String topic, priority;
        double hours;
        Entry(String t, String p, double h) { topic = t; priority = p; hours = h; }
    }
    static class Week implements Serializable {
        java.util.List<Entry> entries = new ArrayList<>();
        double totalHours;
    }
    static class StudyPlan implements Serializable {
        LocalDate generatedDate;
        java.util.List<Week> weeks = new ArrayList<>();
    }

    // ========================== USER (MySQL) ==========================
    static class User implements Serializable {
        String username, password;
        java.util.List<Subtopic> syllabus = new ArrayList<>();
        StudyPlan lastPlan;

        User(String u, String p) { username = u; password = p; }

        // Signup
        static boolean signup(Map<String, User> users, Scanner sc) {
            System.out.print("Username: ");
            String u = sc.nextLine().trim();
            if (u.isEmpty()) {
                System.out.println("Username cannot be empty.");
                return false;
            }

            System.out.print("Password: ");
            String p = sc.nextLine().trim();

            try (Connection conn = DBUtil.getConnection()) {
                if (conn == null) {
                    System.out.println("❌ Database connection failed.");
                    return false;
                }

                // Check if username exists
                String checkSql = "SELECT username FROM users WHERE username = ?";
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setString(1, u);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        System.out.println("⚠️ Username already exists.");
                        return false;
                    }
                }

                // Insert new user
                String insertSql = "INSERT INTO users (username, password) VALUES (?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, u);
                    ps.setString(2, p);
                    ps.executeUpdate();
                    System.out.println("✅ Signup successful!");
                    users.put(u, new User(u, p));
                    return true;
                }
            } catch (SQLException e) {
                System.out.println("❌ Database error during signup: " + e.getMessage());
                return false;
            }
        }

        // Login
        static User login(Map<String, User> users, Scanner sc) {
            System.out.print("Username: ");
            String u = sc.nextLine().trim();
            System.out.print("Password: ");
            String p = sc.nextLine().trim();

            try (Connection conn = DBUtil.getConnection()) {
                if (conn == null) {
                    System.out.println("❌ Database connection failed.");
                    return null;
                }

                String sql = "SELECT username, password FROM users WHERE username = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, u);
                    ResultSet rs = ps.executeQuery();

                    if (rs.next()) {
                        String dbPass = rs.getString("password");
                        if (p.equals(dbPass)) {
                            System.out.println("✅ Login successful!");
                            User user = new User(u, p);
                            users.put(u, user);
                            return user;
                        } else {
                            System.out.println("❌ Incorrect password.");
                        }
                    } else {
                        System.out.println("⚠️ User not found.");
                    }
                }
            } catch (SQLException e) {
                System.out.println("❌ Database error during login: " + e.getMessage());
            }
            return null;
        }

        static void save(Map<String, User> users) {}
        static Map<String, User> load() { return new HashMap<>(); }
    }

    // ========================== Syllabus Manager ==========================
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
                    System.out.println("Read error.");
                }
            } else {
                while (true) {
                    System.out.print("Topic (blank to stop): ");
                    String t = sc.nextLine().trim();
                    if (t.isEmpty()) break;
                    System.out.print("Priority (Hard/Medium/Easy): ");
                    String p = sc.nextLine().trim();
                    list.add(new Subtopic(t, p));
                }
            }
            u.syllabus = list;
            System.out.println("Syllabus saved.");
        }
    }

    // ========================== Plan Generator ==========================
    static class StudyPlanGenerator {
        static StudyPlan gen(java.util.List<Subtopic> t, int wks, String capacity) {
            StudyPlan p = new StudyPlan();
            int n = t.size(), base = n / wks, r = n % wks, idx = 0;
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
                    double h = switch (s.priority.toLowerCase()) {
                        case "hard" -> 2.0;
                        case "medium" -> 1.25;
                        case "easy" -> 0.75;
                        default -> 0.5;
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
    }

    // ========================== GUI Dashboard ==========================
    static class DashboardGUI {
        static void show(User u) {
            JFrame f = new JFrame("Dashboard - " + u.username);
            f.setSize(850, 650);
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

            // ---- Tab 1: Generate Plan ----
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

            // ---- Tab 2: View Plan ----
            JTextArea viewArea = styledArea();
            JPanel viewPanel = styledPanel(panelBg);
            viewPanel.add(new JScrollPane(viewArea), BorderLayout.CENTER);

            // ---- Actions ----
            genBtn.addActionListener(e -> {
                if (u.syllabus == null || u.syllabus.isEmpty()) {
                    msg(f, "No syllabus entered.");
                    return;
                }
                String cap = (String) capBox.getSelectedItem();
                u.lastPlan = StudyPlanGenerator.gen(u.syllabus, (Integer) weekSpinner.getValue(), cap);
                String txt = ViewGUI.render(u.lastPlan);
                genOut.setText(txt);
                viewArea.setText(txt);
            });

            saveBtn.addActionListener(e -> {
                if (u.lastPlan == null) {
                    msg(f, "No plan generated to save!");
                    return;
                }
                try (PrintWriter w = new PrintWriter(u.username + "_plan.txt")) {
                    w.print(ViewGUI.render(u.lastPlan));
                    msg(f, "Plan saved as " + u.username + "_plan.txt");
                } catch (Exception ex) {
                    msg(f, "Error saving plan: " + ex.getMessage());
                }
            });

            tabs.add("Generate Plan", genPanel);
            tabs.add("View Plan", viewPanel);

            content.add(tabs, BorderLayout.CENTER);
            f.add(content);
            f.setVisible(true);
        }
    }

    // ========================== View Renderer ==========================
   // ========================== View Renderer ==========================
static class ViewGUI {
    static String render(StudyPlan p) {
        if (p == null) return "No plan generated yet.";
        StringBuilder sb = new StringBuilder();
        sb.append("Generated on: ").append(p.generatedDate).append("\n");
        int i = 1;
        for (Week w : p.weeks) {
            sb.append("\nWeek ").append(i++).append(" (Total hrs: ")
              .append(formatTime(w.totalHours)).append(")\n");
            for (Entry e : w.entries)
                sb.append(" - ").append(e.topic).append(" [").append(e.priority)
                  .append("] - ").append(formatTime(e.hours)).append("\n");
        }
        return sb.toString();
    }

    // Convert decimal hours to "X hr Y min" format
    private static String formatTime(double hours) {
        int h = (int) hours;
        int m = (int) Math.round((hours - h) * 60);
        if (m == 60) { // handle rounding like 1.9999 hrs -> 2 hr 0 min
            h++;
            m = 0;
        }

        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append(h == 1 ? " hr" : " hrs");
        if (m > 0) {
            if (h > 0) sb.append(" ");
            sb.append(m).append(" min");
        }
        if (h == 0 && m == 0) sb.append("0 min");
        return sb.toString();
    }
}

    // ========================== GUI Helpers ==========================
    static JPanel styledPanel(Color bg) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(bg);
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        return p;
    }

    static JTextArea styledArea() {
        JTextArea a = new JTextArea();
        a.setFont(new Font("Consolas", Font.PLAIN, 14));
        a.setMargin(new Insets(10, 10, 10, 10));
        a.setBackground(new Color(245, 250, 255));
        return a;
    }

    static void msg(Component p, String s) {
        JOptionPane.showMessageDialog(p, s);
    }
}
